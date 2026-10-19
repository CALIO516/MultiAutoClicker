package com.pixelmatrix.studio;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Movie;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.view.WindowManager;

import org.json.JSONObject;
import org.json.JSONArray;
import java.util.Calendar;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import org.json.JSONException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.CRC32;
import java.util.Arrays;

public class MainActivity extends Activity {
    private static final UUID SERVICE = UUID.fromString("000000fa-0000-1000-8000-00805f9b34fb");
    private static final UUID WRITE = UUID.fromString("0000fa02-0000-1000-8000-00805f9b34fb");
    private static final UUID NOTIFY = UUID.fromString("0000fa03-0000-1000-8000-00805f9b34fb");
    private static final UUID CCCD = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");
    private static final int REQUEST_PERMISSIONS=100, FILE_SELECT=101, FILE_SAVE=102;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ArrayBlockingQueue<Integer> acks = new ArrayBlockingQueue<>(16);
    private final Map<String, BluetoothDevice> found = Collections.synchronizedMap(new LinkedHashMap<>());
    private WebView web;
    private BluetoothAdapter adapter;
    private BluetoothLeScanner scanner;
    private ScanCallback scanCb;
    private BluetoothGatt gatt;
    private volatile BluetoothGattCharacteristic tx;
    private volatile boolean connected = false;
    private volatile boolean notifyReady = false;
    private volatile boolean cancelled = false;
    private volatile boolean sending = false;
    private volatile int mtuSize = 23;
    private final ArrayBlockingQueue<Integer> writeResults = new ArrayBlockingQueue<>(4);
    private volatile boolean writeWithResponse = true;
    private String lastDeviceName = "";
    private ValueCallback<Uri[]> fileChoose;
    private byte[] pendingFile;
    private String pendingMime;
    private ArrayAdapter<String> deviceList;
    private AlertDialog deviceDialog;
    private boolean scanning = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        getWindow().setStatusBarColor(Color.rgb(9,11,16));
        getWindow().setNavigationBarColor(Color.rgb(9,11,16));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        BluetoothManager mgr=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        adapter=mgr==null?null:mgr.getAdapter();
        web=new WebView(this);
        web.setFitsSystemWindows(true);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if(url.startsWith("file:///android_asset/"))return false;
                return true;
            }
        });
        web.setWebChromeClient(new WebChromeClient(){
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params){
                if(fileChoose!=null)fileChoose.onReceiveValue(null);
                fileChoose=callback;
                try {
                    Intent i=new Intent(Intent.ACTION_GET_CONTENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType("*/*");
                    i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"image/*","application/json"});
                    startActivityForResult(Intent.createChooser(i,"GIF·이미지·프로젝트 선택"),FILE_SELECT);
                } catch(Exception e){fileChoose.onReceiveValue(null);fileChoose=null;return false;}
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(),"NativeApp");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private void js(String name, Object... args){
        try {
            StringBuilder call=new StringBuilder("window.").append(name).append("&&window.").append(name).append("(");
            for(int i=0;i<args.length;i++){
                if(i>0)call.append(',');
                if(args[i] instanceof Number || args[i] instanceof Boolean)call.append(args[i]);
                else call.append(JSONObject.quote(String.valueOf(args[i])));
            }
            call.append(")");
            ui.post(()->{if(web!=null)web.evaluateJavascript(call.toString(),null);});
        } catch(Exception ignored){}
    }
    private void status(String msg, boolean ready){
        js("nativeBleStatus",msg,ready);
    }
    private void log(String msg){js("nativeBleProgress",msg);}
    private boolean allowed(){
        if(Build.VERSION.SDK_INT>=31){
            return checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)==PackageManager.PERMISSION_GRANTED &&
                    checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;
        }
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
    }
    private void requestBle(){
        if(!allowed()) {
            if(Build.VERSION.SDK_INT>=31)requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},REQUEST_PERMISSIONS);
            else requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_PERMISSIONS);
            return;
        }
        if(adapter==null){status("이 휴대폰은 BLE를 지원하지 않습니다.",false);return;}
        if(!adapter.isEnabled()){
            startActivity(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
            status("블루투스를 켜고 연결 버튼을 다시 누르세요.",false);
            return;
        }
        if(connected){disconnect();return;}
        scan();
    }
    @Override public void onRequestPermissionsResult(int req,String[] perms,int[] results){
        super.onRequestPermissionsResult(req,perms,results);
        if(req==REQUEST_PERMISSIONS){if(allowed())requestBle();else status("근처 기기 권한이 필요합니다.",false);}
    }
    private void scan(){
        stopScan();
        scanner=adapter.getBluetoothLeScanner();
        if(scanner==null){status("블루투스 검색을 시작할 수 없습니다.",false);return;}
        found.clear();
        deviceList=new ArrayAdapter<>(this,android.R.layout.simple_list_item_1,new ArrayList<>());
        deviceDialog=new AlertDialog.Builder(this).setTitle("iDotMatrix LED 선택")
            .setAdapter(deviceList,(dialog,which)->{
                String text=deviceList.getItem(which);
                if(text==null)return;
                String mac=text.substring(text.lastIndexOf('[')+1,text.lastIndexOf(']'));
                BluetoothDevice device=found.get(mac);
                stopScan();
                if(device!=null)connect(device);
            })
            .setNegativeButton("취소",(d,w)->stopScan())
            .create();
        deviceDialog.setOnDismissListener(d->stopScan());
        scanCb=new ScanCallback(){
            @Override public void onScanResult(int type,ScanResult res){addDevice(res);}
            @Override public void onBatchScanResults(java.util.List<ScanResult> list){for(ScanResult r:list)addDevice(r);}
            @Override public void onScanFailed(int error){status("BLE 검색 오류 "+error+" · 권한·블루투스 설정을 확인하세요.",false);stopScan();}
        };
        try{scanner.startScan(scanCb);scanning=true;status("주변 LED 검색 중…",false);deviceDialog.show();ui.postDelayed(()->{
            if(scanning){stopScan();status("검색 완료 · 기기를 선택하세요.",false);}
        },14000);}catch(Exception e){status("검색 실패: "+e.getMessage(),false);}
    }
    private void addDevice(ScanResult result){
        if(result==null||result.getDevice()==null)return;
        BluetoothDevice d=result.getDevice();
        String mac=d.getAddress();
        if(found.containsKey(mac))return;
        String name;
        try{name=d.getName();}catch(SecurityException e){name=null;}
        if(name==null||name.isEmpty())name=result.getScanRecord()==null?null:result.getScanRecord().getDeviceName();
        if(name==null||name.isEmpty()) name="이름 없는 BLE 장치";
        final String display=name+"  ["+mac+"]  · "+result.getRssi()+"dBm";
        found.put(mac,d);
        ui.post(()->{
            if(deviceList!=null){deviceList.add(display);deviceList.notifyDataSetChanged();}
        });
    }
    private void stopScan(){
        if(scanning && scanner!=null && scanCb!=null){
            try{scanner.stopScan(scanCb);}catch(Exception ignored){}
        }
        scanning=false;
    }
    private void connect(BluetoothDevice device){
        disconnect();
        cancelled=false; mtuSize=23;
        String name;
        try{name=device.getName();}catch(Exception e){name=null;}
        lastDeviceName=(name==null||name.isEmpty())?"LED 기기":name;
        status("연결 중: "+lastDeviceName+" · 약 10초 소요",false);
        try{
            gatt=device.connectGatt(this,false,callback,BluetoothDevice.TRANSPORT_LE);
            ui.postDelayed(()->{
                if(gatt!=null && !connected && gatt.getDevice().getAddress().equals(device.getAddress())){
                    status("연결 지연 · 공식 앱 연결을 끄고 다시 검색하세요.",false);
                }
            },16000);
        } catch(Exception e){status("연결 오류: "+e.getMessage(),false);}
    }
    private void reconnectLast(){
        if(!allowed()){requestBle();return;}
        if(adapter==null || !adapter.isEnabled()){status("먼저 블루투스를 켜주세요.",false);return;}
        String address=getPreferences(MODE_PRIVATE).getString("last_ble_address","");
        if(address.isEmpty()){scan();return;}
        try{connect(adapter.getRemoteDevice(address));}catch(Exception e){status("최근 기기 연결 실패: "+e.getMessage(),false);scan();}
    }
    private void disconnect(){
        cancelled=true;
        stopScan();
        connected=false;notifyReady=false;tx=null;mtuSize=23;
        acks.clear();writeResults.clear();
        BluetoothGatt previous=gatt;
        gatt=null;
        if(previous!=null){try{previous.disconnect();previous.close();}catch(Exception ignored){}}
        status("연결 해제됨",false);
    }
    private final BluetoothGattCallback callback=new BluetoothGattCallback(){
        @Override public void onConnectionStateChange(BluetoothGatt b,int st,int state){
            if(b!=gatt)return;
            if(st!=BluetoothGatt.GATT_SUCCESS||state==BluetoothProfile.STATE_DISCONNECTED){
                connected=false;tx=null;notifyReady=false;
                status("LED 연결 끊김 (상태 "+st+") · 다시 검색하세요.",false);
                try{b.close();}catch(Exception ignored){}
                if(b==gatt)gatt=null;
                return;
            }
            if(state==BluetoothProfile.STATE_CONNECTED){status("GATT 서비스 검색 중…",false);b.discoverServices();}
        }
        @Override public void onServicesDiscovered(BluetoothGatt b,int st){
            if(b!=gatt)return;
            if(st!=BluetoothGatt.GATT_SUCCESS){status("서비스 검색 실패: "+st,false);return;}
            BluetoothGattService s=b.getService(SERVICE);
            if(s==null){status("FA00 서비스 없음: 지원하지 않는 기기입니다.",false);return;}
            tx=s.getCharacteristic(WRITE);
            if(tx==null){status("FA02 전송 채널 없음",false);return;}
            int props=tx.getProperties();
            writeWithResponse=(props & BluetoothGattCharacteristic.PROPERTY_WRITE)!=0;
            notifyReady=false;
            BluetoothGattCharacteristic rx=s.getCharacteristic(NOTIFY);
            if(rx!=null){
                try {
                    boolean enabled=b.setCharacteristicNotification(rx,true);
                    BluetoothGattDescriptor desc=rx.getDescriptor(CCCD);
                    if(enabled&&desc!=null){
                        int code;
                        if(Build.VERSION.SDK_INT>=33)code=b.writeDescriptor(desc,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        else{desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);code=b.writeDescriptor(desc)?0:1;}
                        notifyReady=code==0;
                        if(!notifyReady)try{b.requestMtu(247);}catch(Exception ignored){}
                    }else{try{b.requestMtu(247);}catch(Exception ignored){}}
                }catch(Exception ignored){try{b.requestMtu(247);}catch(Exception ignored2){}}
            }else{try{b.requestMtu(247);}catch(Exception ignored){}}
            cancelled=false;connected=true;
            try{getPreferences(MODE_PRIVATE).edit().putString("last_ble_address",b.getDevice().getAddress()).apply();}catch(Exception ignored){}
            status("연결됨 · "+lastDeviceName,true);
            log("BLE 연결 완료 · 전송 채널 FA02 확인. MTU 협상 중…");
        }
        @Override public void onDescriptorWrite(BluetoothGatt b,BluetoothGattDescriptor d,int result){
            if(b!=gatt)return;
            notifyReady=result==BluetoothGatt.GATT_SUCCESS;
            try{b.requestMtu(247);}catch(Exception ignored){}
        }
        @Override public void onMtuChanged(BluetoothGatt b,int newMtu,int result){
            if(b!=gatt)return;
            if(result==BluetoothGatt.GATT_SUCCESS)mtuSize=Math.max(23,newMtu);
            log("BLE 최대 전송 단위: "+Math.max(20,Math.min(244,mtuSize-3))+"바이트");
        }
        @Override public void onCharacteristicWrite(BluetoothGatt b,BluetoothGattCharacteristic c,int result){
            if(b==gatt)writeResults.offer(result);
        }
        @Override public void onCharacteristicChanged(BluetoothGatt b,BluetoothGattCharacteristic c){recv(c.getValue());}
        @Override public void onCharacteristicChanged(BluetoothGatt b,BluetoothGattCharacteristic c,byte[] val){recv(val);}
        private void recv(byte[] val){
            if(val==null||val.length<5)return;
            if(val[2]==1||val[2]==2||val[2]==3){
                acks.offer(val[4]&255);
            }
        }
    };
    private void sendRaw(byte[] bytes) throws Exception{
        BluetoothGatt b=gatt;
        BluetoothGattCharacteristic c=tx;
        if(b==null||c==null||!connected||cancelled)throw new Exception("BLE 연결이 종료되었습니다.");
        if(bytes.length>Math.max(20,Math.min(244,mtuSize-3)))throw new Exception("전송 바이트가 BLE MTU를 초과합니다: "+bytes.length);
        int type=writeWithResponse?BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT:BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        for(int retry=0;retry<3;retry++){
            writeResults.clear();
            boolean ok;
            if(Build.VERSION.SDK_INT>=33){ok=b.writeCharacteristic(c,bytes,type)==BluetoothGatt.GATT_SUCCESS;}
            else{c.setWriteType(type);c.setValue(bytes);ok=b.writeCharacteristic(c);}
            if(ok){
                if(writeWithResponse){
                    Integer result=writeResults.poll(2500,TimeUnit.MILLISECONDS);
                    if(result!=null&&result==BluetoothGatt.GATT_SUCCESS)return;
                }else{Thread.sleep(25);return;}
            }
            Thread.sleep(65L*(retry+1));
        }
        throw new Exception("BLE 패킷 쓰기 실패 · 거리·연결 상태를 확인하세요.");
    }
    private void sendCommand(byte... packet){
        io.execute(()->{
            try{sendRaw(packet);Thread.sleep(50);}
            catch(Exception e){log("전송 오류: "+e.getMessage());}
        });
    }
    private void bulk(byte[] payload,int dataType,int index){
        if(sending){log("다른 파일 전송이 진행 중입니다.");return;}
        sending=true;
        io.execute(()->{
            boolean unconfirmed=false;
            try{
                if(payload.length==0||payload.length>3_000_000)throw new Exception("파일 크기는 3MB 이하만 지원합니다.");
                acks.clear();
                if(dataType==2){sendRaw(new byte[]{5,0,4,1,0});Thread.sleep(60);}
                CRC32 crc=new CRC32();crc.update(payload);
                long sum=crc.getValue();
                int blocks=(payload.length+4095)/4096;
                for(int part=0;part<blocks;part++){
                    if(cancelled||!connected)throw new Exception("장치 연결이 끊겼습니다.");
                    int off=part*4096, plen=Math.min(4096,payload.length-off);
                    byte[] packet=new byte[16+plen];
                    packet[0]=(byte)(packet.length&255);packet[1]=(byte)(packet.length>>8);
                    packet[2]=(byte)dataType;packet[3]=0;
                    packet[4]=(byte)(part==0?0:2);
                    put32(packet,5,payload.length);put32(packet,9,(int)sum);
                    packet[13]=5;packet[14]=0;packet[15]=(byte)index;
                    System.arraycopy(payload,off,packet,16,plen);
                    int step=Math.max(20,Math.min(244,mtuSize-3));
                    for(int i=0;i<packet.length;i+=step){
                        int len=Math.min(step,packet.length-i);
                        sendRaw(Arrays.copyOfRange(packet,i,i+len));
                    }
                    log("전송 중: "+(part+1)+" / "+blocks+" 블록 ("+(int)((part+1)*100.0/blocks)+"%)");
                    if(notifyReady){
                        Integer code=acks.poll(5,TimeUnit.SECONDS);
                        if(code!=null&&code==2)throw new Exception("장치 저장 공간 부족 (ACK 2)");
                        if(code==null)unconfirmed=true;
                    }else{unconfirmed=true;Thread.sleep(500);}
                }
                if(unconfirmed)log("전송 요청 완료 · ACK 미확인. LED 표시 결과를 확인하세요.");
                else log("BLE 데이터 수신 확인됨 · LED 표시 결과를 확인하세요.");
            } catch(Exception e){log("전송 실패: "+e.getMessage());}
            finally{sending=false;}
        });
    }
    private static int clamp(int n,int min,int max){return Math.max(min,Math.min(max,n));}
    private static void put32(byte[] arr,int index,int number){
        for(int i=0;i<4;i++)arr[index+i]=(byte)((number>>(i*8))&255);
    }
    public final class Bridge {
        @JavascriptInterface public void connect(){ui.post(()->requestBle());}
        @JavascriptInterface public void disconnect(){ui.post(()->MainActivity.this.disconnect());}
        @JavascriptInterface public void reconnect(){ui.post(()->reconnectLast());}
        @JavascriptInterface public boolean isConnected(){return connected;}

        @JavascriptInterface public String appVersion(){
            try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}
            catch(Exception e){return "3.5.0";}
        }
        @JavascriptInterface public String decodeGifFrames(String base64,int target,int maxFrames,String fit){
            try{
                if(target!=16&&target!=32&&target!=64)throw new Exception("출력 해상도 오류");
                byte[] bytes=Base64.decode(base64,Base64.DEFAULT);
                if(bytes.length>50*1024*1024)throw new Exception("50MB를 초과한 GIF");
                Movie movie=Movie.decodeByteArray(bytes,0,bytes.length);
                if(movie==null)throw new Exception("Android 기본 디코더에서 처리할 수 없는 GIF");
                int total=movie.duration();if(total<=0)total=1000;
                int n=Math.max(1,Math.min(90,Math.min(maxFrames,Math.max(1,(int)Math.ceil(total/90.0)))));
                JSONArray pictures=new JSONArray(),delays=new JSONArray();
                for(int i=0;i<n;i++){
                    int t=(int)((long)i*total/n);movie.setTime(t);
                    Bitmap bmp=Bitmap.createBitmap(target,target,Bitmap.Config.ARGB_8888);
                    Canvas cv=new Canvas(bmp);cv.drawColor(Color.BLACK);
                    float w=Math.max(1,movie.width()),h=Math.max(1,movie.height());
                    float factor="cover".equals(fit)?Math.max(target/w,target/h):Math.min(target/w,target/h);
                    if("stretch".equals(fit))cv.scale(target/w,target/h);
                    else {cv.translate((target-w*factor)/2f,(target-h*factor)/2f);cv.scale(factor,factor);}
                    movie.draw(cv,0,0,new Paint(Paint.FILTER_BITMAP_FLAG));
                    ByteArrayOutputStream stream=new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.PNG,100,stream);
                    pictures.put(Base64.encodeToString(stream.toByteArray(),Base64.NO_WRAP));
                    delays.put((int)Math.max(30,(long)total/n));bmp.recycle();
                }
                JSONObject out=new JSONObject();out.put("frames",pictures);out.put("delays",delays);
                out.put("durationMs",total);out.put("sourceFrames",n);return out.toString();
            }catch(Exception ex){try{return new JSONObject().put("error",ex.getMessage()).toString();}
                catch(Exception ignored){return "{\"error\":\"unknown\"}";}}
        }

        private File libraryFolder(){
            File folder=new File(getFilesDir(),"gif-library-v1");
            if(!folder.exists())folder.mkdirs();
            return folder;
        }
        @JavascriptInterface public String saveLibraryItem(String json){
            try{
                if(json==null || json.length()>17_000_000)throw new Exception("저장 용량 제한(17MB)");
                JSONObject item=new JSONObject(json);
                String id=item.optString("id","");
                if(!id.matches("user_[0-9]{10,17}_[a-z0-9]{3,10}"))throw new Exception("GIF 식별자가 올바르지 않습니다.");
                if(!item.optBoolean("custom")||!"32x32".equals(item.optString("resolution")))throw new Exception("32×32 개인 GIF만 저장됩니다.");
                File folder=libraryFolder();
                File[] existing=folder.listFiles((f,name)->name.endsWith(".json"));
                if(existing!=null && existing.length>=250)throw new Exception("내 GIF 보관함은 최대 250개입니다.");
                File tmp=new File(folder,id+".tmp"),dest=new File(folder,id+".json");
                byte[] data=json.getBytes(StandardCharsets.UTF_8);
                try(FileOutputStream fos=new FileOutputStream(tmp)){fos.write(data);fos.getFD().sync();}
                if(!tmp.renameTo(dest)){
                    try(FileOutputStream fos=new FileOutputStream(dest)){fos.write(data);fos.getFD().sync();}
                    tmp.delete();
                }
                return new JSONObject().put("ok",true).toString();
            }catch(Exception ex){try{return new JSONObject().put("error",ex.getMessage()).toString();}catch(Exception ignored){return "{\"error\":\"save failed\"}";}}
        }
        @JavascriptInterface public String loadLibraryItems(){
            try{
                JSONArray arr=new JSONArray();File folder=libraryFolder();
                File[] files=folder.listFiles((f,name)->name.matches("user_[0-9]{10,17}_[a-z0-9]{3,10}\\.json"));
                if(files!=null){
                    long loadedBytes=0;
                    Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));
                    for(int i=0;i<Math.min(files.length,250);i++){
                        if(files[i].length()>17_000_000)continue;
                        if(loadedBytes+files[i].length()>24_000_000)break;
                        loadedBytes+=files[i].length();
                        ByteArrayOutputStream b=new ByteArrayOutputStream();
                        try(FileInputStream stream=new FileInputStream(files[i])){
                            byte[] buf=new byte[8192];int n;
                            while((n=stream.read(buf))!=-1)b.write(buf,0,n);
                        }
                        try{arr.put(new JSONObject(new String(b.toByteArray(),StandardCharsets.UTF_8)));}catch(Exception ignored){}
                    }
                }
                return new JSONObject().put("items",arr).toString();
            }catch(Exception ex){try{return new JSONObject().put("error",ex.getMessage()).toString();}catch(Exception ignored){return "{\"error\":\"load failed\"}";}}
        }

        @JavascriptInterface public String galleryGif(String fileName){
            if(fileName==null || !fileName.matches("(?:v22/pixel_[0-9]{3}|premium_[0-9]{3})\\.gif"))return "";
            try(InputStream in=getAssets().open("gallery/"+fileName);ByteArrayOutputStream out=new ByteArrayOutputStream()){
                byte[] buf=new byte[4096];int n;int total=0;
                while((n=in.read(buf))!=-1){total+=n;if(total>512000)return "";out.write(buf,0,n);}
                return Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
            }catch(Exception ex){return "";}
        }

        @JavascriptInterface public void rgb(String b64,int dimension){
            if(!connected){log("먼저 LED 기기에 연결하세요.");return;}
            try{
                byte[] raw=Base64.decode(b64,Base64.DEFAULT);
                if(dimension!=16&&dimension!=32&&dimension!=64)throw new Exception("지원하지 않는 픽셀 크기");
                if(raw.length!=dimension*dimension*3)throw new Exception("RGB 데이터 크기 불일치");
                bulk(raw,2,12);
            }catch(Exception e){log("이미지 오류: "+e.getMessage());}
        }
        @JavascriptInterface public void gif(String b64,int index){
            if(!connected){log("먼저 LED 기기에 연결하세요.");return;}
            try{
                byte[] raw=Base64.decode(b64,Base64.DEFAULT);
                if(raw.length<13||raw[0]!='G'||raw[1]!='I'||raw[2]!='F')throw new Exception("잘못된 GIF 데이터");
                bulk(raw,1,index==12?12:13);
            }catch(Exception e){log("GIF 오류: "+e.getMessage());}
        }
        @JavascriptInterface public void pixel(int x,int y,int r,int g,int b){
            if(!connected||sending)return;
            if(x<0||x>63||y<0||y>63)return;
            sendCommand((byte)10,(byte)0,(byte)5,(byte)1,(byte)0,(byte)r,(byte)g,(byte)b,(byte)x,(byte)y);
        }
        @JavascriptInterface public void control(String command,int value){
            if(!connected){log("먼저 LED 기기를 연결하세요.");return;}
            if("power".equals(command))sendCommand((byte)5,(byte)0,(byte)7,(byte)1,(byte)(value>0?1:0));
            else if("brightness".equals(command))sendCommand((byte)5,(byte)0,(byte)4,(byte)0x80,(byte)Math.min(100,Math.max(0,value)));
            else if("color".equals(command))sendCommand((byte)7,(byte)0,(byte)2,(byte)2,(byte)(value>>16),(byte)(value>>8),(byte)value);
            else if("diy".equals(command))sendCommand((byte)5,(byte)0,(byte)4,(byte)1,(byte)(value>0?1:0));
        }
        @JavascriptInterface public void deviceCommand(String command,String jsonValues){
            if(!connected){log("먼저 LED 기기에 연결하세요.");return;}
            try{
                JSONArray arg=new JSONArray(jsonValues);
                byte[] packet;
                switch(command){
                    case "syncTime":{
                        Calendar c=Calendar.getInstance();
                        int dow=(c.get(Calendar.DAY_OF_WEEK)+5)%7+1;
                        packet=new byte[]{11,0,1,(byte)0x80,(byte)(c.get(Calendar.YEAR)-2000),(byte)(c.get(Calendar.MONTH)+1),(byte)c.get(Calendar.DAY_OF_MONTH),(byte)dow,(byte)c.get(Calendar.HOUR_OF_DAY),(byte)c.get(Calendar.MINUTE),(byte)c.get(Calendar.SECOND)};
                        break;
                    }
                    case "clock":{
                        int style=clamp(arg.optInt(0),0,7),date=arg.optInt(1),hour24=arg.optInt(2),rgb=arg.optInt(3,0xffffff);
                        int flags=style|(date>0?0x80:0)|(hour24>0?0x40:0);
                        packet=new byte[]{8,0,6,1,(byte)flags,(byte)(rgb>>16),(byte)(rgb>>8),(byte)rgb};
                        break;
                    }
                    case "effect":{
                        int style=clamp(arg.optInt(0),0,6),speed=clamp(arg.optInt(1),0,100),rgb=arg.optInt(2,0x55ddcc);
                        // 2-color built-in animation (effect CMD 3 / SUB 2).
                        packet=new byte[]{13,0,3,2,(byte)style,(byte)speed,2,(byte)(rgb>>16),(byte)(rgb>>8),(byte)rgb,(byte)0x20,(byte)0x40,(byte)0x60};
                        break;
                    }
                    case "flip":packet=new byte[]{5,0,6,(byte)0x80,(byte)(arg.optInt(0)>0?1:0)};break;
                    case "countdown":packet=new byte[]{7,0,8,(byte)0x80,(byte)clamp(arg.optInt(0),0,3),(byte)clamp(arg.optInt(1),0,255),(byte)clamp(arg.optInt(2),0,59)};break;
                    case "stopwatch":packet=new byte[]{5,0,9,(byte)0x80,(byte)clamp(arg.optInt(0),0,3)};break;
                    case "score":{
                        int a=clamp(arg.optInt(0),0,9999),b=clamp(arg.optInt(1),0,9999);
                        packet=new byte[]{8,0,10,(byte)0x80,(byte)a,(byte)(a>>8),(byte)b,(byte)(b>>8)};
                        break;
                    }
                    case "eco":{
                        int enabled=arg.optInt(0)>0?1:0,start=clamp(arg.optInt(1),0,23),end=clamp(arg.optInt(2),0,23),brightness=clamp(arg.optInt(3),1,100);
                        packet=new byte[]{10,0,2,(byte)0x80,(byte)enabled,(byte)start,0,(byte)end,0,(byte)brightness};
                        break;
                    }
                    default:throw new Exception("알 수 없는 기기 명령: "+command);
                }
                sendCommand(packet);
                log("기기 제어 요청: "+command+" (표시 결과 확인)");
            }catch(Exception e){log("기기 명령 오류: "+e.getMessage());}
        }
        @JavascriptInterface public void save(String name,String mime,String base64){
            try{
                byte[] data=Base64.decode(base64,Base64.DEFAULT);
                if(data.length>12*1024*1024)throw new Exception("12MB보다 큰 파일은 저장할 수 없습니다.");
                pendingFile=data;pendingMime=mime;
                ui.post(()->{
                    Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType(mime);
                    i.putExtra(Intent.EXTRA_TITLE,name.replace('/','_'));
                    startActivityForResult(i,FILE_SAVE);
                });
            }catch(Exception e){log("파일 저장 오류: "+e.getMessage());}
        }
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);
        if(request==FILE_SELECT){
            if(fileChoose!=null){Uri[] uris=result==RESULT_OK&&data!=null&&data.getData()!=null?new Uri[]{data.getData()}:null;fileChoose.onReceiveValue(uris);fileChoose=null;}
        }else if(request==FILE_SAVE){
            if(result==RESULT_OK&&data!=null&&data.getData()!=null&&pendingFile!=null){
                try(OutputStream out=getContentResolver().openOutputStream(data.getData())){
                    if(out==null)throw new Exception("출력 파일을 열 수 없습니다.");
                    out.write(pendingFile);
                    log("파일 저장 완료");
                }catch(Exception e){log("저장 실패: "+e.getMessage());}
            }
            pendingFile=null;
        }
    }
    @Override public void onBackPressed(){
        if(web!=null)web.evaluateJavascript("window.nativeBack ? window.nativeBack() : false",result->{
            if("false".equals(result))ui.post(()->new AlertDialog.Builder(this)
                .setMessage("PixelMatrix Studio를 종료할까요?")
                .setNegativeButton("취소",null).setPositiveButton("종료",(d,w)->finish()).show());
        });
        else super.onBackPressed();
    }
    @Override protected void onDestroy(){
        disconnect();io.shutdownNow();
        if(web!=null){web.removeJavascriptInterface("NativeApp");web.destroy();web=null;}
        super.onDestroy();
    }
}