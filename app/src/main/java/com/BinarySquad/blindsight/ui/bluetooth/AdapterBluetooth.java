package com.BinarySquad.blindsight.ui.bluetooth;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.BinarySquad.blindsight.R;

import java.util.List;

public class AdapterBluetooth extends RecyclerView.Adapter<AdapterBluetooth.ViewHolder> {

    private List<BluetoothItem> devices;
    private Context context; // Added Context field
    private OnDeviceClickListener deviceClickListener; // Add callback interface

    // Interface for device click callback
    public interface OnDeviceClickListener {
        void onDeviceClick(BluetoothItem device);
    }
    public AdapterBluetooth(List<BluetoothItem> devices, OnDeviceClickListener listener) {
        this.devices = devices;
        this.deviceClickListener = listener;
    }
    // Updated constructor to include Context
    public AdapterBluetooth(List<BluetoothItem> devices) {
        this.deviceClickListener = null;
        this.devices = devices;
    }


    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        this.context = parent.getContext();
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View v = inflater.inflate(R.layout.bluetooth_device, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BluetoothItem device = devices.get(position);
        holder.name.setText(device.getName());
        holder.address.setText(device.getAddress());

        // Add click listener to start ChatActivity
        holder.background.setOnClickListener(v -> {
            if (deviceClickListener != null) {
                // If we have a callback (for ultrasonic sensor connection), use it
                deviceClickListener.onDeviceClick(device);
            } else {
                // Otherwise, use the original ChatActivity behavior
                Intent intent = new Intent(context, ChatActivity.class);
                intent.putExtra("DEVICE_NAME", device.getName());
                intent.putExtra("DEVICE_ADDRESS", device.getAddress());
                context.startActivity(intent);
            }
        });
    }

    @Override
    public int getItemCount() {
        return devices.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView name, address;
        ConstraintLayout background;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            this.name = itemView.findViewById(R.id.txt_bt_name);
            this.address = itemView.findViewById(R.id.txt_bt_address);
            this.background = itemView.findViewById(R.id.bkg_bluetooth);
        }
    }
}