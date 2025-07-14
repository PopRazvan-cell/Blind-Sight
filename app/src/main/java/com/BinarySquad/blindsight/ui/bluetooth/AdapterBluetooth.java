package com.BinarySquad.blindsight.ui.bluetooth;

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


    public AdapterBluetooth(List<BluetoothItem> devices) {
        this.devices = devices;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());

        View v = inflater.inflate(R.layout.bluetooth_device, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.name.setText(devices.get(position).getName());
        holder.address.setText(devices.get(position).getAddress());


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
