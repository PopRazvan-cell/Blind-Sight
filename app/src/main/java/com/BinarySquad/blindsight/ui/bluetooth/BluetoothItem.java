package com.BinarySquad.blindsight.ui.bluetooth;

public class BluetoothItem {

    String name;
    String address;

    public BluetoothItem(String name, String address) {
        this.name = name;
        this.address = address;
    }

    public String getName() {
        return name;
    }

    public String getAddress() {
        return address;
    }

}