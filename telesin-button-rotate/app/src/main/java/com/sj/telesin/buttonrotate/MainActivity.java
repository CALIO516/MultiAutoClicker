package com.sj.telesin.buttonrotate;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Color;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.Collections;
public class MainActivity extends Activity {
 LinearLayout body;
 int d(int a){return Math.round(getResources().getDisplayMetrics().density*a);}
 void text(String s, int size, int color) {
  TextView v=new TextView(this); v.setText(s);v.setTextColor(color);v.setTextSize(size);v.setPadding(0,d(8),0,d(8)); body.addView(v);
 }
 void button(String s, Runnable r) {
  Button b=new Button(this);b.setText(s);b.setAllCaps(false);
  LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,d(52));lp.bottomMargin=d(8);body.addView(b,lp);b.setOnClickListener(v->r.run());
 }
 @Override public void onCreate(Bundle b){super.onCreate(b);registerShortcut();screen();}
 void registerShortcut() {
  try {
   ShortcutManager manager=getSystemService(ShortcutManager.class);
   Intent i=new Intent(this,RotateActivity.class);i.setAction(Intent.ACTION_VIEW);
   ShortcutInfo info=new ShortcutInfo.Builder(this,"smartview_rotate_once")
    .setShortLabel("TELESIN 90도 회전")
    .setLongLabel("Smart View 외부 화면 90도 회전 시도")
    .setIcon(Icon.createWithResource(this,android.R.drawable.ic_menu_manage))
    .setIntent(i).build();
   manager.setDynamicShortcuts(Collections.singletonList(info));
  }catch(Exception ignored){}
 }
 void screen() {
  ScrollView scroll=new ScrollView(this);
  body=new LinearLayout(this);body.setOrientation(1);body.setPadding(d(20),d(24),d(20),d(30));body.setBackgroundColor(0xfff6f8ff);
  scroll.addView(body);setContentView(scroll);
  text("TELESIN 90° 버튼 회전",24,0xff203c83);
  text("학습 없이 촬영 버튼 길게 누르기에 연결하는 시험판 (v0.3)",15,0xff334155);
  button("① 접근성 서비스 설정",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
  button("② 지금 90° 회전 시험",()->{RotateService s=RotateService.instance;if(s==null){new AlertDialog.Builder(this).setMessage("접근성 서비스에서 'TELESIN 회전 제어 접근성'을 먼저 허용하세요.").setPositiveButton("확인",null).show();}else{s.begin();}});
  button("③ 마지막 진행 결과 보기",()->new AlertDialog.Builder(this).setTitle("진행 결과").setMessage(getSharedPreferences("status",0).getString("report","아직 실행하지 않았습니다.")).setPositiveButton("확인",null).show());
  text("Button Mapper 설정: 버튼 추가 → 텔레신 촬영 버튼 등록 → 길게 누르기 → 앱 실행에서 'TELESIN 90도 회전'을 지정하세요.",15,0xff1f2937);
  text("앱 목록에 없으면 '바로가기(Shortcuts)'의 TELESIN 90도 회전을 선택하세요. 홈 화면에도 별도 회전 아이콘이 있습니다.",14,0xff475569);
  text("주의: 삼성 Smart View에 공개된 회전 API가 없어 Smart View 화면의 해당 항목을 자동으로 찾고 눌러봅니다. 삼성 One UI 버전에 따라 작동하지 않을 수 있고 카메라 화면을 잠시 가릴 수 있습니다. 화면 회전 성공 여부는 모니터에서 확인해야 합니다.",14,0xff9a3412);
  text("접근성은 회전 명령 실행 시 화면의 버튼을 찾는 데만 사용합니다. 인터넷 권한은 요청하지 않습니다.",13,0xff475569);
 }
 @Override protected void onResume(){super.onResume();}
}
