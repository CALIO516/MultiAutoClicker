package com.sj.telesin.buttonrotate;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
public class RotateService extends AccessibilityService {
 public static RotateService instance;
 Handler handler=new Handler(Looper.getMainLooper());
 boolean running=false, attemptedDirect=false, openedPanel=false, clickedSmartView=false;
 boolean clickedSettings=false, clickedLabs=false, clickedMore=false;
 long started=0, lastAction=0;
 int ticks=0;
 String state="대기 중";
 @Override protected void onServiceConnected(){instance=this;report("접근성 연결됨. 회전 버튼을 실행할 수 있습니다.");}
 @Override public void onAccessibilityEvent(AccessibilityEvent event) {
  if(running && (event.getEventType()==AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || event.getEventType()==AccessibilityEvent.TYPE_WINDOWS_CHANGED)) handler.removeCallbacks(stepNow);
  if(running) handler.postDelayed(stepNow,330);
 }
 @Override public void onInterrupt(){}
 @Override public void onDestroy(){running=false;handler.removeCallbacks(stepNow);if(instance==this)instance=null;super.onDestroy();}
 void report(String message){
  state=message;
  getSharedPreferences("status",MODE_PRIVATE).edit().putString("report",message).apply();
 }
 void announce(String message){report(message);handler.post(()->Toast.makeText(this,message,Toast.LENGTH_LONG).show());}
 public void begin(){
  if(running){announce("이미 회전 메뉴를 찾는 중입니다.");return;}
  running=true;started=SystemClock.uptimeMillis();ticks=0;
  attemptedDirect=false;openedPanel=false;clickedSmartView=false;
  clickedSettings=false;clickedLabs=false;clickedMore=false;lastAction=0;
  report("회전 요청 수신. Smart View 90° 메뉴 찾는 중...");
  handler.postDelayed(stepNow,400);
 }
 final Runnable stepNow=()->tick();
 List<AccessibilityNodeInfo> roots(){
  List<AccessibilityNodeInfo> result=new ArrayList<>();
  AccessibilityNodeInfo active=getRootInActiveWindow();
  if(active!=null)result.add(active);
  try{List<AccessibilityWindowInfo> windows=getWindows();if(windows!=null)for(AccessibilityWindowInfo win:windows){
   AccessibilityNodeInfo root=win.getRoot();
   if(root!=null&&!result.contains(root))result.add(root);
  }}catch(Exception ignored){}
  return result;
 }
 static String st(CharSequence t){return t==null?"":t.toString();}
 boolean allowed(AccessibilityNodeInfo root){
  String pkg=st(root.getPackageName());
  return pkg.equals("com.samsung.android.smartmirroring") || pkg.equals("com.android.systemui")
    || pkg.equals("com.android.settings") || pkg.equals("com.samsung.android.settings")
    || pkg.equals("com.sec.android.app.launcher");
 }
 boolean matches(String str,String[] labels) {
  String s=str.toLowerCase(java.util.Locale.ROOT);
  for(String label:labels)if(s.equals(label.toLowerCase(java.util.Locale.ROOT)))return true;
  return false;
 }
 AccessibilityNodeInfo findIn(AccessibilityNodeInfo node,String[] labels,int remaining,int[] seen){
  if(node==null || remaining<0 || seen[0]++>500)return null;
  if(matches(st(node.getText()).trim(),labels) || matches(st(node.getContentDescription()).trim(),labels))return node;
  for(int i=0;i<node.getChildCount();i++){AccessibilityNodeInfo match=findIn(node.getChild(i),labels,remaining-1,seen);if(match!=null)return match;}
  return null;
 }
 AccessibilityNodeInfo find(String... labels){
  for(AccessibilityNodeInfo root:roots()){
   if(!allowed(root))continue;
   AccessibilityNodeInfo n=findIn(root,labels,25,new int[]{0});
   if(n!=null)return n;
  }
  return null;
 }
 boolean press(AccessibilityNodeInfo n) {
  if(n==null)return false;
  AccessibilityNodeInfo current=n;
  for(int i=0;i<7 && current!=null;i++){
   if(current.isEnabled()&&current.isClickable()&&current.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;
   current=current.getParent();
  }
  Rect rect=new Rect();n.getBoundsInScreen(rect);
  if(rect.isEmpty())return false;
  Path path=new Path();path.moveTo(rect.centerX(),rect.centerY());
  return dispatchGesture(new GestureDescription.Builder().addStroke(
   new GestureDescription.StrokeDescription(path,0,90)).build(),null,null);
 }
 boolean tap(String... labels){return press(find(labels));}
 void later(String s,long delay){report(s);lastAction=SystemClock.uptimeMillis();handler.postDelayed(stepNow,delay);}
 void complete(String s){running=false;handler.removeCallbacks(stepNow);announce(s);}
 void fail(String why){running=false;handler.removeCallbacks(stepNow);announce("실행 중단: "+why+". Smart View 회전 메뉴는 수동 사용 가능.");}
 void openSmartView(){
  attemptedDirect=true;
  try{
   Intent i=new Intent(Intent.ACTION_MAIN);
   i.setClassName("com.samsung.android.smartmirroring","com.samsung.android.smartmirroring.welcome.WelcomeIntroActivity");
   i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
   startActivity(i);
   later("Smart View 설정 진입 시도 중",1100);
  }catch(Exception e){
   if(performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)){
    openedPanel=true;
    later("Smart View 빠른 설정 메뉴 열기",1100);
   }else fail("Smart View 메뉴 진입 실패");
  }
 }
 void tick(){
  if(!running)return;
  if(SystemClock.uptimeMillis()-started>22000 || ticks++>70){fail("22초 내 90° 회전 버튼에 도달하지 못함");return;}
  if(SystemClock.uptimeMillis()-lastAction<450){handler.postDelayed(stepNow,400);return;}
  if(tap("90° 회전","90도 회전","90 ° 회전","90° Rotation","Rotate 90°","Rotate 90 degrees")){
   complete("Smart View '90° 회전' 항목을 눌렀습니다. 외부 모니터에서 실제 회전 여부를 확인하세요.");
   return;
  }
  if(!attemptedDirect){openSmartView();return;}
  if(!clickedLabs&&tap("실험실","스마트 뷰 실험실","Smart View Labs","Labs")){
   clickedLabs=true;later("Smart View 실험실 열기",1050);return;
  }
  if(!clickedSettings && tap("설정","Smart View 설정","Settings","Smart View settings")){
   clickedSettings=true;later("Smart View 설정 열기",950);return;
  }
  if(!clickedMore && tap("더보기","옵션 더보기","More options","More","옵션")){
   clickedMore=true;later("Smart View 더보기 열기",900);return;
  }
  if(!openedPanel && SystemClock.uptimeMillis()-started>4200){
   if(performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)){
    openedPanel=true;later("빠른 설정의 Smart View 찾는 중",900);return;
   }
  }
  if(openedPanel&&!clickedSmartView&&tap("Smart View","스마트 뷰","SmartView")){
   clickedSmartView=true;clickedSettings=false;clickedLabs=false;clickedMore=false;
   later("Smart View 화면 열기",1150);return;
  }
  handler.postDelayed(stepNow,520);
 }
}
