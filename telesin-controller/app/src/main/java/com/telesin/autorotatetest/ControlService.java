package com.telesin.autorotatetest;
import android.accessibilityservice.*;import android.content.*;import android.graphics.*;import android.hardware.*;import android.os.*;import android.provider.MediaStore;import android.view.*;import android.view.accessibility.*;import android.widget.*;import org.json.*;import java.util.*;
public class ControlService extends AccessibilityService{
 static ControlService ref;SharedPreferences p;Handler h=new Handler(Looper.getMainLooper());WindowManager wm;LinearLayout overlay;Button done;
 ArrayList<Step> path=new ArrayList<>();boolean learning=false,busy=false,auto=false;int quarter=0,category=-1,sensed=-1,pos=0,pending=0;long steady=0,lastClick=0;OrientationEventListener sensor;
 static class Step{String pkg="",txt="",desc="",id="";double x=.5,y=.5;
 JSONObject json(){JSONObject j=new JSONObject();try{j.put("p",pkg);j.put("t",txt);j.put("d",desc);j.put("i",id);j.put("x",x);j.put("y",y);}catch(Exception e){}return j;}
 static Step parse(JSONObject j){Step s=new Step();s.pkg=j.optString("p");s.txt=j.optString("t");s.desc=j.optString("d");s.id=j.optString("i");s.x=j.optDouble("x",.5);s.y=j.optDouble("y",.5);return s;}}
 String st(CharSequence s){return s==null?"":s.toString().trim();}
 int d(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
 void msg(String s){if(p!=null)p.edit().putString("report",s).apply();h.post(()->Toast.makeText(this,s,Toast.LENGTH_LONG).show());}
 public String report(){return p==null?"서비스 미연결":p.getString("report","재생 전입니다");}
 @Override protected void onServiceConnected(){ref=this;p=getSharedPreferences("control",MODE_PRIVATE);wm=(WindowManager)getSystemService(WINDOW_SERVICE);auto=p.getBoolean("auto",false);quarter=p.getInt("quarter",0);try{JSONArray a=new JSONArray(p.getString("path","[]"));for(int i=0;i<a.length()&&i<20;i++)path.add(Step.parse(a.getJSONObject(i)));}catch(Exception e){}
 if(p.getBoolean("overlay",true))show();sensor=new OrientationEventListener(this,SensorManager.SENSOR_DELAY_NORMAL){@Override public void onOrientationChanged(int a){if(a<0||!auto||busy||learning||pending>0)return;int c=(a<40||a>320||(a>140&&a<220))?0:((a>50&&a<130)||(a>230&&a<310))?1:-1;if(c<0)return;long now=SystemClock.uptimeMillis();if(sensed!=c){sensed=c;steady=now;return;}if(now-steady<1400||category==c)return;category=c;int target=c;int n=(target-quarter+4)%4;if(n>0){pending=n;rotateOnce();}}};if(sensor.canDetectOrientation())sensor.enable();}
 @Override public void onAccessibilityEvent(AccessibilityEvent e){if(!learning||busy||e.getEventType()!=AccessibilityEvent.TYPE_VIEW_CLICKED||path.size()>=20)return;String pkg=st(e.getPackageName());if(pkg.isEmpty()||pkg.equals(getPackageName()))return;AccessibilityNodeInfo n=e.getSource();if(n==null)return;Rect b=new Rect();n.getBoundsInScreen(b);if(b.isEmpty())return;Step s=new Step();s.pkg=pkg;s.txt=st(n.getText());s.desc=st(n.getContentDescription());s.id=st(n.getViewIdResourceName());android.util.DisplayMetrics m=getResources().getDisplayMetrics();s.x=(double)b.centerX()/m.widthPixels;s.y=(double)b.centerY()/m.heightPixels;long now=SystemClock.uptimeMillis();if(now-lastClick<240&&!path.isEmpty()&&path.get(path.size()-1).pkg.equals(pkg)&&path.get(path.size()-1).txt.equals(s.txt))return;lastClick=now;path.add(s);}
 @Override public void onInterrupt(){} @Override public void onDestroy(){if(sensor!=null)sensor.disable();hide();if(ref==this)ref=null;super.onDestroy();}
 void save(){JSONArray a=new JSONArray();for(Step s:path)a.put(s.json());p.edit().putString("path",a.toString()).apply();}
 public int size(){return path.size();}
 public void baseline(int q){quarter=q;p.edit().putInt("quarter",q).apply();msg("모니터 기준 설정됨");}
 public void autoToggle(){auto=!auto;p.edit().putBoolean("auto",auto).apply();category=-1;msg(auto?"자동회전 시험 ON":"자동회전 OFF");}
 public void record(boolean on){if(busy)return;if(on){path.clear();learning=true;msg("카메라에서 회전까지 직접 눌러주세요.");}else{learning=false;save();msg("학습 경로 "+path.size()+"개 저장");}if(done!=null)done.setVisibility(learning?View.VISIBLE:View.GONE);}
 public void clear(){learning=false;path.clear();save();if(done!=null)done.setVisibility(View.GONE);}
 public void toggle(){if(overlay!=null){hide();p.edit().putBoolean("overlay",false).apply();}else{show();p.edit().putBoolean("overlay",true).apply();}}
 void show(){if(overlay!=null)return;overlay=new LinearLayout(this);overlay.setOrientation(1);Button turn=new Button(this);turn.setText("↻");turn.setTextSize(24);turn.setAllCaps(false);overlay.addView(turn,new LinearLayout.LayoutParams(d(65),d(64)));turn.setOnClickListener(v->rotate());done=new Button(this);done.setText("완료");done.setAllCaps(false);done.setTextSize(12);overlay.addView(done,new LinearLayout.LayoutParams(d(65),d(45)));done.setOnClickListener(v->record(false));done.setVisibility(learning?View.VISIBLE:View.GONE);WindowManager.LayoutParams w=new WindowManager.LayoutParams(d(70),-2,WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE|WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,android.graphics.PixelFormat.TRANSLUCENT);w.gravity=Gravity.END|Gravity.CENTER_VERTICAL;try{wm.addView(overlay,w);}catch(Exception e){overlay=null;msg("플로팅 버튼 표시 실패");}}
 void hide(){if(overlay!=null){try{wm.removeView(overlay);}catch(Exception e){}overlay=null;done=null;}}
 java.util.List<AccessibilityNodeInfo> roots(){ArrayList<AccessibilityNodeInfo> list=new ArrayList<>();AccessibilityNodeInfo a=getRootInActiveWindow();if(a!=null)list.add(a);for(AccessibilityWindowInfo w:getWindows()){AccessibilityNodeInfo n=w.getRoot();if(n!=null)list.add(n);}return list;}
 boolean tap(int x,int y){Path path=new Path();path.moveTo(x,y);return dispatchGesture(new GestureDescription.Builder().addStroke(new GestureDescription.StrokeDescription(path,0,90)).build(),null,null);}
 boolean click(AccessibilityNodeInfo n){AccessibilityNodeInfo c=n;for(int i=0;i<5&&c!=null;i++,c=c.getParent())if(c.isClickable()&&c.performAction(AccessibilityNodeInfo.ACTION_CLICK))return true;Rect r=new Rect();n.getBoundsInScreen(r);return !r.isEmpty()&&tap(r.centerX(),r.centerY());}
 boolean lab(){for(AccessibilityNodeInfo r:roots())for(AccessibilityNodeInfo n:r.findAccessibilityNodeInfosByText("90° 회전"))if(st(n.getText()).contains("90° 회전")||st(n.getContentDescription()).contains("90° 회전"))if(click(n))return true;return false;}
 public void rotate(){if(learning){msg("학습을 먼저 종료해주세요");return;}if(busy)return;pending=1;rotateOnce();}
 void rotateOnce(){if(busy||learning)return;if(lab()){success(false);return;}if(path.isEmpty()){pending=0;msg("학습 경로가 없습니다. Smart View 실험실에서 회전을 눌러주세요.");return;}busy=true;pos=0;
 msg("카메라를 열고 학습 경로 재생 준비 중 ("+path.size()+"단계)");
 try{Intent camera=new Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA);camera.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_SINGLE_TOP);startActivity(camera);}catch(Exception e){busy=false;pending=0;msg("카메라 실행 실패: "+e.getClass().getSimpleName());return;}
 h.postDelayed(()->step(0),1700);
}
 AccessibilityNodeInfo find(Step s){for(AccessibilityNodeInfo r:roots()){ /* SystemUI may host a Samsung overlay with a different root package. */
 if(!s.id.isEmpty())try{List<AccessibilityNodeInfo> v=r.findAccessibilityNodeInfosByViewId(s.id);if(v!=null&&!v.isEmpty())return v.get(0);}catch(Exception e){}
 if(!s.txt.isEmpty())for(AccessibilityNodeInfo n:r.findAccessibilityNodeInfosByText(s.txt))if(s.txt.equals(st(n.getText()))||s.txt.equals(st(n.getContentDescription())))return n;
 if(!s.desc.isEmpty())for(AccessibilityNodeInfo n:r.findAccessibilityNodeInfosByText(s.desc))if(s.desc.equals(st(n.getContentDescription())))return n;
 }return null;}
 boolean hasPackage(String pkg){for(AccessibilityNodeInfo r:roots())if(pkg.equals(st(r.getPackageName())))return true;return false;}
 void step(int retry){if(!busy)return;if(pos>=path.size()){busy=false;success(true);return;}Step s=path.get(pos);AccessibilityNodeInfo n=find(s);boolean ok=n!=null&&click(n);
 if(!ok&&s.txt.isEmpty()&&s.desc.isEmpty()&&hasPackage(s.pkg)){android.util.DisplayMetrics d=getResources().getDisplayMetrics();ok=tap((int)(s.x*d.widthPixels),(int)(s.y*d.heightPixels));}
 if(ok){msg("재생 "+(pos+1)+"/"+path.size()+" 단계 실행");pos++;h.postDelayed(()->step(0),850);}else if(retry<15)h.postDelayed(()->step(retry+1),420);else{busy=false;pending=0;msg("재생 실패: "+(pos+1)+"/"+path.size()+" 단계, "+s.pkg+" 화면에서 버튼을 못 찾음");}}
 void success(boolean returnBack){quarter=(quarter+1)%4;p.edit().putInt("quarter",quarter).apply();pending=Math.max(0,pending-1);msg("회전 버튼 누르기 요청 완료. 모니터 실제 회전 확인 필요");
 if(returnBack){h.postDelayed(()->performGlobalAction(GLOBAL_ACTION_BACK),450);h.postDelayed(()->performGlobalAction(GLOBAL_ACTION_BACK),720);h.postDelayed(()->performGlobalAction(GLOBAL_ACTION_BACK),990);}
 if(pending>0)h.postDelayed(this::rotateOnce,1700);}
}