package in.avimarine.rctimer;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Objects;

public class TimerFragment extends Fragment implements ServiceConnection, SerialListener, BluetoothListener {

    private static final String TAG = "TimerFragment";

    @Override
    public void onBTConnected(String mac) {
        if (deviceAddress.equals(mac)) {
            if (status!=null) {
                status.setImageResource(android.R.drawable.presence_online);
            }
        }
    }

    @Override
    public void onBTDisconnected(String mac) {
        if (deviceAddress.equals(mac)) {
            if (status!=null) {
                status.setImageResource(android.R.drawable.presence_offline);
            }
        }
    }

    private enum Connected { False, Pending, True }

    private String deviceAddress;
    private SerialService service;
    private ImageView status;

    private Connected connected = Connected.False;
    private boolean initialStart = true;
    private boolean hexEnabled = false;
    private String newline = TextUtil.newline_crlf;
    private static final String start = "A0 01 01 A2";
    private static final String stop = "A0 01 00 A1";

    private BluetoothConnectionReceiver btcr;

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = getArguments().getString("device");
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECT_REQUESTED);
        filter.addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED);
        btcr = new BluetoothConnectionReceiver(this);
        getActivity().registerReceiver(btcr, filter);

    }

    @Override
    public void onDestroy() {
        if (connected != Connected.False)
            disconnect();
        getActivity().stopService(new Intent(getActivity(), SerialService.class));
        getActivity().unregisterReceiver(btcr);
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
//        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            getActivity().startForegroundService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
//        } else{
            getActivity().startService(new Intent(getActivity(), SerialService.class));
//        }
    }

    @Override
    public void onStop() {
        if(service != null && !getActivity().isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        getActivity().bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { getActivity().unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service != null) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        service.attach(this);
        if(initialStart && isResumed()) {
            initialStart = false;
            getActivity().runOnUiThread(this::connect);
        }


    }

    @Override
    public void onServiceDisconnected(ComponentName name) {

        service = null;


    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_timer, container, false);
        View Start = view.findViewById(R.id.button);
        View Stop = view.findViewById(R.id.button2);
        View startShort = view.findViewById(R.id.button3);
        View startLong = view.findViewById(R.id.button4);
        status = view.findViewById(R.id.imageView);
        if (connected == Connected.True){
            status.setImageResource(android.R.drawable.presence_online);
        } else {
            status.setImageResource(android.R.drawable.presence_offline);
        }
        status.setOnClickListener(v -> connect());
        Start.setOnClickListener(v -> send(start));
        Stop.setOnClickListener(v -> send(stop));

        startShort.setOnClickListener(v -> startSoundDuration(500));
        startLong.setOnClickListener(v -> startSoundDuration(1500));
        return view;
    }

    private void startSoundDuration(int i) {
        send(start);
        Handler handler = new Handler();
        handler.postDelayed(() -> send(stop), i);
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_terminal, menu);
        menu.findItem(R.id.hex).setChecked(hexEnabled);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.clear) {
            return true;
        } else if (id == R.id.devices) {
            Fragment fragment = new DevicesFragment();
            getFragmentManager().beginTransaction().replace(R.id.fragment, fragment, "devices").addToBackStack(null).commit();
            return true;
        } else if (id == R.id.hex) {
            hexEnabled = !hexEnabled;
            item.setChecked(hexEnabled);
            return true;
        } else {
            return super.onOptionsItemSelected(item);
        }
    }

    /*
     * Serial + UI
     */
    private void connect() {
        try {
            BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            status("connecting...");
            connected = Connected.Pending;
            SerialSocket socket = new SerialSocket(Objects.requireNonNull(getActivity()).getApplicationContext(), device);
            service.connect(socket);
        } catch (Exception e) {
            onSerialConnectError(e);
        }
    }

    private void disconnect() {
        Log.d(TAG, "Disconnected");
        connected = Connected.False;
        service.disconnect();
        if (status!=null){
            status.setImageResource(android.R.drawable.presence_offline);
        }
    }

    private void send(String str) {
        if(connected != Connected.True) {
            Toast.makeText(getActivity(), "not connected", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String msg;
            byte[] data;
            StringBuilder sb = new StringBuilder();
            TextUtil.toHexString(sb, TextUtil.fromHexString(str));
            TextUtil.toHexString(sb, newline.getBytes());
            msg = sb.toString();
            data = TextUtil.fromHexString(msg);
            service.write(data);
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    private void status(String str) {
        SpannableStringBuilder spn = new SpannableStringBuilder(str+'\n');
        spn.setSpan(new ForegroundColorSpan(getResources().getColor(R.color.colorStatusText)), 0, spn.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /*
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        status("connected");
        connected = Connected.True;
        if (status!=null){
            status.setImageResource(android.R.drawable.presence_online);
        }
    }

    @Override
    public void onSerialConnectError(Exception e) {
        status("connection failed: " + e.getMessage());
        disconnect();
    }

    @Override
    public void onSerialRead(byte[] data) {
//        receive(data);
    }

    @Override
    public void onSerialIoError(Exception e) {
        status("connection lost: " + e.getMessage());
        disconnect();
    }

}
