package com.example.chargeguard;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;
    private MaterialButton btnToggle, btnSetupPin;
    private TextView tvStatus;
    private SwitchMaterial switchAutoArm, switchPocketMode, switchBatteryAlert;
    private SharedPreferences prefs;
    private boolean isServiceRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("ChargeGuardPrefs", MODE_PRIVATE);

        btnToggle = findViewById(R.id.btnToggle);
        tvStatus = findViewById(R.id.tvStatus);
        btnSetupPin = findViewById(R.id.btnSetupPin);
        switchAutoArm = findViewById(R.id.switchAutoArm);
        switchPocketMode = findViewById(R.id.switchPocketMode);
        switchBatteryAlert = findViewById(R.id.switchBatteryAlert);

        loadSettings();
        updateUIState();

        btnToggle.setOnClickListener(v -> {
            if (isServiceRunning) {
                handleStopRequest();
            } else {
                if (checkPermissions()) {
                    startMonitoring();
                } else {
                    requestPermissions();
                }
            }
        });

        btnSetupPin.setOnClickListener(v -> showPinDialog(false));

        switchAutoArm.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("auto_arm", isChecked).apply());
        switchPocketMode.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("pocket_mode", isChecked).apply());
        switchBatteryAlert.setOnCheckedChangeListener((v, isChecked) -> prefs.edit().putBoolean("battery_alert", isChecked).apply());
    }

    private void loadSettings() {
        switchAutoArm.setChecked(prefs.getBoolean("auto_arm", false));
        switchPocketMode.setChecked(prefs.getBoolean("pocket_mode", false));
        switchBatteryAlert.setChecked(prefs.getBoolean("battery_alert", false));
    }

    private void handleStopRequest() {
        String savedPin = prefs.getString("pin", "");
        if (savedPin.isEmpty()) {
            stopMonitoring();
        } else {
            showPinDialog(true);
        }
    }

    private void showPinDialog(boolean isConfirmOnly) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_pin, null);
        builder.setView(view);

        TextView tvTitle = view.findViewById(R.id.tvPinTitle);
        TextInputEditText etPin = view.findViewById(R.id.etPin);
        MaterialButton btnSave = view.findViewById(R.id.btnSavePin);

        if (isConfirmOnly) {
            tvTitle.setText(R.string.pin_required_to_stop);
            btnSave.setText("Confirm");
        }

        AlertDialog dialog = builder.create();
        btnSave.setOnClickListener(v -> {
            String inputPin = etPin.getText().toString();
            if (isConfirmOnly) {
                if (inputPin.equals(prefs.getString("pin", ""))) {
                    stopMonitoring();
                    dialog.dismiss();
                } else {
                    Toast.makeText(this, R.string.pin_incorrect, Toast.LENGTH_SHORT).show();
                }
            } else {
                if (inputPin.length() == 4) {
                    prefs.edit().putString("pin", inputPin).apply();
                    Toast.makeText(this, "PIN Saved", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                } else {
                    Toast.makeText(this, "Enter a 4-digit PIN", Toast.LENGTH_SHORT).show();
                }
            }
        });
        dialog.show();
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
    }

    private void stopMonitoring() {
        Intent serviceIntent = new Intent(this, ChargeMonitoringService.class);
        stopService(serviceIntent);
        isServiceRunning = false;
        updateUIState();
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
            btnToggle.setBackgroundColor(ContextCompat.getColor(this, R.color.primary));
            tvStatus.setText(R.string.status_off);
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.text_main));
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
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startMonitoring();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUIState();
    }
}
