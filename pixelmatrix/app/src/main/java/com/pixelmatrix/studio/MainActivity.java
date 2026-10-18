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

import java.io.OutputStream;
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
    private ValueCallback<Uri[]> fileChoose;
    private byte[] pendingFile;
    private String pendingMime;
    private ArrayAdapter<String> deviceList;
    private AlertDialog deviceDialog;
    private boolean scanning = false;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(11,15,24));
        getWindow().setNavigationBarColor(Color.rgb(11,15,24));
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        BluetoothManager mgr=(BluetoothManager)getSystemService(BLUETOOTH_SERVICE);
        adapter=mgr==null?null:mgr.getAdapter();
        web=new WebView(this);
        WebSettings settings=web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setDefaultTextEncodingName("UTF-8");
        settings.setSupportZoom(false);
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
            @Override public void onScanFailed(int error){status("BLE 검색 오류: "+error,false);stopScan();}
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
        if(name==null||name.isEmpty())return;
        final String display=name+"  ["+mac+"]";
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
        status("연결 중: "+device.getName(),false);
        try{gatt=device.connectGatt(this,false,callback,BluetoothDevice.TRANSPORT_LE);}
        catch(Exception e){status("연결 오류: "+e.getMessage(),false);}
    }
    private void disconnect(){
        cancelled=true;
        stopScan();
        connected=false;notifyReady=false;tx=null;
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
                status("LED 연결 끊김 (상태 "+st+")",false);
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
            BluetoothGattCharacteristic rx=s.getCharacteristic(NOTIFY);
            if(rx!=null){
                try {
                    b.setCharacteristicNotification(rx,true);
                    BluetoothGattDescriptor desc=rx.getDescriptor(CCCD);
                    if(desc!=null){
                        if(Build.VERSION.SDK_INT>=33)b.writeDescriptor(desc,BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                        else{desc.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);b.writeDescriptor(desc);}
                        notifyReady=true;
                    }
                }catch(Exception ignored){}
            }
            try{b.requestMtu(247);}catch(Exception ignored){}
            cancelled=false;connected=true;
            status("연결됨 · "+b.getDevice().getName(),true);
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
        int type=BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE;
        for(int retry=0;retry<4;retry++){
            boolean ok;
            if(Build.VERSION.SDK_INT>=33){ok=b.writeCharacteristic(c,bytes,type)==BluetoothGatt.GATT_SUCCESS;}
            else {c.setWriteType(type);c.setValue(bytes);ok=b.writeCharacteristic(c);}
            if(ok)return;
            Thread.sleep(50L*(retry+1));
        }
        throw new Exception("BLE 쓰기 요청 실패 (4회 재시도)");
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
                    packet[4]=(byte)(blocks>1?2:0);
                    put32(packet,5,payload.length);put32(packet,9,(int)sum);
                    packet[13]=5;packet[14]=0;packet[15]=(byte)index;
                    System.arraycopy(payload,off,packet,16,plen);
                    for(int i=0;i<packet.length;i+=180){
                        int len=Math.min(180,packet.length-i);
                        sendRaw(Arrays.copyOfRange(packet,i,i+len));
                        Thread.sleep(22);
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
    private static void put32(byte[] arr,int index,int number){
        for(int i=0;i<4;i++)arr[index+i]=(byte)((number>>(i*8))&255);
    }
    public final class Bridge {
        @JavascriptInterface public void connect(){ui.post(()->requestBle());}
        @JavascriptInterface public void disconnect(){ui.post(()->MainActivity.this.disconnect());}
        @JavascriptInterface public boolean isConnected(){return connected;}
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