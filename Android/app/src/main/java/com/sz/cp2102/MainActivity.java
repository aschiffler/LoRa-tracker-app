package com.sz.cp2102;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.blankj.utilcode.util.BarUtils;
import com.blankj.utilcode.util.ColorUtils;
import com.clj.fastble.utils.HexUtil;
import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;
import com.hoho.android.usbserial.util.SerialInputOutputManager;
import com.lxj.xpopup.XPopup;
import com.lxj.xpopup.core.BasePopupView;
import com.lxj.xpopup.impl.InputConfirmPopupView;
import com.lxj.xpopup.interfaces.OnCancelListener;
import com.lxj.xpopup.interfaces.OnConfirmListener;
import com.lxj.xpopup.interfaces.OnInputConfirmListener;
import com.lxj.xpopup.interfaces.OnSelectListener;
import com.sz.cp2102.bean.LogBean;
import com.sz.cp2102.service.BackstageService;
import com.sz.cp2102.utils.LogUtil;
import com.sz.cp2102.utils.PreferencesUtil;
import com.sz.cp2102.utils.TextUtils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import pub.devrel.easypermissions.EasyPermissions;

public class MainActivity extends Activity implements SerialInputOutputManager.Listener, EasyPermissions.PermissionCallbacks, ServiceConnection {
    private TextView text_statu1;
    private TextView text_statu2;
    private TextView text_statu31;

    private ImageView img1;

