package com.sj.telesin.buttonrotate;
import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;
public class RotateActivity extends Activity {
 @Override protected void onCreate(Bundle b) {
  super.onCreate(b);
  RotateService s=RotateService.instance;
  if(s==null) Toast.makeText(this,"TELESIN 설정에서 접근성 서비스를 먼저 켜세요.",Toast.LENGTH_LONG).show();
  else s.begin();
  finish();
 }
}
