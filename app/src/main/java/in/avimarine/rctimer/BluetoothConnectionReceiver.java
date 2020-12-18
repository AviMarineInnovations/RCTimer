package in.avimarine.rctimer;

import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BluetoothConnectionReceiver extends BroadcastReceiver {
    BluetoothListener listener;
    public BluetoothConnectionReceiver(BluetoothListener btl){
        listener = btl;
    }

    @Override
    public void onReceive(Context context, Intent intent){
        String action = intent.getAction();
        BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)){
            listener.onBTConnected(device.getAddress());
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)){
            listener.onBTDisconnected(device.getAddress());
        }
    }
}