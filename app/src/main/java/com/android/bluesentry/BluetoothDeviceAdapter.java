package com.android.bluesentry;

import android.Manifest;
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
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class BluetoothDeviceAdapter extends RecyclerView.Adapter<BluetoothDeviceAdapter.ViewHolder> {

    private static final String TAG = "BluetoothDeviceAdapter";
    private final List<BluetoothDevice> devices;
    private final Context context;
    private final BluetoothAdapter bluetoothAdapter;
    private static final UUID MY_UUID = UUID.fromString ("00001101-0000-1000-8000-00805F9B34FB");

    // Constructor
    public BluetoothDeviceAdapter (Context context) {
        this.context = context;
        devices = new ArrayList<> ();
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter ();
        registerReceivers ();
        refreshDevices ();
    }

    // Register broadcast receivers
    private void registerReceivers () {
        IntentFilter filter = new IntentFilter (BluetoothDevice.ACTION_FOUND);
        context.registerReceiver (receiver, filter);
        filter = new IntentFilter (BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        context.registerReceiver (receiver, filter);
    }

    // Refresh available devices and notify the adapter
    @SuppressLint("NotifyDataSetChanged")
    public void refreshDevices () {
        devices.clear ();
        if (bluetoothAdapter != null) {
            if (bluetoothAdapter.isEnabled ()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    if (ActivityCompat.checkSelfPermission (context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                            ActivityCompat.checkSelfPermission (context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                        ActivityCompat.requestPermissions ((HomeActivity) context, new String[]{
                                Manifest.permission.BLUETOOTH_CONNECT,
                                Manifest.permission.BLUETOOTH_SCAN
                        }, HomeActivity.REQUEST_BLUETOOTH_PERMISSIONS);
                        return;
                    }
                }
                Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices ();
                if (bondedDevices != null) {
                    devices.addAll (bondedDevices);
                }
                bluetoothAdapter.startDiscovery ();
            } else {
                Intent enableBtIntent = new Intent (BluetoothAdapter.ACTION_REQUEST_ENABLE);
                context.startActivity (enableBtIntent);
                showToast ("Bluetooth is disabled");
            }
        }
        notifyDataSetChanged ();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder (@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from (parent.getContext ()).inflate (R.layout.available_device_layout, parent, false);
        return new ViewHolder (view);
    }

    @Override
    public void onBindViewHolder (@NonNull ViewHolder holder, int position) {
        BluetoothDevice device = devices.get (position);
        if (device != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                if (ActivityCompat.checkSelfPermission (context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions ((HomeActivity) context, new String[]{Manifest.permission.BLUETOOTH_CONNECT}, HomeActivity.REQUEST_BLUETOOTH_PERMISSIONS);
                    return;
                }
            }
            String deviceName = device.getName ();
            holder.deviceName.setText (deviceName != null ? deviceName : "Unknown device");
            holder.deviceAddress.setText (device.getAddress ());
            holder.deviceStatus.setText (getBondStateString (device.getBondState ()));

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                holder.itemView.setOnClickListener (v -> connectToDevice (device));
            }
        }
    }

    @Override
    public int getItemCount () {
        return devices.size ();
    }

    //Get bond state string
    private String getBondStateString (int bondState) {
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

    // Sort devices by signal strength
    public void sortDevicesBySignalStrength () {
        devices.sort ((o1, o2) -> {
            if (ActivityCompat.checkSelfPermission (context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return 1;
            }
            return o2.getBluetoothClass ().getDeviceClass () - o1.getBluetoothClass ().getDeviceClass ();
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

    // Connect to device
    @RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private void connectToDevice(BluetoothDevice device) {
        // Connect to device
        new Thread(() -> {
            BluetoothSocket socket = null;
            try {
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                        ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions((HomeActivity) context, new String[]{
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                    }, HomeActivity.REQUEST_BLUETOOTH_PERMISSIONS);
                    return;
                }
                socket = device.createRfcommSocketToServiceRecord(MY_UUID);
                bluetoothAdapter.cancelDiscovery();
                socket.connect();
                showToast("Connected to " + device.getName());
                monitorConnection(socket);
            } catch (IOException e) {
                Log.e(TAG, "Could not connect to device: " + e.getMessage());
                showToast("Could not connect to " + device.getName());
                closeSocket(socket);
            }
        }).start();
    }

    // Monitor connection
    private void monitorConnection(BluetoothSocket socket) {
        // Check if socket is still connected
        new Thread(() -> {
            try {
                while (socket.isConnected()) {
                    Thread.sleep(1000);
                }
                onDeviceDisconnected();
            } catch (InterruptedException e) {
                Log.e(TAG, "Connection monitoring error: " + e.getMessage());
                onDeviceDisconnected();
            }
        }).start();
    }

    // On device disconnected
    private void onDeviceDisconnected() {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(() -> ((HomeActivity) context).playEmergencyBuzzer());
    }

    // Close socket
    private void closeSocket(BluetoothSocket socket) {
        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close socket: " + e.getMessage());
            }
        }
    }

    // Show toast
    private void showToast(String message) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    // Broadcast receiver
    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @SuppressLint("NotifyDataSetChanged")
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                // Filter out bonded devices
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
