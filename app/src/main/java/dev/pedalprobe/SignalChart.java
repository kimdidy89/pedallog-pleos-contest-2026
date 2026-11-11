package dev.pedalprobe;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.util.ArrayList;

/** Observation-time plot. It does not claim synchronized physical pedal timestamps. */
public final class SignalChart extends View {
    public record Speed(long time, double kmh) {}
    public record BrakePoint(long time, SignalEngine.Brake state) {}
    private final ArrayList<Speed> speeds = new ArrayList<>();
    private final ArrayList<BrakePoint> brakes = new ArrayList<>();
    private final ArrayList<Long> events = new ArrayList<>();
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path line = new Path();
    private final float density;
    private final UiKit ui;
    private long end = -1;
    private static final long RANGE = 12000;
    private Runnable dataChanged;
    public void setOnDataChanged(Runnable listener){dataChanged=listener;}
    public java.util.List<Speed> snapshotSpeeds(){return new ArrayList<>(speeds);}
    public java.util.List<BrakePoint> snapshotBrakes(){return new ArrayList<>(brakes);}
    public long endTime(){return end;}
    private void changed(){invalidate();if(dataChanged!=null)dataChanged.run();}
    public SignalChart(Context context) {
        super(context); density=getResources().getDisplayMetrics().density;
        ui=new UiKit(context);p.setTypeface(ui.regular);
        setContentDescription("최근 12초의 속도 곡선과 브레이크 입력, 해제, 미확인 상태. 현재 값은 위의 신호 상태에서 확인할 수 있습니다.");
    }
    public void clear(){speeds.clear();brakes.clear();events.clear();end=-1;changed();}
    public void speed(long now,double kmh){
        if(!Double.isFinite(kmh))return;
        speeds.add(new Speed(now,kmh)); end=now;
        while(speeds.size()>1&&speeds.get(0).time<now-RANGE)speeds.remove(0);
        changed();
    }
    public void brake(long now,SignalEngine.Brake state){
        brakes.add(new BrakePoint(now,state));
        while(brakes.size()>1&&brakes.get(1).time<now-RANGE)brakes.remove(0);
        changed();
    }
    public void event(long now){events.add(now);while(events.size()>1&&events.get(0)<now-RANGE)events.remove(0);invalidate();}
    private float d(float value){return density*value;}
    private void label(Canvas c,String text,float x,float y,int color){p.setColor(color);p.setTextSize(d(11));p.setStyle(Paint.Style.FILL);c.drawText(text,x,y,p);}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);c.drawColor(Color.WHITE);
        int ink=ui.color("foreground"),grey=ui.color("unknown"),green=ui.color("pressed"),blue=ui.color("primary");
        float left=d(36),right=getWidth()-d(12),top=d(28),bottom=getHeight()-d(56),width=right-left;
        label(c,"속도와 브레이크 관측 기록",d(10),d(16),ink);
        if(end<0){label(c,"모의 실행 또는 신호 수신 후 표시됩니다",d(20),getHeight()/2f,grey);return;}
        long start=Math.max(0,end-RANGE);
        double maximum=20;
        for(Speed s:speeds)maximum=Math.max(maximum,Math.ceil(s.kmh/10)*10);
        p.setStrokeWidth(d(1));p.setColor(Color.rgb(225,232,239));
        for(int i=0;i<=2;i++){
            float y=bottom-(bottom-top)*i/2f;c.drawLine(left,y,right,y,p);
            label(c,String.format(java.util.Locale.ROOT,"%.0f",maximum*i/2),d(4),y,grey);
            p.setColor(Color.rgb(225,232,239));
        }
        line.reset();boolean first=true;long prior=-1;
        for(Speed s:speeds){
            float x=left+width*(s.time-start)/(float)RANGE,y=bottom-(float)(s.kmh/maximum)*(bottom-top);
            if(first||prior<0||s.time-prior>SignalEngine.MAX_SPEED_GAP_MS)line.moveTo(x,y);else line.lineTo(x,y);
            first=false;prior=s.time;
        }
        p.setColor(blue);p.setStrokeWidth(d(2));p.setStyle(Paint.Style.STROKE);c.drawPath(line,p);p.setStyle(Paint.Style.FILL);
        float band=bottom+d(12);
        // Cap each observation at the detector freshness limit. Longer gaps are grey.
        p.setColor(Color.rgb(205,211,219));c.drawRect(left,band,right,band+d(10),p);
        for(int i=0;i<brakes.size();i++){
            BrakePoint b=brakes.get(i);
            long until=Math.min(end,i+1<brakes.size()?brakes.get(i+1).time:end);
            until=Math.min(until,b.time+SignalEngine.MAX_BRAKE_AGE_MS);
            float x1=left+width*Math.max(0,b.time-start)/(float)RANGE,x2=left+width*Math.max(0,until-start)/(float)RANGE;
            p.setColor(b.state==SignalEngine.Brake.PRESSED?green:b.state==SignalEngine.Brake.RELEASED?blue:grey);
            if(x2>x1)c.drawRect(x1,band,Math.min(right,x2),band+d(10),p);
        }
        p.setColor(Color.rgb(209,113,26));p.setStrokeWidth(d(1));
        for(long t:events)if(t>=start){float x=left+width*(t-start)/(float)RANGE;c.drawLine(x,top,x,band+d(10),p);}
        label(c,"입력",left, getHeight()-d(15),green);
        label(c,"해제",left+d(40),getHeight()-d(15),blue);
        label(c,"미확인",left+d(80),getHeight()-d(15),grey);
        label(c,"관측 시각 기준 · 최근 12초",Math.max(left,right-d(151)),getHeight()-d(1),grey);
    }
}
