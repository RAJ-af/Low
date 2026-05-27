package com.example.chargeguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.os.Build;
import android.util.Log;

public class PowerConnectionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences("ChargeGuardPrefs", Context.MODE_PRIVATE);
        if (!prefs.getBoolean("auto_arm", false)) return;

        String action = intent.getAction();
        boolean shouldStart = false;

        if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
            shouldStart = true;
        } else if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            // Check if already charging after reboot
            IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
            Intent batteryStatus = context.registerReceiver(null, ifilter);
            int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            shouldStart = (status == BatteryManager.BATTERY_STATUS_CHARGING ||
                          status == BatteryManager.BATTERY_STATUS_FULL);
        }

        if (shouldStart) {
            Intent serviceIntent = new Intent(context, ChargeMonitoringService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
