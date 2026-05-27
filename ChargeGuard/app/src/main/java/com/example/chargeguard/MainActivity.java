package com.example.chargeguard;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private MaterialButton btnToggle;
    private TextView tvStatus;
    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnToggle = findViewById(R.id.btnToggle);
        tvStatus = findViewById(R.id.tvStatus);

        updateUIState();

        btnToggle.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isServiceRunning) {
                    stopMonitoring();
                } else {
                    if (checkPermissions()) {
                        startMonitoring();
                    } else {
                        requestPermissions();
                    }
                }
            }
        });
    }

    private void startMonitoring() {
        Intent serviceIntent = new Intent(this, ChargeMonitoringService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        isServiceRunning = true;
        updateUIState();
        Toast.makeText(this, "Monitoring Started", Toast.LENGTH_SHORT).show();
    }

    private void stopMonitoring() {
        Intent serviceIntent = new Intent(this, ChargeMonitoringService.class);
        stopService(serviceIntent);
        isServiceRunning = false;
        updateUIState();
        Toast.makeText(this, "Monitoring Stopped", Toast.LENGTH_SHORT).show();
    }

    private void updateUIState() {
        isServiceRunning = isServiceRunning(ChargeMonitoringService.class);
        if (isServiceRunning) {
            btnToggle.setText(R.string.turn_off);
            btnToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.red));
            tvStatus.setText(R.string.status_on);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.green));
        } else {
            btnToggle.setText(R.string.turn_on);
            btnToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_500));
            tvStatus.setText(R.string.status_off);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.black));
        }
    }

    private boolean isServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        }
        return true;
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startMonitoring();
            } else {
                Toast.makeText(this, "Notification permission is required for the service to run", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUIState();
    }
}
