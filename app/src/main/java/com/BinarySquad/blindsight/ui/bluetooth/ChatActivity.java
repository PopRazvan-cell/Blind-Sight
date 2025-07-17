package com.BinarySquad.blindsight.ui.bluetooth;


import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.BinarySquad.blindsight.R;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;


public class ChatActivity extends AppCompatActivity {
    private static final String TAG = ChatActivity.class.getSimpleName();

    private String connectedDevice;

    static final int STATE_LISTENING = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    static final int STATE_CONNECTION_FAILED = 4;
    static final int STATE_MESSAGE_RECEIVED = 5;

    private SendReceive mSendReceive;

    int REQUEST_ENABLE_BLUETOOTH = 1;

    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    Button btnSend;
    EditText txtMessage;
    RecyclerView vMessages;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        try {

            vMessages.setLayoutManager(new LinearLayoutManager(this));

            BluetoothAdapter btAdapter = BluetoothAdapter.getDefaultAdapter();

            BluetoothDevice device = btAdapter.getRemoteDevice(getIntent()
                    .getStringExtra("address"));

            ClientClass client = new ClientClass(device);
            client.start();

            connectedDevice = device.getAddress();


        } catch (Exception err) {
            Toast.makeText(this, err.getMessage(), Toast.LENGTH_LONG).show();
        }
    }


    Handler mHandler = new Handler(msg -> {

        switch (msg.what) {
            case STATE_LISTENING:
                Toast.makeText(this, "Listening...", Toast.LENGTH_LONG).show();
                break;

            case STATE_CONNECTING:
                Toast.makeText(this, "Connectiong...", Toast.LENGTH_LONG).show();
                break;

            case STATE_CONNECTED:
                Toast.makeText(this, "Connected!", Toast.LENGTH_LONG).show();
                break;

            case STATE_CONNECTION_FAILED:
                Toast.makeText(this, "Connection failed!", Toast.LENGTH_LONG).show();
                break;

            case STATE_MESSAGE_RECEIVED:

                byte[] readBuffer = (byte[]) msg.obj;
                String tempMsg = new String(readBuffer, 0, msg.arg1);
                if (tempMsg.length() == 0) break;
                Log.d("tempMsg", "Ready to go");
        }
        return true;
    });

    public class ClientClass extends Thread {

        private BluetoothDevice mBluetoothDevice;
        private BluetoothSocket mBluetoothSocket;
        
        private Context mContext;

        public ClientClass(Context context, BluetoothDevice bluetoothDevice, Handler handler) {
            mContext = context; // Store the Context
            mBluetoothDevice = bluetoothDevice;
            mHandler = handler;
            try {
                if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // Permission not granted, handle accordingly
                    return;
                }
                mBluetoothSocket = mBluetoothDevice.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public ClientClass(BluetoothDevice device) {
        }

        @Override
        public void run() {
            try {
                if (ActivityCompat.checkSelfPermission(mContext, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    // Permission not granted, handle accordingly
                    return;
                }
                mBluetoothSocket.connect();
                Message message = Message.obtain();
                message.what = STATE_CONNECTED;
                mHandler.sendMessage(message);

                mSendReceive = new SendReceive(mBluetoothSocket);
                mSendReceive.start();
            } catch (IOException e) {
                e.printStackTrace();
                Message message = Message.obtain();
                message.what = STATE_CONNECTION_FAILED;
                mHandler.sendMessage(message);
            }
        }
    }

    public class SendReceive extends Thread {
        private final BluetoothSocket mBluetoothSocket;
        private final InputStream mInputStream;

        public SendReceive(BluetoothSocket bluetoothSocket) {

            mBluetoothSocket = bluetoothSocket;
            InputStream tempIn = null;

            try {
                tempIn = mBluetoothSocket.getInputStream();

            } catch (IOException e) {
                e.printStackTrace();
            }

            mInputStream = tempIn;


        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes;

            while (true) {
                try {
                    bytes = mInputStream.read(buffer);
                    mHandler.obtainMessage(STATE_MESSAGE_RECEIVED, bytes, -1, buffer).sendToTarget();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

    }
}