package com.example.chargeguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

public class PowerConnectionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = context.getSharedPreferences("ChargeGuardPrefs", Context.MODE_PRIVATE);
        if (Intent.ACTION_POWER_CONNECTED.equals(intent.getAction()) && prefs.getBoolean("auto_arm", false)) {
            Intent serviceIntent = new Intent(context, ChargeMonitoringService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        }
    }
}
