package dev.pedalprobe;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.view.View;
import android.widget.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MainActivity extends androidx.activity.ComponentActivity {
    private final Handler main = new Handler();
    private final ArrayDeque<String> messages = new ArrayDeque<>();
    private TextView values, log, mode;
    private SignalChart chart;
    private SignalEngine engine = new SignalEngine();
    private VehicleSource source;
    private BufferedWriter writer;
    private File sessionFile;
    private boolean active;
    private int generation, eventCount, records;
    private long speedAt = -1, previousSource = -1, lastSourceGap = -1, startMs;
    private double speedKmh = Double.NaN;
    private String sessionMode = "NONE";
    private UiKit ui;
    private NativeDashboard dashboard;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ui=new UiKit(this);ui.window(this);
        if(state!=null){
            String saved=state.getString("sessionFile");
            if(saved!=null&&new File(saved).getName().equals(saved)){
                File prior=new File(getFilesDir(),saved);
                if(prior.exists())sessionFile=prior;
            }
        }
        dashboard=new NativeDashboard(this,ui,new NativeDashboard.Actions(){
            public void diagnostics(){showDiagnostics();}
            public void startDemo(){demo(true,true,false);}
            public void connectVehicle(){requestReal();}
            public void stopRecording(){stop("사용자 중지");}
            public void exportFile(File file){stop("기록 내보내기");sessionFile=file;export();}
        });
        mode=dashboard.mode;values=dashboard.diagnostics;log=dashboard.log;chart=dashboard.chart;
        setContentView(dashboard.root);
        refreshDashboard();
    }
    private void showDiagnostics(){
        String[] labels={"모의 실행: 일반 가속","모의 실행: 급격한 가속 + 브레이크 해제",
                "모의 실행: 급격한 가속 + 브레이크 입력","모의 실행: 급격한 가속 + 브레이크 수신 실패",
                "실차/에뮬레이터 API 진단 시작"};
        new android.app.AlertDialog.Builder(ui.dialogContext()).setTitle("진단 도구 · 정차 상태에서 사용")
                .setItems(labels,(dialog,which)->{
                    dashboard.show(0);
                    if(which==0)demo(false,false,false);
                    else if(which==1)demo(true,false,false);
                    else if(which==2)demo(true,true,false);
                    else if(which==3)demo(true,false,true);
                    else requestReal();
                }).setNegativeButton("닫기",null).show();
    }
    private void refreshDashboard(){
        if(dashboard==null)return;
        long now=SystemClock.elapsedRealtime();
        boolean fresh=active&&speedAt>=0&&now>=speedAt&&now-speedAt<=SignalEngine.MAX_SPEED_GAP_MS;
        dashboard.update(active,sessionMode,fresh?speedKmh:Double.NaN,brakeLabel(engine.brakeAt(now)),records,sessionFile);
    }
    private void begin(String newMode) {
        stop("새 세션");
        engine = new SignalEngine();messages.clear();log.setText("");eventCount=0;records=0;
        chart.clear();
        speedAt=-1;previousSource=-1;lastSourceGap=-1;speedKmh=Double.NaN;
        sessionMode = newMode;startMs = SystemClock.elapsedRealtime();
        String name = new SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.ROOT).format(new Date());
        sessionFile = new File(getFilesDir(),newMode.toLowerCase(Locale.ROOT)+"-"+name+".jsonl");
        try {
            writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(sessionFile),StandardCharsets.UTF_8));
            active=true; mode.setText(newMode.equals("DEMO") ? "모의 신호 · 실차 데이터 아님" : "실차 API 진단 · 지원 미확정");
            write("session_start",json("mode",newMode,"schemaVersion",1,"wallClockUtc",java.time.Instant.now().toString(),
                    "brakeTimestamp","host_receipt_only","speedTimestamp","vehicle_elapsed_realtime",
                    "accelerationThreshold",SignalEngine.ACCEL_THRESHOLD,"windowMs",SignalEngine.WINDOW_MS,
                    "persistenceMs",SignalEngine.PERSIST_MS,"claims","no_pedal_intent_or_fault_classification"));
            refreshDashboard();
            int current = generation;
            main.postDelayed(() -> { if(active && generation==current) stop("30분 제한"); },30*60*1000);
        } catch(IOException e) { active=false; mode.setText("기록 파일 생성 실패");refreshDashboard();info("기록 파일 생성 실패: "+e); }
    }
    private void demo(boolean rapid,boolean pressed,boolean missing) {
        begin("DEMO"); if(!active) return;
        int current = generation;
        Runnable sequence = new Runnable() {
            int index=0;
            @Override public void run() {
                if(!active || generation!=current)return;
                long t=index*100L;
                // A confirmed initial state followed by failed reads becomes UNKNOWN, never RELEASED.
                SignalEngine.Brake b = missing && t>=1000 ? SignalEngine.Brake.UNKNOWN
                        : pressed ? SignalEngine.Brake.PRESSED : SignalEngine.Brake.RELEASED;
                brake(b);
                double sec=t/1000.0;
                double v=sec<2 ? 0 : sec<5 ? (sec-2)*(rapid?3.2:1.0) : Math.max(0,(rapid?9.6:3)-(sec-5)*2);
                speed(SystemClock.elapsedRealtime(),v);
                if(++index<=100)main.postDelayed(this,100);else stop("모의 시나리오 종료");
            }
        };
        main.post(sequence);main.post(ticker);
    }
    private void requestReal() {
        stop("권한 확인");
        requestPermissions(new String[]{"android.car.permission.CAR_SPEED","android.car.permission.READ_CAR_PEDALS",
                "pleos.car.permission.CAR_DRIVE"},11);
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request!=11)return;
        begin("REAL");if(!active)return;
        for(int i=0;i<permissions.length;i++) info(permissions[i]+"="+(i<results.length&&results[i]==PackageManager.PERMISSION_GRANTED?"GRANTED":"DENIED/UNAVAILABLE"));
        try {
            Class.forName("android.car.Car");
            int current = generation;
            source = new PleosSource(this,new VehicleSource.Sink() {
                private boolean live(){return active&&generation==current;}
                public void info(String message){if(live())MainActivity.this.info(message);}
                public void raw(String kind,JSONObject value){if(live())write(kind,value);}
                public void brake(SignalEngine.Brake value){if(live())MainActivity.this.brake(value);}
                public void speed(long t,double v){if(live())MainActivity.this.speed(t,v);}
                public void speedUnavailable(String reason){if(live()){
                    engine.invalidateSpeed();speedAt=-1;speedKmh=Double.NaN;info(reason);
                }}
            });
            source.start();main.post(ticker);
        } catch(Exception | LinkageError e) {info("차량 플랫폼 API 없음/연결 실패: "+e);stop("연결 불가");}
    }
    private void brake(SignalEngine.Brake value) {
        long now=SystemClock.elapsedRealtime();engine.brake(now,value);
        chart.brake(now,value);
        write("brake_observation",json("state",value.toString(),"timeKind","host_receipt_only"));
    }
    private void speed(long sourceMs,double v) {
        long now=SystemClock.elapsedRealtime();
        write("speed_sample",json("sourceElapsedMs",sourceMs,"metresPerSecond",v,
                "sourceAgeMs",now-sourceMs));
        SignalEngine.Event event=engine.speed(sourceMs,now,v);
        boolean valid=Double.isFinite(v)&&sourceMs>=0&&sourceMs<=now&&now-sourceMs<=SignalEngine.MAX_SPEED_GAP_MS;
        if(valid&&sourceMs>previousSource){
            lastSourceGap=previousSource<0?-1:sourceMs-previousSource;
            previousSource=sourceMs;speedAt=sourceMs;speedKmh=Math.abs(v)*3.6;
            chart.speed(now,speedKmh);
        }
        if(event!=null){
            eventCount++;
            chart.event(now);
            write("rapid_speed_growth",json("sourceElapsedMs",event.timeMs(),"speedKmh",event.speedKmh(),
                    "accelerationMps2",event.acceleration(),"brakeObservation",event.brake().toString(),
                    "brakeObservationAgeMs",event.brakeObservationAgeMs(),"classification","pedal_intent_unknown"));
            info("속도 증가 구간 #"+eventCount+" / 브레이크 관측="+event.brake()+" / 오조작 판정 없음");
        }
    }
    private final Runnable ticker=new Runnable(){public void run(){
        if(!active)return;
        long now=SystemClock.elapsedRealtime();
        boolean fresh=speedAt>=0&&now>=speedAt&&now-speedAt<=SignalEngine.MAX_SPEED_GAP_MS;
        String velocity=fresh?String.format(Locale.ROOT,"%.1f km/h",speedKmh):"미수신/오래된 신호";
        values.setText(getString(R.string.live_status,velocity,brakeLabel(engine.brakeAt(now)),
                lastSourceGap<0?"미측정":lastSourceGap+"ms",eventCount));
        refreshDashboard();
        if(!fresh)engine.invalidateSpeed();
        main.postDelayed(this,200);
    }};
    private static String brakeLabel(SignalEngine.Brake state){
        return state==SignalEngine.Brake.PRESSED?"입력 관측":state==SignalEngine.Brake.RELEASED?"해제 관측":"미확인";
    }
    private void info(String message) {
        if(log==null)return;
        messages.addLast(message);while(messages.size()>60)messages.removeFirst();
        log.setText(String.join("\n",messages));
        if(active)write("diagnostic",json("message",message));
    }
    private void write(String kind,JSONObject data) {
        if(writer==null)return;
        try {
            JSONObject envelope=json("type",kind,"mode",sessionMode,"hostElapsedMs",SystemClock.elapsedRealtime(),"data",data);
            writer.write(envelope.toString());writer.newLine();
            if(++records%10==0)writer.flush();
            if(active&&records>=100000)stop("레코드 수 제한");
        }catch(IOException e){
            try{writer.close();}catch(IOException ignored){}writer=null;
            stop("기록 실패: "+e.getMessage());
        }
    }
    private void stop(String reason) {
        boolean wasActive=active;active=false;generation++;
        main.removeCallbacksAndMessages(null);
        if(source!=null){source.stop();source=null;}
        if(writer!=null){
            BufferedWriter closing=writer;writer=null;
            try{
                closing.write(json("type","session_end","mode",sessionMode,"hostElapsedMs",SystemClock.elapsedRealtime(),
                        "data",json("reason",reason,"events",eventCount)).toString());
                closing.newLine();closing.close();
            }catch(IOException ignored){try{closing.close();}catch(IOException ignoredAgain){}}
        }
        refreshDashboard();
        if(wasActive){mode.setText(getString(R.string.session_stopped,sessionMode.equals("DEMO")?"모의 기록":"실차 진단",reason));info("세션 파일: "+sessionFile.getName());}
    }
    private void export() {
        stop("내보내기");
        if(sessionFile==null||!sessionFile.exists()){info("내보낼 세션이 없습니다.");return;}
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT);intent.setType("application/octet-stream");
        intent.addCategory(Intent.CATEGORY_OPENABLE);intent.putExtra(Intent.EXTRA_TITLE,sessionFile.getName());
        startActivityForResult(intent,22);
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request!=22||result!=RESULT_OK||data==null||data.getData()==null)return;
        if(sessionFile==null){info("세션 파일을 복원하지 못했습니다.");return;}
        try(InputStream in=new FileInputStream(sessionFile);OutputStream out=getContentResolver().openOutputStream(data.getData())){
            if(out==null)throw new IOException("저장 대상을 열 수 없습니다.");
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);
            info("내보내기 완료");
        }catch(IOException e){info("내보내기 실패: "+e);}
    }
    @Override protected void onStop(){stop("화면 이탈: 수집 중지");super.onStop();}
    @Override protected void onSaveInstanceState(Bundle state){
        if(sessionFile!=null)state.putString("sessionFile",sessionFile.getName());
        super.onSaveInstanceState(state);
    }
    static JSONObject json(Object... pairs){
        JSONObject o=new JSONObject();
        try{for(int i=0;i<pairs.length;i+=2){
            Object value=pairs[i+1];
            if(value instanceof Number n&&!Double.isFinite(n.doubleValue()))value=null;
            o.put(pairs[i].toString(),value==null?JSONObject.NULL:value);
        }}
        catch(Exception e){throw new IllegalArgumentException(e);}return o;
    }
}
