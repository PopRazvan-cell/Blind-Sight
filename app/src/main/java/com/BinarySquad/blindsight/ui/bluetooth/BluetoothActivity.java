package com.BinarySquad.blindsight.ui.bluetooth;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.BinarySquad.blindsight.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class BluetoothActivity extends AppCompatActivity {
    private static final String TAG = BluetoothActivity.class.getSimpleName();

    private BluetoothAdapter bluetoothAdapter;
    private static final int REQUEST_ENABLE_BT = 1;
    private List<BluetoothItem> devices;

    public BluetoothActivity() {
        devices = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.blue_layout);

        BluetoothManager bluetoothManager = getSystemService(BluetoothManager.class);
        assert bluetoothManager != null;
        bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.e(TAG, "Device doesn't support bluetooth!");
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent btIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            startActivityForResult(btIntent, REQUEST_ENABLE_BT);
        } else {
            initDevices();
        }

        Toast.makeText(this,
                String.format("Found %d devices", devices.size()),
                Toast.LENGTH_LONG).show();

        RecyclerView rcv = findViewById(R.id.rcv_devices);
        AdapterBluetooth adapterBluetooth = new AdapterBluetooth(devices);
        rcv.setAdapter(adapterBluetooth);
        rcv.setLayoutManager(new LinearLayoutManager(this));


    }

    protected void initDevices() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        if (pairedDevices.size() > 0) {
            for (BluetoothDevice device : pairedDevices) {
                String deviceName = device.getName();
                String deviceHardwareAddress = device.getAddress();

                Log.i(TAG, String.format("Device name: %s; Device Address: %s",
                        deviceName, deviceHardwareAddress));

                devices.add(new BluetoothItem(deviceName, deviceHardwareAddress));
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        try {
            super.onActivityResult(requestCode, resultCode, data);

            if (requestCode == REQUEST_ENABLE_BT  && resultCode  == RESULT_OK) {
                initDevices();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            Toast.makeText(this, ex.toString(),
                    Toast.LENGTH_SHORT).show();
        }

    }
}