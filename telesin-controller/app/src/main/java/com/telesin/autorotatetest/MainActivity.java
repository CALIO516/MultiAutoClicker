package com.telesin.autorotatetest;
import android.app.*;import android.os.*;import android.content.*;import android.provider.*;import android.view.*;import android.widget.*;import android.graphics.*;import android.graphics.drawable.*;
public class MainActivity extends Activity{
 LinearLayout c;TextView status;
 int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
 void label(String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(14);t.setTextColor(0xff394b69);t.setPadding(0,dp(10),0,dp(10));c.addView(t);}
 void button(String s,Runnable r){Button b=new Button(this);b.setAllCaps(false);b.setText(s);c.addView(b,new LinearLayout.LayoutParams(-1,dp(52)));b.setOnClickListener(v->r.run());}
 ControlService svc(){if(ControlService.ref==null){Toast.makeText(this,"접근성 서비스를 먼저 켜 주세요.",Toast.LENGTH_LONG).show();}return ControlService.ref;}
 @Override public void onCreate(Bundle x){super.onCreate(x);render();}
 void render(){ScrollView v=new ScrollView(this);c=new LinearLayout(this);c.setOrientation(1);c.setPadding(dp(18),dp(15),dp(18),dp(20));c.setBackgroundColor(0xfff5f7ff);v.addView(c);setContentView(v);
 TextView h=new TextView(this);h.setText("TELESIN  Auto Rotate");h.setTextColor(0xff2449a2);h.setTextSize(24);h.setTypeface(null,1);c.addView(h);
 label("v0.2 · 학습 경로 재생 오류 수정 및 실패 단계 표시");status=new TextView(this);status.setTextSize(14);c.addView(status);
 button("접근성 설정 열기",()->startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
 button("화면 위 ↻ 버튼 표시/숨기기",()->{ControlService s=svc();if(s!=null){s.toggle();update();}});
 label("Smart View > 실험실 > 90° 회전 화면을 열고 우측 플로팅 ↻ 버튼을 누르면 해당 메뉴의 버튼을 클릭합니다.");
 button("회전 테스트 (실험실 메뉴가 보일 때)",()->{ControlService s=svc();if(s!=null)s.rotate();});
 label("학습 기능: 삼성 기본 카메라에서 Smart View 플로팅 아이콘 → 설정 → 실험실 → 90° 회전까지 순서대로 직접 누르고, 주황색 '완료' 버튼으로 저장하세요.");
 button("카메라 실행 + 클릭 경로 학습 시작",()->{ControlService s=svc();if(s!=null){s.record(true);camera();}});
 button("학습 완료",()->{ControlService s=svc();if(s!=null){s.record(false);update();}});
 button("카메라부터 학습 경로 재실행",()->{ControlService s=svc();if(s!=null)s.rotate();});
 button("최근 재생 결과 / 실패 원인 보기",()->{ControlService s=svc();if(s!=null)new AlertDialog.Builder(this).setTitle("최근 재생 진단").setMessage(s.report()+"\n\n실패할 경우 화면의 내용을 알려주세요.").setPositiveButton("확인",null).show();});
 button("학습 경로 삭제",()->{ControlService s=svc();if(s!=null){s.clear();update();}});
 button("자동 회전 시험 모드 ON/OFF",()->{ControlService s=svc();if(s!=null){s.autoToggle();update();}});
 button("현재 모니터를 세로 0°로 지정",()->{ControlService s=svc();if(s!=null){s.baseline(0);update();}});
 button("현재 모니터를 가로 90°로 지정",()->{ControlService s=svc();if(s!=null){s.baseline(1);update();}});
 button("삼성 기본 카메라 실행",this::camera);
 label("사용법: 접근성 권한을 켠 뒤 '카메라부터 학습 경로 재실행'을 누르면 카메라를 먼저 실행하고 저장된 경로를 재생합니다. '최근 재생 결과'에서 어느 단계가 실패했는지 확인하세요. 삼성 시스템 UI 차이로 동작하지 않을 수 있으며, 실제 화면 회전을 보장하지 않습니다. 사진·영상은 수집하지 않습니다.");
 update();}
 void camera(){try{startActivity(new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA));}catch(Exception e){Toast.makeText(this,"기본 카메라 실행 불가",0).show();}}
 void update(){if(status!=null){ControlService s=ControlService.ref;status.setText(s==null?"● 접근성 서비스 미연결":"● 서비스 연결 · 경로 "+s.size()+"개 · 회전 "+(s.quarter*90)+"° · 자동 "+s.auto);}}
 @Override public void onResume(){super.onResume();update();}
}