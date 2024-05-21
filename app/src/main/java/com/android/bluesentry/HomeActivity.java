package com.android.bluesentry;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.Timer;
import java.util.TimerTask;

public class HomeActivity extends AppCompatActivity {

    public static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;
    private static final int REQUEST_ENABLE_BT = 1;
    private BluetoothAdapter bluetoothAdapter;
    private CardView turnOn_OffButtonCard;
    private RecyclerView availableDevicesRecyclerView;
    private BluetoothDeviceAdapter bluetoothDeviceAdapter;
    private Handler mHandler;
    private Timer mTimer;
    MediaPlayer mp;
    ChargingReceiver chargingReceiver;
    private final long refreshInterval = 5000; // Refresh every 5 seconds

    @SuppressLint("MissingInflatedId")
    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        WindowInsetsControllerCompat windowInsetsController = ViewCompat.getWindowInsetsController(findViewById(R.id.main));
        assert windowInsetsController != null;
        windowInsetsController.isAppearanceLightStatusBars();

        mHandler = new Handler();

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Request Bluetooth permissions
        checkAndRequestPermissions();

        // Check Bluetooth status
        checkBluetoothStatus();

        // Set the layout based on the device orientation
        if (getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.activity_home_landscape);
        }

        turnOn_OffButtonCard = findViewById(R.id.turnOn_OffButtonCard);

        turnOn_OffButtonCard.setOnClickListener(v -> {
            toggleBluetooth();
        });

        availableDevicesRecyclerView = findViewById(R.id.availableDevicesRecyclerView);
        availableDevicesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        bluetoothDeviceAdapter = new BluetoothDeviceAdapter(this);
        availableDevicesRecyclerView.setAdapter(bluetoothDeviceAdapter);

        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mHandler.post(() -> refreshDeviceList());
            }
        }, 0, refreshInterval);

        // Register charging receiver
        chargingReceiver = new ChargingReceiver(new ChargingReceiver.ChargingStatusListener() {
            @Override
            public void onChargingStatusChanged(boolean isCharging) {
                updateChargingStatus(isCharging);
            }
        });
        registerReceiver(chargingReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    }

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.BLUETOOTH, android.Manifest.permission.BLUETOOTH_ADMIN}, REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }

    private void toggleBluetooth() {
        if (bluetoothAdapter.isEnabled()) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            // Turn off Bluetooth of Mobile device
            bluetoothAdapter.disable();
            updateBluetoothStatus(false);
            checkBluetoothStatus();

        } else {
            checkBluetoothStatus();
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);

        }
    }

    // Handle the result of Bluetooth enabling
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth turned on", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Bluetooth enabling canceled", Toast.LENGTH_SHORT).show();
            }
        }
    }



    // Refresh the list of available devices
    private void refreshDeviceList() {
        bluetoothDeviceAdapter.refreshDevices();
        bluetoothDeviceAdapter.sortDevicesBySignalStrength();
    }

    // Bluetooth Status
    private void updateBluetoothStatus(boolean isEnabled) {
        TextView bluetoothStatus = findViewById(R.id.statusTextView);
        if (isEnabled) {
            bluetoothStatus.setText("Bluetooth is ON");
        } else {
            bluetoothStatus.setText("Bluetooth is OFF");
        }
    }

    // check the bluetooth status
    public void checkBluetoothStatus() {
        if (bluetoothAdapter.isEnabled()) {
            updateBluetoothStatus(true);
        } else {
            updateBluetoothStatus(false);
        }
    }

    // Method to update UI based on charging status
    private void updateChargingStatus(boolean isCharging) {
        if (isCharging) {
            Toast.makeText(this, "Mobile is charging", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Mobile is not charging", Toast.LENGTH_SHORT).show();
        }
    }



    // Emergency Buzzer play
    @Override
    protected void onDestroy() {
        super.onDestroy();
        mTimer.cancel();

        // Unregister the charging receiver to prevent memory leaks
        if (chargingReceiver != null) {
            unregisterReceiver(chargingReceiver);
        }

        if (mp != null) {
            if (mp.isPlaying()) {
                mp.stop();
            }
            mp.release();
            mp = null;
        }
        // Cancel the timer
        mTimer.cancel();

        // Unregister the charging receiver to prevent memory leaks
        if (chargingReceiver != null) {
            unregisterReceiver(chargingReceiver);
        }
    }

    public void playEmergencyBuzzer() {
        if (mp != null) {
            mp.release();
        }
        mp = MediaPlayer.create(this, R.raw.emergency_buzzer);
        if (mp != null) {
            mp.setLooping(true); // Set looping
            mp.setVolume(1.0f, 1.0f); // Set volume
            mp.start(); // Start playback
        } else {
            Toast.makeText(this, "Error: Cannot play buzzer", Toast.LENGTH_SHORT).show();
        }
    }

    // Bluetooth Permissions
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show();
                // Refresh devices or start discovery again if needed
                BluetoothDeviceAdapter adapter = new BluetoothDeviceAdapter(this);
                adapter.refreshDevices();
            } else {
                Toast.makeText(this, "Bluetooth permissions denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
