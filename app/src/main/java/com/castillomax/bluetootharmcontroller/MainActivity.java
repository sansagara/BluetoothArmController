package com.castillomax.bluetootharmcontroller;

    import android.support.v7.app.ActionBarActivity;
    import android.os.Bundle;
    import android.view.Menu;
    import android.view.MenuItem;

    import java.io.IOException;
    import java.io.OutputStream;
    import java.lang.reflect.Method;
    import java.util.UUID;

    import android.bluetooth.BluetoothAdapter;
    import android.bluetooth.BluetoothDevice;
    import android.bluetooth.BluetoothSocket;
    import android.content.Intent;
    import android.os.Build;
    import android.util.Log;
    import android.view.View;
    import android.view.View.OnClickListener;
    import android.widget.Button;
    import android.widget.Toast;

    import com.thalmic.myo.AbstractDeviceListener;
    import com.thalmic.myo.DeviceListener;
    import com.thalmic.myo.Hub;
    import com.thalmic.myo.Myo;
    import com.thalmic.myo.Pose;
    import com.thalmic.myo.scanner.ScanActivity;

public class MainActivity extends ActionBarActivity {

    private static final String TAG = "bluetooth1";
    Button btnOn, btnOff;
    private BluetoothAdapter btAdapter = null;
    private BluetoothSocket btSocket = null;
    private OutputStream outStream = null;

    // SPP UUID service
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    // MAC-address of Bluetooth module
    private static String address = "98:D3:31:40:23:B1";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Para el MYO
        Hub hub = Hub.getInstance();
        if (!hub.init(this)) {
            Log.e(TAG, "Could not initialize the Hub.");
            finish();
            return;
        }
        Hub.getInstance().setLockingPolicy(Hub.LockingPolicy.NONE);
        Hub.getInstance().addListener(mListener);

        //Para los Botones
        btnOn = (Button) findViewById(R.id.openFist);
        btnOff = (Button) findViewById(R.id.closeFist);

        btAdapter = BluetoothAdapter.getDefaultAdapter();
        checkBTState();

        btnOn.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sendData(10);
                Toast.makeText(getBaseContext(), "Opening Fist...", Toast.LENGTH_SHORT).show();
            }
        });

        btnOff.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                sendData(11);
                Toast.makeText(getBaseContext(), "Closing Fist...", Toast.LENGTH_SHORT).show();
            }
        });

    }

    private BluetoothSocket createBluetoothSocket(BluetoothDevice device) throws IOException {
        if(Build.VERSION.SDK_INT >= 10){
            try {
                final Method  m = device.getClass().getMethod("createInsecureRfcommSocketToServiceRecord", new Class[] { UUID.class });
                return (BluetoothSocket) m.invoke(device, MY_UUID);
            } catch (Exception e) {
                Log.e(TAG, "Could not create Insecure RFComm Connection",e);
            }
        }
        return  device.createRfcommSocketToServiceRecord(MY_UUID);
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.d(TAG, "...onResume - try connect...");

        // Set up a pointer to the remote node using it's address.
        BluetoothDevice device = btAdapter.getRemoteDevice(address);

        // Two things are needed to make a connection:
        //   A MAC address, which we got above.
        //   A Service ID or UUID.  In this case we are using the
        //     UUID for SPP.

        try {
            btSocket = createBluetoothSocket(device);
        } catch (IOException e1) {
            errorExit("Fatal Error", "In onResume() and socket create failed: " + e1.getMessage() + ".");
        }

        // Discovery is resource intensive.  Make sure it isn't going on
        // when you attempt to connect and pass your message.
        btAdapter.cancelDiscovery();

        // Establish the connection.  This will block until it connects.
        Log.d(TAG, "...Connecting...");
        try {
            btSocket.connect();
            Log.d(TAG, "...Connection ok...");
        } catch (IOException e) {
            try {
                btSocket.close();
            } catch (IOException e2) {
                errorExit("Fatal Error", "In onResume() and unable to close socket during connection failure" + e2.getMessage() + ".");
            }
        }

        // Create a data stream so we can talk to server.
        Log.d(TAG, "...Create Socket...");

        try {
            outStream = btSocket.getOutputStream();
        } catch (IOException e) {
            errorExit("Fatal Error", "In onResume() and output stream creation failed:" + e.getMessage() + ".");
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        Log.d(TAG, "...In onPause()...");

        try     {
            btSocket.close();
        } catch (IOException e2) {
            errorExit("Fatal Error", "In onPause() and failed to close socket." + e2.getMessage() + ".");
        }

        Hub.getInstance().removeListener(mListener);
    }

    private void checkBTState() {
        // Check for Bluetooth support and then check to make sure it is turned on
        // Emulator doesn't support Bluetooth and will return null
        if(btAdapter==null) {
            errorExit("Fatal Error", "Bluetooth not support");
        } else {
            if (btAdapter.isEnabled()) {
                Log.d(TAG, "...Bluetooth ON...");
            } else {
                //Prompt user to turn on Bluetooth
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, 1);
            }
        }
    }

    private void errorExit(String title, String message){
        Toast.makeText(getBaseContext(), title + " - " + message, Toast.LENGTH_LONG).show();
        finish();
    }

    private void sendData(Integer message) {
        //byte[] msgBuffer = message();

        Log.d(TAG, "...Send data: " + message + "...");

        try {
            outStream.write(message);
        } catch (IOException e) {
            String msg = "In onResume() and an exception occurred during write: " + e.getMessage();
            if (address.equals("00:00:00:00:00:00"))
                msg = msg + ".\n\nUpdate your server address from 00:00:00:00:00:00 to the correct address on line 35 in the java code";
            msg = msg +  ".\n\nCheck that the SPP UUID: " + MY_UUID.toString() + " exists on server.\n\n";

            errorExit("Fatal Error", msg);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.myo_connect) {
            Intent intent = new Intent(this, ScanActivity.class);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }


    private DeviceListener mListener = new AbstractDeviceListener() {
        @Override
        public void onConnect(Myo myo, long timestamp) {
            Toast.makeText(getBaseContext(), "Myo Connected!", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onDisconnect(Myo myo, long timestamp) {
            Toast.makeText(getBaseContext(), "Myo Disconnected!", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onPose(Myo myo, long timestamp, Pose pose) {
            Toast.makeText(getBaseContext(), "Pose: " + pose, Toast.LENGTH_SHORT).show();
            switch (pose) {
                case UNKNOWN:
                    Toast.makeText(getBaseContext(), R.string.hello_world, Toast.LENGTH_SHORT).show();
                    break;
                case REST:
                case DOUBLE_TAP:
                    switch (myo.getArm()) {
                        case LEFT:
                            Toast.makeText(getBaseContext(), R.string.arm_left, Toast.LENGTH_SHORT).show();
                            break;
                        case RIGHT:
                            Toast.makeText(getBaseContext(), R.string.arm_right, Toast.LENGTH_SHORT).show();
                            break;
                    }
                    break;
                case FIST:
                    Toast.makeText(getBaseContext(), R.string.pose_fist, Toast.LENGTH_SHORT).show();
                    break;
                case WAVE_IN:
                    Toast.makeText(getBaseContext(), R.string.pose_wavein, Toast.LENGTH_SHORT).show();
                    break;
                case WAVE_OUT:
                    Toast.makeText(getBaseContext(), R.string.pose_waveout, Toast.LENGTH_SHORT).show();
                    break;
                case FINGERS_SPREAD:
                    Toast.makeText(getBaseContext(), R.string.pose_fingersspread, Toast.LENGTH_SHORT).show();
                    break;
            }

        }
    };

}

