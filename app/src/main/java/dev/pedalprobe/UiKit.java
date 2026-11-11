package dev.pedalprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.ScrollView;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.io.InputStream;

/** 앱과 웹이 함께 사용하는 로컬 디자인 에셋. 네트워크 글꼴 의존 없음. */
final class UiKit {
    private final Context context;
    private final JSONObject colors;
    private final JSONObject darkColors;
    private boolean darkMode;
    final Typeface regular, semibold, bold;
    UiKit(Context context) {
        this.context=context;
        try {
            JSONObject tokens=new JSONObject(read("tokens.json"));
            colors=tokens.getJSONObject("colors");darkColors=tokens.getJSONObject("darkColors");
            regular=Typeface.createFromAsset(context.getAssets(),"fonts/Pretendard-Regular.otf");
            semibold=Typeface.createFromAsset(context.getAssets(),"fonts/Pretendard-SemiBold.otf");
            bold=Typeface.createFromAsset(context.getAssets(),"fonts/Pretendard-Bold.otf");
        } catch(Exception e) { throw new IllegalStateException("디자인 에셋을 읽을 수 없습니다",e); }
    }
    String read(String path) throws java.io.IOException {
        try(InputStream stream=context.getAssets().open(path)){
            java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
            byte[] buffer=new byte[8192];int count;
            while((count=stream.read(buffer))!=-1)bytes.write(buffer,0,count);
            return bytes.toString(StandardCharsets.UTF_8.name());
        }
    }
    int themeColor(String name,boolean dark){return Color.parseColor((dark?darkColors:colors).optString(name,dark?"#EDF3FF":"#17253B"));}
    int color(String name){ return themeColor(name,darkMode); }
    Context dialogContext(){return new android.view.ContextThemeWrapper(context,darkMode?android.R.style.Theme_Material_Dialog_Alert:android.R.style.Theme_Material_Light_Dialog_Alert);}
    int dp(float value){ return Math.round(value*context.getResources().getDisplayMetrics().density); }
    GradientDrawable surface(String fill,int radius){
        GradientDrawable shape=new GradientDrawable();shape.setColor(color(fill));shape.setCornerRadius(dp(radius));
        shape.setStroke(dp(1),color("border"));return shape;
    }
    void apply(View root){
        if(root instanceof TextView t){t.setTypeface(regular);t.setTextColor(color("foreground"));t.setFontFeatureSettings("tnum");}
        if(root instanceof Button b){
            b.setTypeface(semibold);b.setAllCaps(false);b.setTextColor(color("primary"));
            b.setBackground(new RippleDrawable(ColorStateList.valueOf(color("primarySoft")),surface("surface",10),null));
            b.setPadding(dp(16),dp(12),dp(16),dp(12));b.setMinHeight(dp(52));
            if(b.getLayoutParams() instanceof ViewGroup.MarginLayoutParams lp){lp.topMargin=dp(8);b.setLayoutParams(lp);}
        }
        if(root instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)apply(group.getChildAt(i));
    }
    void window(Activity activity){
        String choice=context.getSharedPreferences("app_experience",Context.MODE_PRIVATE).getString("theme_mode","system");
        boolean system=(context.getResources().getConfiguration().uiMode&android.content.res.Configuration.UI_MODE_NIGHT_MASK)==android.content.res.Configuration.UI_MODE_NIGHT_YES;
        window(activity,"dark".equals(choice)||("system".equals(choice)&&system));
    }
    void window(Activity activity,boolean dark){
        darkMode=dark;
        activity.getWindow().setStatusBarColor(color("background"));
        activity.getWindow().setNavigationBarColor(color("background"));
        int flags=activity.getWindow().getDecorView().getSystemUiVisibility();
        int light=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        activity.getWindow().getDecorView().setSystemUiVisibility(dark?flags&~light:flags|light);
    }
    void licenses(Activity activity){
        try {
            ScrollView scroll=new ScrollView(activity);scroll.setBackgroundColor(color("surface"));TextView text=new TextView(activity);
            text.setText(context.getString(R.string.font_license_body,read("licenses/pretendard.txt")));
            text.setTextSize(14);text.setTypeface(regular);text.setTextColor(color("foreground"));
            text.setPadding(dp(20),dp(16),dp(20),dp(16));scroll.addView(text);
            new AlertDialog.Builder(dialogContext()).setTitle("글꼴 라이선스").setView(scroll).setPositiveButton("닫기",null).show();
        } catch(Exception e){new AlertDialog.Builder(dialogContext()).setMessage("라이선스를 읽을 수 없습니다.").setPositiveButton("닫기",null).show();}
    }
    void nativeNotices(Activity activity){
        try {
            ScrollView scroll=new ScrollView(activity);scroll.setBackgroundColor(color("surface"));TextView text=new TextView(activity);
            String notices=read("licenses/native-dependencies.json")+"\n\n"+read("licenses/androidx-apache-2.0.txt");
            text.setText(notices);text.setTextSize(13);text.setTypeface(regular);
            text.setTextColor(color("foreground"));text.setPadding(dp(20),dp(16),dp(20),dp(16));scroll.addView(text);
            new AlertDialog.Builder(dialogContext()).setTitle("네이티브 UI 오픈소스 고지").setView(scroll).setPositiveButton("닫기",null).show();
        } catch(Exception e){new AlertDialog.Builder(dialogContext()).setMessage("고지를 읽을 수 없습니다.").setPositiveButton("닫기",null).show();}
    }
}
