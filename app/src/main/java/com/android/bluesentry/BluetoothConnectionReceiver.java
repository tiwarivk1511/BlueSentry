package com.android.bluesentry;

import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BluetoothConnectionReceiver extends BroadcastReceiver {

    private final Context context;

    public BluetoothConnectionReceiver(Context context) {
        this.context = context;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (BluetoothAdapter.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_CONNECTION_STATE, BluetoothAdapter.ERROR);
            if (state == BluetoothAdapter.STATE_DISCONNECTED) {
                ((HomeActivity) context).playEmergencyBuzzer();
            }
        }
    }
}
