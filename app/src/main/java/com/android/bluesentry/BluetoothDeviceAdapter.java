package com.android.bluesentry;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BluetoothDeviceAdapter extends RecyclerView.Adapter<BluetoothDeviceAdapter.ViewHolder> {

    private static final String TAG = "BluetoothDeviceAdapter";
    private List<BluetoothDevice> devices;
    private Context context;
    private BluetoothAdapter bluetoothAdapter;
    private static final UUID MY_UUID = UUID.randomUUID(); // Replace with your app's UUID
    private BluetoothSocket bluetoothSocket;

    public BluetoothDeviceAdapter(Context context) {
        this.context = context;
        devices = new ArrayList<>();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        refreshDevices();

        // Register for broadcasts when a device is discovered
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        context.registerReceiver(receiver, filter);

        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver(receiver, filter);
    }

    @SuppressLint("NotifyDataSetChanged")
    public void refreshDevices() {
        devices.clear();
        if (bluetoothAdapter != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions((HomeActivity) context, new String[]{
                            android.Manifest.permission.BLUETOOTH_CONNECT,
                            android.Manifest.permission.BLUETOOTH_SCAN
                    }, HomeActivity.REQUEST_BLUETOOTH_PERMISSIONS);
                    return;
                }
            }
            Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
            if (bondedDevices != null) {
                devices.addAll(bondedDevices);
            }
            bluetoothAdapter.startDiscovery();
        }
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.available_device_layout, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BluetoothDevice device = devices.get(position);
        if (device != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions((HomeActivity) context, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, HomeActivity.REQUEST_BLUETOOTH_PERMISSIONS);
                    return;
                }
            }
            String deviceName = device.getName();
            holder.deviceName.setText(deviceName != null ? deviceName : "Unknown device");
            holder.deviceAddress.setText(device.getAddress());
            holder.deviceStatus.setText(getBondStateString(device.getBondState()));

            holder.itemView.setOnClickListener(v -> connectToDevice(device));
        }
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    private String getBondStateString(int bondState) {
        switch (bondState) {
            case BluetoothDevice.BOND_BONDED:
                return "Connected";
            case BluetoothDevice.BOND_BONDING:
                return "Pairing...";
            case BluetoothDevice.BOND_NONE:
                return "Available";
            default:
                return "Unknown";
        }
    }

    public void sortDevicesBySignalStrength() {
        Collections.sort(devices, (o1, o2) -> {
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return 1;
            }
            return o2.getBluetoothClass().getDeviceClass() - o1.getBluetoothClass().getDeviceClass();
        });
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
        TextView deviceStatus;

        public ViewHolder(View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(R.id.deviceName);
            deviceAddress = itemView.findViewById(R.id.deviceID);
            deviceStatus = itemView.findViewById(R.id.connectionStatus);
        }
    }

    private void connectToDevice(BluetoothDevice device) {
        new Thread(() -> {
            BluetoothSocket socket = null;
            try {
                if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions((HomeActivity) context, new String[]{
                            android.Manifest.permission.BLUETOOTH_CONNECT,
                            android.Manifest.permission.BLUETOOTH_SCAN
                    }, HomeActivity.REQUEST_BLUETOOTH_PERMISSIONS);
                    return;
                }
                socket = device.createRfcommSocketToServiceRecord(MY_UUID);
                bluetoothAdapter.cancelDiscovery();
                socket.connect();
                bluetoothSocket = socket;
                showToast("Connected to " + device.getName());
                monitorConnection(socket);
            } catch (IOException e) {
                Log.e(TAG, "Could not connect to device: " + e.getMessage());
                showToast("Could not connect to " + device.getName());
                closeSocket(socket);
            }
        }).start();
    }

    private void monitorConnection(BluetoothSocket socket) {
        new Thread(() -> {
            try {
                while (socket.isConnected()) {
                    // Keep the thread alive while the socket is connected
                    Thread.sleep(1000);
                }
                // If the socket gets disconnected
                onDeviceDisconnected();
            } catch (InterruptedException e) {
                Log.e(TAG, "Connection monitoring error: " + e.getMessage());
                onDeviceDisconnected();
            }
        }).start();
    }

    private void onDeviceDisconnected() {
        // Notify the main thread to play the emergency buzzer
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> ((HomeActivity) context).playEmergencyBuzzer());
    }

    private void closeSocket(BluetoothSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close socket: " + e.getMessage());
            }
        }
    }

    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    // The BroadcastReceiver that listens for discovered devices and updates the list
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                if (device != null && device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    devices.add(device);
                    notifyDataSetChanged();
                }
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                showToast("Discovery finished");
            }
        }
    };
}
