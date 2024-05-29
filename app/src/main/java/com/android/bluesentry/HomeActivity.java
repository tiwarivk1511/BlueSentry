package com.android.bluesentry;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
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

import com.google.firebase.auth.FirebaseAuth;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class HomeActivity extends AppCompatActivity {

    public static final int REQUEST_BLUETOOTH_PERMISSIONS = 2;
    private static final int REQUEST_ENABLE_BT = 1;
    private static final String TAG = "HomeActivity";
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothDeviceAdapter bluetoothDeviceAdapter;
    private Handler mHandler;
    private Timer mTimer;
    View turnOn_OffButtonBG; // Declare the variable here

    MediaPlayer mp;
    ChargingReceiver chargingReceiver;
    private BluetoothConnectionReceiver bluetoothConnectionReceiver;
    private BluetoothSocket connectedSocket;

    @SuppressLint("MissingInflatedId")
    @RequiresApi(api = Build.VERSION_CODES.S)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_home);

        // Initialize turnOn_OffButtonBG after setContentView()
        turnOn_OffButtonBG = findViewById(R.id.turnOn_OffButtonBG);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // intigrate the Background service
        startService(new Intent(this, BackgroundService.class));

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

        ImageView profileBtn = findViewById (R.id.profileImageView);
        profileBtn.setOnClickListener (new View.OnClickListener () {
            @Override
            public void onClick (View v) {
                Intent intent = new Intent (HomeActivity.this, ProfileActivity.class);
                startActivity (intent);
            }
        });

        ImageView logoutBtn = findViewById (R.id.logoutButton);
        logoutBtn.setOnClickListener(new View.OnClickListener() {
                                         @Override
                                         public void onClick (View v) {
                                             FirebaseAuth.getInstance ().signOut ();
                                             Intent intent = new Intent (HomeActivity.this, LoginActivity.class);
                                             startActivity (intent);
                                             finish ();
                                         }
                                     });
        // Set the layout based on the device orientation
        if (getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            setContentView(R.layout.activity_home_landscape);
        }
        CardView turnOn_OffButtonCard = findViewById(R.id.turnOn_OffButtonCard);
        turnOn_OffButtonBG = findViewById(R.id.turnOn_OffButtonBG);

        turnOn_OffButtonCard.setOnClickListener(v -> {
            boolean toggle = !bluetoothAdapter.isEnabled();
            toggleBluetooth(toggle);
        });

        RecyclerView availableDevicesRecyclerView = findViewById(R.id.availableDevicesRecyclerView);
        availableDevicesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        bluetoothDeviceAdapter = new BluetoothDeviceAdapter(this);
        availableDevicesRecyclerView.setAdapter(bluetoothDeviceAdapter);

        mTimer = new Timer();
        // Refresh every 5 seconds
        long refreshInterval = 5000;
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                mHandler.post(() -> refreshDeviceList());
            }
        }, 0, refreshInterval);

        // Register charging receiver
        chargingReceiver = new ChargingReceiver(this::updateChargingStatus);
        registerReceiver(chargingReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));

        // Register Bluetooth connection receiver
        bluetoothConnectionReceiver = new BluetoothConnectionReceiver(this);
        IntentFilter filter = new IntentFilter(BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED);
        registerReceiver(bluetoothConnectionReceiver, filter);
    }

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.BLUETOOTH, android.Manifest.permission.BLUETOOTH_ADMIN}, REQUEST_BLUETOOTH_PERMISSIONS);
        }
    }

    private void toggleBluetooth(boolean toggle) {
        if (!toggle) {
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            // Disable Bluetooth and update UI
            bluetoothAdapter.disable();
            turnOn_OffButtonBG.setBackgroundResource(R.drawable.device_bg);
            checkBluetoothStatus();
            turnOffConnectedDevice();
            Toast.makeText(this, "Bluetooth disabled", Toast.LENGTH_SHORT).show();
        } else {
            checkBluetoothStatus();
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

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

    private void refreshDeviceList() {
        bluetoothDeviceAdapter.refreshDevices();
        bluetoothDeviceAdapter.sortDevicesBySignalStrength();
    }

    private void updateBluetoothStatus(boolean isEnabled) {
        TextView bluetoothStatus = findViewById(R.id.statusTextView);
        if (isEnabled) {
            bluetoothStatus.setText("Bluetooth is ON");
        } else {
            bluetoothStatus.setText("Bluetooth is OFF");
            if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            bluetoothAdapter.disable();
        }
    }

    public void checkBluetoothStatus() {
        if (bluetoothAdapter.isEnabled()) {
            updateBluetoothStatus(true);

            turnOn_OffButtonBG.setBackgroundResource(R.drawable.list_bg);
        } else {
            updateBluetoothStatus(false);
            turnOn_OffButtonBG.setBackgroundResource(R.drawable.device_bg);
        }
    }

    private void updateChargingStatus(boolean isCharging) {
        if (isCharging) {
            Toast.makeText(this, "Mobile is charging", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Mobile is not charging", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mTimer.cancel();

        if (chargingReceiver != null) {
            unregisterReceiver(chargingReceiver);
        }

        if (bluetoothConnectionReceiver != null) {
            unregisterReceiver(bluetoothConnectionReceiver);
        }

        if (mp != null) {
            if (mp.isPlaying()) {
                mp.stop();
            }
            mp.release();
            mp = null;
        }
    }

    public void playEmergencyBuzzer() {
        if (mp != null) {
            mp.release();
        }
        mp = MediaPlayer.create(this, R.raw.emergency_buzzer);
        if (mp != null) {
            mp.setLooping(true);
            mp.setVolume(1.0f, 1.0f);
            mp.start();
        } else {
            Toast.makeText(this, "Error: Cannot play buzzer", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BLUETOOTH_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Bluetooth permissions granted", Toast.LENGTH_SHORT).show();
                BluetoothDeviceAdapter adapter = new BluetoothDeviceAdapter(this);
                adapter.refreshDevices();
            } else {
                Toast.makeText(this, "Bluetooth permissions denied", Toast.LENGTH_SHORT).show();
            }
        }
    }


    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    public void connectToDevice(BluetoothDevice device) {
        // Start a new thread to connect to the device
        new Thread(() -> {
            BluetoothSocket socket = null;
            try {
                if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{
                            android.Manifest.permission.BLUETOOTH_CONNECT,
                            android.Manifest.permission.BLUETOOTH_SCAN
                    }, REQUEST_BLUETOOTH_PERMISSIONS);
                    return;
                }
                socket = device.createRfcommSocketToServiceRecord(MY_UUID);
                socket.connect();
                connectedSocket = socket;
                runOnUiThread(() -> Toast.makeText(this, "Connected to device", Toast.LENGTH_SHORT).show());
            } catch (IOException e) {
                Log.e(TAG, "Could not connect to device", e);
                runOnUiThread(() -> Toast.makeText(this, "Failed to connect to device", Toast.LENGTH_SHORT).show());
                try {
                    if (socket != null) {
                        socket.close();
                    }
                } catch (IOException closeException) {
                    Log.e(TAG, "Could not close socket", closeException);
                }
            }
        }).start();
    }

    public void turnOffConnectedDevice() {
        if (connectedSocket != null && connectedSocket.isConnected()) {
            try {
                OutputStream outputStream = connectedSocket.getOutputStream();
                String turnOffCommand = "TURN_OFF"; // Replace with the actual command to turn off the device
                outputStream.write(turnOffCommand.getBytes());
                outputStream.flush();
                Toast.makeText(this, "Turned off connected device", Toast.LENGTH_SHORT).show();
            } catch (IOException e) {
                Log.e(TAG, "Failed to send turn off command", e);
                Toast.makeText(this, "Failed to turn off device", Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No connected device", Toast.LENGTH_SHORT).show();
        }
    }
}
