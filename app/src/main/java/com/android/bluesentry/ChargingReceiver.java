package com.android.bluesentry;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;

public class ChargingReceiver extends BroadcastReceiver {

    private final ChargingStatusListener listener;
    private boolean isCharging = false;

    public ChargingReceiver(ChargingStatusListener listener) {
        this.listener = listener;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean currentChargingStatus = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;

        // If the charging status has changed
        if (currentChargingStatus != isCharging) {
            isCharging = currentChargingStatus;
            listener.onChargingStatusChanged(isCharging);
        }
    }

    public interface ChargingStatusListener {
        void onChargingStatusChanged(boolean isCharging);
    }
}