    private ListView listView;
    private LogAdapter logAdapter;
    private SwipeRefreshLayout swipeRefreshLayout;
    public List<LogBean> logList = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lan);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        BarUtils.setStatusBarColor(this, ColorUtils.getColor(R.color.transparent));
        locationTime = PreferencesUtil.getInt(MainActivity.this, "locationTime", locationTime);

        initView();
        time3();
        String[] perms = {Manifest.permission.ACCESS_FINE_LOCATION};

        if (EasyPermissions.hasPermissions(this, perms)) {
            initMap();
        } else {
            EasyPermissions.requestPermissions(this, "Apply for document read, write and location functions", 10002, perms);
        }
    }

    @Override
    public void onPermissionsGranted(int requestCode, @NonNull List<String> list) {
        initMap();
    }

    @Override
    public void onPermissionsDenied(int requestCode, @NonNull List<String> list) {
        showText("Please give location permission manually");
    }

    public void showText(String str) {
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show();
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    private Boolean seleteHex = false;

    public void initView() {
        img1 = findViewById(R.id.img1);
        text_statu1 = findViewById(R.id.text_statu1);
        text_statu1.setText("No Device detected");
        text_statu2 = findViewById(R.id.text_statu2);
        text_statu31 = findViewById(R.id.text_statu31);

        findViewById(R.id.btn_Reconnection1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    checkConnect();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        listView = findViewById(R.id.listView);
        listView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);
        listView.setStackFromBottom(true);
        logAdapter = new LogAdapter(this);
        logAdapter.setSeleteHex(seleteHex);
        logAdapter.addResult(logList);
        listView.setAdapter(logAdapter);
//        swipeRefreshLayout.setRefreshing(false);
        findViewById(R.id.btn_send_hex).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                selectPopup5.show();
            }
        });

        findViewById(R.id.btn_save).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                System.exit(0);
            }
        });
        btn_location = findViewById(R.id.btn_location);
        btn_location.setText("Uplink Interval（" + locationTime + "s）");

        findViewById(R.id.btn_location1).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                InputConfirmPopupView a = new XPopup.Builder(MainActivity.this).asInputConfirm("Interval [s]", "",
                        text -> {
                            try {
                                locationTime = Integer.parseInt(text);
                                PreferencesUtil.putInt(MainActivity.this, "locationTime", locationTime);
                                btn_location.setText("Uplink Interval（" + locationTime + "s）");
                                mLocationClient.stopLocation();
                                mLocationOption.setInterval(locationTime * 1000);
                                mLocationClient.setLocationOption(mLocationOption);
                                mLocationClient.startLocation();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                EditText et_input = a.findViewById(R.id.et_input);
                et_input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
                TextView textView = a.findViewById(R.id.tv_confirm);
                textView.setText("Ok");
                TextView textView1 = a.findViewById(R.id.tv_cancel);
                textView1.setText("Cancel");
                a.show();
            }
        });

    }


    private long sendtime = 0;
    private Boolean isSendData = false;

    public void send(String send) {
        try {
            timeNum = 0;
            Log.e("tyyy", send + "*");
            Log.e("tyyy", send.indexOf("SENDB") + "*");
            Log.e("tyyy", send + "*" + isSendData);
            if (isSendData) {
                if (send.indexOf("SENDB") != -1) {
                    return;
                }
                if (sendDataList.size() == 0) {
                    sendtime = new Date().getTime();
                    sendDataList.add(send);
                    if (send.indexOf("NJS") == -1)
                        runOnUiThread(() -> {
                            showText("Please send it automatically after the contract is awarded");
                        });
                } else {
                    if (send.indexOf("NJS") == -1)
                        runOnUiThread(() -> {
                            showText("There are instructions to be sent, please wait");
                        });
                }
                return;
            }
            String hex1 = TextUtils.strToASCII(send) + "0D0A";

            LogBean logBean = new LogBean();
            logBean.setText(send + TextUtils.decode("0D0A"));
            logBean.setTime(new Date().getTime());
            logBean.setType(2);
            logList.add(logBean);
            runOnUiThread(() -> {
                logAdapter.addResult(logList);
                logAdapter.notifyDataSetChanged();
                listView.setSelection(listView.getBottom());
            });
            if (isConnect) {
                MyApplication.port.write(HexUtil.hexStringToBytes(hex1), 3000);
                if (send.indexOf("SENDB") != -1) {
                    isSendData = true;
                }
            }
        } catch (Exception e) {
            Log.e("Exception", "send");
            e.printStackTrace();
        }
    }

    private TextView btn_location;
    private int locationTime = 30;
    private BasePopupView selectPopup5;
    public Boolean isConnect = false;
    public Boolean isLan = false;

    public void clearLog() {
        logList.clear();
        logAdapter.addResult(logList);
        logAdapter.notifyDataSetChanged();
    }

    private final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";

    public UsbSerialDriver driver;
    public UsbManager manager1;

    public void getDriver() throws Exception {
        manager1 = (UsbManager) getSystemService(Context.USB_SERVICE);
        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager1);
        if (availableDrivers.isEmpty()) {
            return;
        }
        driver = availableDrivers.get(0);
        if (manager1.hasPermission(driver.getDevice())) {
            getConnection();
        } else {
            IntentFilter filter = new IntentFilter();
            filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
            filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
            filter.addAction(ACTION_USB_PERMISSION);
            registerReceiver(receiver, filter);
            PendingIntent mPermissionIntent = PendingIntent.getBroadcast(this, 0,
                    new Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE);
            manager1.requestPermission(driver.getDevice(), mPermissionIntent);
        }
    }

    private UsbDeviceConnection connection;

    public void getConnection() throws Exception {
        connection = manager1.openDevice(driver.getDevice());
        if (connection == null) {
            return;
        }
        if (MyApplication.port != null) {
            MyApplication.port.close();
        }
        MyApplication.port = driver.getPorts().get(0); // Most devices have just one port (port 0)
        MyApplication.port.open(connection);
        MyApplication.port.setParameters(9600, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

        success();
        Log.e("UsbDeviceConnection", "success");
        if (myService == null) {
            bindService(new Intent(this, BackstageService.class), this, BIND_AUTO_CREATE);
        } else {
            myService.reStart();
        }

        time1();
//        time2();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.e("onDestroy", "onDestroy");
        MyApplication.port = null;

        myService.onDestroy();
        myService = null;
    }

    private BackstageService myService;

    @Override
    public void onServiceConnected(ComponentName name, IBinder service) {
        Log.e("onServiceConnected", "onServiceConnected");
        BackstageService.LocalBinder binder = (BackstageService.LocalBinder) service;
        myService = binder.getService();
        myService.setCallback(new BackstageService.Callback() {
            @Override
            public void onDataChange(byte[] buffer, int length) {
                String recv = HexUtil.formatHexString(buffer, true);
                if (startTime == 0) {
                    str = str + recv;
                } else {
                    str = str + recv;
                }
                startTime = new Date().getTime();
                if (isSendData & ((sendTIme - startTime) > 5000)) {
                    runOnUiThread(() -> {
                        text_statu31.setText("--");
                        img1.setImageDrawable(getResources().getDrawable(R.mipmap.img_rssi1));
                    });
                } else if (isSendData & ((sendTIme - startTime) > 7000)) {
                    isSendData = false;
                    sendTIme = 0;
                    if (sendDataList.size() > 0) {
                        send(sendDataList.get(sendDataList.size() - 1));
                        sendDataList.remove(sendDataList.size() - 1);
                    }
                }
            }

            @Override
            public void onRunError() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        text_statu1.setText("No Device detected");
                        isConnect = false;
                        text_statu2.setText("LoRaWAN：Offline");
                        isSendData = false;
                        isLan = false;
                    }
                });

            }
        });
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                Log.e("BroadcastReceiver", "BroadcastReceiver");
                if (granted) {
                    try {
                        getConnection();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                } else {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false) && driver.getDevice().equals(device)) {
                        try {
                            getConnection();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }

                context.unregisterReceiver(receiver);
            }
        }
    };


    public void success() {

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                text_statu1.setText("Device connected");
                isConnect = true;
            }
        });
    }

    public void error() {

    }

    public Timer timer1;

    public Timer timer2;
    public Timer timer3;

    public void time3() {
        if (timer3 != null) {
            timer3 = null;
            timer3 = new Timer();
        } else {
            timer3 = new Timer();
        }
        timer3.schedule(new TimerTask() {
            @Override
            public void run() {
                if (!isConnect) {
                    try {
                        Log.e("isConnect", "schedule");
                        getDriver();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

            }
        }, 1000 * 1, 1000 * 3);
    }

    public void time2() {
        if (timer2 != null) {
            timer2 = null;
            timer2 = new Timer();
        } else {
            timer2 = new Timer();
        }
        timer2.schedule(new TimerTask() {
            @Override
            public void run() {
//                try {
                    //checkConnect();
//                    new readThread().start();
//                } catch (IOException e) {
 //                   e.printStackTrace();
//                }
            }
        }, 1000 * 1, 1000 * 30);
    }

    private int timeNum = 0;

    public void time1() {
        if (timer1 != null) {
            timer1.cancel();
            timer1 = null;
            timer1 = new Timer();
        } else {
            timer1 = new Timer();
        }
        timer1.schedule(new TimerTask() {
            @Override
            public void run() {
                endTime = new Date().getTime();

                if (endTime > startTime + 100 && startTime != 0) {
                    runOnUiThread(() -> {
                        try {
                            Log.e("schedule", str);
//                            Log.e("ttt", str.length() + "**");
                            String stt = str.replace(" ", "").toUpperCase();
//                            Log.e("ttt", stt + "**");
//                            Log.e("ttt", TextUtils.decode(stt));

                            if (stt.contains("527373693D20")) {
                                timeNum = 0;
                                String str1 = stt.split("527373693D202D")[1].split("0D")[0];
                                String str = stt.split("527373693D202D")[1].split("0D")[0];
                                Log.e("***777", str);
                                Log.e("***777", TextUtils.decode(str));
                                Log.e("***7771", sendTIme + "*");
//                                text_statu3.setText("LoRaWAN RSSI:" + TextUtils.decode("2D" + str));
                                text_statu31.setText(TextUtils.decode("2D" + str));
                                int rssi = Integer.valueOf(TextUtils.decode(str));
                                if (rssi > 129) {
                                    img1.setImageDrawable(getResources().getDrawable(R.mipmap.img_rssi1));
                                } else if (rssi > 109) {
                                    img1.setImageDrawable(getResources().getDrawable(R.mipmap.img_rssi2));
                                } else if (rssi > 90) {
                                    img1.setImageDrawable(getResources().getDrawable(R.mipmap.img_rssi3));
                                } else if (rssi > 70) {
                                    img1.setImageDrawable(getResources().getDrawable(R.mipmap.img_rssi4));
                                } else {
                                    img1.setImageDrawable(getResources().getDrawable(R.mipmap.img_rssi5));
                                }
                                if (dataList.size() > 0)
                                    dataList.remove(dataList.size() - 1);
                                sendTIme = 0;
                                isSendData = false;
//                                dataList.clear();
                                Log.e("datalist", dataList.size() + "**");
                                if (dataList.size() > 0) {
                                    send("AT+SENDB=01,02," + dataList.get(dataList.size() - 1).length() / 2 + "," + dataList.get(dataList.size() - 1));
                                } else {
                                    if (sendDataList.size() > 0) {
                                        send(sendDataList.get(sendDataList.size() - 1));
                                        sendDataList.remove(sendDataList.size() - 1);
                                    }
                                }
                            }
                            if (stt.contains("0D0A0D0A4F4B0D0A") && stt.length() == 18) {
                                timeNum = 0;
                                if (stt.split("0D0A0D0A4F4B0D0A")[0].equalsIgnoreCase("31")) {
                                    text_statu2.setText("LoRaWAN：Online");
                                    if (!isLan) {
                                        isLan = true;
//                                        send("AT+JOIN?");
                                    }
//                                            send("AT+RSSI=?");
                                } else {
                                    text_statu2.setText("LoRaWAN：Offline");
                                    isLan = false;
                                    isSendData = false;
                                }
                            }
                            Log.e("sttschedule", stt);
                            Log.e("sttschedule", ":" + stt.indexOf("727854696d656f7574"));

                            if (stt.indexOf("727854696d656f7574") != -1 || stt.indexOf("727854696D656F7574") != -1
                                    || stt.indexOf("41545F425553595F4552524F50") != -1 || stt.indexOf("41545f425553595f4552524f50") != -1) {
                                sendTIme = 0;
                                isSendData = false;
                                timeNum++;
                                if (timeNum == 2) {
                                    runOnUiThread(() -> {
                                        text_statu31.setText("--");
                                        img1.setImageDrawable(getResources().getDrawable(R.mipmap.img_rssi1));
                                    });
                                }
                            }
                            LogBean logBean = new LogBean();
                            logBean.setText(TextUtils.decode(stt));
                            logBean.setTime(new Date().getTime());
                            logBean.setType(1);
                            logList.add(logBean);
                            logAdapter.addResult(logList);
                            logAdapter.notifyDataSetChanged();

                            listView.setSelection(listView.getBottom());

                            startTime = 0;
                            str = "";
                            Log.e("Exception", "notifyDataSetChanged:" + logList.size());
                            Log.e("Exception", "notifyDataSetChanged:" + listView.getBottom());
                            Log.e("Exception", "notifyDataSetChanged");
                        } catch (Exception e) {
                            Log.e("Exception", "Exceptiontime1");
                            e.printStackTrace();
                            startTime = 0;
                            str = "";
                        }
                    });

                }
                if (endTime > sendTIme + 7000 && sendTIme != 0 && rssiData.length() != 0) {
                    Log.e("***777sendTIme", sendTIme + "*");
                    Log.e("***777sendTIme", endTime + "*");
//                    if(dataList.size()==100){
//                        dataList.remove(0);
//                    }
//                    dataList.add(rssiData);
//                    sendTIme=0;
//                    rssiData="";
                }
            }
        }, 1000 * 2, 1000 * 1 + 100);

    }

    public void checkConnect() throws IOException {
        send("AT+NJS=?");
    }


    private long startTime = 0;
    private long endTime = 0;
    private String str = "";

    @Override
    public void onNewData(byte[] data) {

    }

    @Override
    public void onRunError(Exception e) {
        Log.e("onRunError", "onRunError");
    }


    private String TAG = "LAnActivity";
    //声明AMapLocationClientOption对象
    public AMapLocationClientOption mLocationOption = null;
    //声明AMapLocationClient类对象
    public AMapLocationClient mLocationClient = null;
    private double latitude;
    private double longitude;

    private long sendTIme = 0;
    private ArrayList<String> dataList = new ArrayList<>();
    private ArrayList<String> sendDataList = new ArrayList<>();
    private String rssiData = "";

    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    //声明定位回调监听器
    LocationListener locationListenerGPS = aMapLocation -> {
        ByteBuffer buffer = ByteBuffer.allocate(12);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putFloat((float) aMapLocation.getLatitude());
        buffer.putFloat((float) aMapLocation.getLongitude());
        buffer.putFloat((float) aMapLocation.getAltitude());
        String data = bytesToHex(buffer.array());
        TextView info = findViewById(R.id.btn_send_hex);
        info.setText(aMapLocation.getLatitude() + "," + aMapLocation.getLongitude());
        aMapLocation.setTime(locationTime * 1000L);
        if (isLan){
            send("AT+SENDB=01,02," + data.length() / 2 + "," + data);
        }
    };

    public AMapLocationListener mLocationListener = aMapLocation -> {
        if (aMapLocation != null) {
            if (aMapLocation.getErrorCode() == 0) {
                ByteBuffer buffer = ByteBuffer.allocate(12);
                buffer.order(ByteOrder.BIG_ENDIAN);
                buffer.putFloat((float) aMapLocation.getLatitude());
                buffer.putFloat((float) aMapLocation.getLongitude());
                buffer.putFloat((float) aMapLocation.getAltitude());
                String data = bytesToHex(buffer.array());
                TextView info = findViewById(R.id.btn_send_hex);
                info.setText("Lat: " + aMapLocation.getLatitude() +
                        " Long: " + aMapLocation.getLongitude() +
                        " Alt: " + aMapLocation.getAltitude());
                if (isLan){
                    send("AT+SENDB=01,02," + data.length() / 2 + "," + data);
                }
            }
        }
    };

    public void initMap() {
        /*
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager != null) {
            // Request location updates (you can specify the provider and update interval)
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                    30000,
                    0,
                    locationListenerGPS
            );

        }
        */
        //
        mLocationClient = new AMapLocationClient(getApplicationContext());
        mLocationClient.setLocationListener(mLocationListener);
        mLocationOption = new AMapLocationClientOption();
        TextView info = findViewById(R.id.btn_send_hex);
        info.setText("No GPS read yet. Waiting...");
        text_statu31.setText("--");
        if (null != mLocationClient) {
            mLocationClient.setLocationOption(mLocationOption);
            mLocationClient.stopLocation();
            mLocationClient.startLocation();
        }
        mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
        mLocationOption.setInterval(locationTime * 1000);
        mLocationOption.setHttpTimeOut(20000);
        mLocationOption.setLocationCacheEnable(false);
        mLocationClient.setLocationOption(mLocationOption);
        mLocationClient.startLocation();
    }

    private class LogAdapter extends BaseAdapter {

        private Boolean seleteHex;
        private Context context;
        private List<LogBean> logList;
        private String mac;

        LogAdapter(Context context) {
            this.context = context;
            logList = new ArrayList<>();
        }

        public void setSeleteHex(Boolean seleteHex) {
            this.seleteHex = seleteHex;
        }

        void addResult(List<LogBean> characteristicList) {
//            for ( int i=0;i<characteristicList.size();i++ ){
//
//            }

            this.logList.clear();
            this.logList.addAll(characteristicList);
            notifyDataSetChanged();
            listView.setSelection(listView.getBottom());

//            this.logList=characteristicList;
        }

        void clear() {
            logList.clear();
        }

        @Override
        public int getCount() {
            return logList.size();
        }

        @Override
        public LogBean getItem(int position) {
            if (position > logList.size())
                return null;
            return logList.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LogAdapter.ViewHolder holder;
            if (convertView != null) {
                holder = (LogAdapter.ViewHolder) convertView.getTag();
            } else {
                convertView = View.inflate(context, R.layout.adapter_log, null);
                holder = new LogAdapter.ViewHolder();
                holder.txt_log = (TextView) convertView.findViewById(R.id.txt_log);
                convertView.setTag(holder);
            }
//            holder.txt_title.setText("数据:");
            if (seleteHex) {
                holder.txt_log.setText(TextUtils.strToASCII(logList.get(position).getText()));
            } else {
                holder.txt_log.setText(logList.get(position).getText());
            }
            if (logList.get(position).getType() == 1) {
                holder.txt_log.setTextColor(context.getResources().getColor(R.color.black));
            } else {
                holder.txt_log.setTextColor(context.getResources().getColor(R.color.qmui_config_color_red));
            }
            return convertView;
        }

        class ViewHolder {
            TextView txt_log;
        }
    }
}
