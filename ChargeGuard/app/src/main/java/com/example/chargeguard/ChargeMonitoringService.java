package com.example.chargeguard;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;

public class ChargeMonitoringService extends Service implements SensorEventListener {
    public static final String CHANNEL_ID = "ChargeGuardChannel";
    public static final int NOTIFICATION_ID = 1;
    public static final String ACTION_STOP_ALARM = "com.example.chargeguard.ACTION_STOP_ALARM";

    private Ringtone alarmRingtone;
    private AudioManager audioManager;
    private SensorManager sensorManager;
    private Sensor proximitySensor;
    private boolean isAlarmRinging = false;
    private SharedPreferences prefs;

    private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_POWER_DISCONNECTED.equals(action)) {
                startAlarm(getString(R.string.alarm_notification_title), getString(R.string.alarm_notification_text));
            } else if (Intent.ACTION_POWER_CONNECTED.equals(action)) {
                stopAlarm();
            } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
                checkBatteryStatus(intent);
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        proximitySensor = sensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        prefs = getSharedPreferences("ChargeGuardPrefs", MODE_PRIVATE);

        createNotificationChannel();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_POWER_DISCONNECTED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        registerReceiver(powerReceiver, filter);

        if (prefs.getBoolean("pocket_mode", false) && proximitySensor != null) {
            sensorManager.registerListener(this, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL);
        }

        setupRingtone();
    }

    private void setupRingtone() {
        Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmUri == null) {
            alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        }
        alarmRingtone = RingtoneManager.getRingtone(this, alarmUri);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            alarmRingtone.setAudioAttributes(audioAttributes);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_ALARM.equals(intent.getAction())) {
            stopAlarm();
        }
        Notification notification = createNotification(getString(R.string.notification_title), getString(R.string.notification_text));
        startForeground(NOTIFICATION_ID, notification);
        return START_STICKY;
    }

    private void checkBatteryStatus(Intent intent) {
        if (!prefs.getBoolean("battery_alert", false)) return;

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isFull = status == BatteryManager.BATTERY_STATUS_FULL;
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level * 100 / (float)scale;

        if (isFull || batteryPct >= 100) {
            startAlarm(getString(R.string.battery_full_title), getString(R.string.battery_full_text));
        }
    }

    private void startAlarm(String title, String text) {
        if (!isAlarmRinging) {
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM,
                    audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0);

            if (alarmRingtone != null) {
                alarmRingtone.play();
                isAlarmRinging = true;
                updateNotification(title, text);
            }
        }
    }

    public void stopAlarm() {
        if (isAlarmRinging) {
            if (alarmRingtone != null && alarmRingtone.isPlaying()) {
                alarmRingtone.stop();
            }
            isAlarmRinging = false;
            updateNotification(getString(R.string.notification_title), getString(R.string.notification_text));
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
            if (event.values[0] >= proximitySensor.getMaximumRange()) {
                // Device is away (pulled out of pocket)
                startAlarm("Pocket Alert!", "Device removed from pocket!");
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_HIGH
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    private Notification createNotification(String title, String text) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(this,
                0, notificationIntent, android.app.PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(text)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    private void updateNotification(String title, String text) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, createNotification(title, text));
        }
    }

    @Override
    public void onDestroy() {
        stopAlarm();
        unregisterReceiver(powerReceiver);
        sensorManager.unregisterListener(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
