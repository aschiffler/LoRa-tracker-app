package com.sz.cp2102;

import android.app.Application;


import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.tencent.bugly.crashreport.CrashReport;

public class MyApplication extends Application {
    public static  UsbSerialPort port;
    @Override
    public void onCreate() {
        super.onCreate();
        CrashReport.initCrashReport(getApplicationContext(), "865b700306", false);
    }

}
