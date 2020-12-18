package in.avimarine.rctimer;

public interface BluetoothListener {
    void onBTConnected(String mac);
    void onBTDisconnected(String mac);
}
