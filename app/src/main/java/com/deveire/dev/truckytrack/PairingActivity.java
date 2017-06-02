package com.deveire.dev.truckytrack;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class PairingActivity extends Activity
{
    private ListView deviceList;
    private ArrayList<BluetoothDevice> btDevices;
    private BTDeviceListAdapter deviceListAdapter;
    private BluetoothAdapter btAdapter;
    private boolean isScanning;
    private Handler btHandler;

    private static final int SCAN_INTERVAL = 10000;
    private static final int REQUEST_ENABLE_BT = 1;

    private Button scanButton;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_pairing);

        scanButton = (Button) findViewById(R.id.scanButton);
        scanButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                performBTScan();
            }
        });

        deviceList = (ListView) findViewById(R.id.deviceList);
        deviceListAdapter = new BTDeviceListAdapter();
        deviceList.setAdapter(deviceListAdapter);
        deviceList.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id)
            {
                final BluetoothDevice btDeviceFound = deviceListAdapter.getDevice(position);
                if (btDeviceFound == null) {
                    return;
                }
                scanLeDevice(false);

                //TODO: Insert return result
                Intent resultData = new Intent();
                resultData.putExtra("BTMacAddress", btDeviceFound.getAddress());
                Log.i("BTScanner Update", "mac address retrieved from " + btDeviceFound.getName() + " and it is: " + btDeviceFound.getAddress());
                setResult(RESULT_OK, resultData);
                finish();
            /*final Intent intent = new Intent(this, ReaderActivity.class);
            intent.putExtra(ReaderActivity.EXTRAS_DEVICE_NAME, device.getName());
            intent.putExtra(ReaderActivity.EXTRAS_DEVICE_ADDRESS,
                device.getAddress());
            startActivity(intent);*/
            }
        });
        btDevices = new ArrayList<BluetoothDevice>();

        btHandler = new Handler();

        /*
         * Use this check to determine whether BLE is supported on the device.
         * Then you can selectively disable BLE-related features.
         */
        if (!getPackageManager().hasSystemFeature(
                PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE Not Supported", Toast.LENGTH_SHORT)
                    .show();
            finish();
        }

        /*
         * Initializes a Bluetooth adapter. For API level 18 and above, get a
         * reference to BluetoothAdapter through BluetoothManager.
         */
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        btAdapter = bluetoothManager.getAdapter();

        /* Checks if Bluetooth is supported on the device. */
        if (btAdapter == null) {
            Toast.makeText(this, "Bluetooth not supported",
                    Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

       performBTScan();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        /* User chose not to enable Bluetooth. */
        if (requestCode == REQUEST_ENABLE_BT
                && resultCode == Activity.RESULT_CANCELED) {
            finish();
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }





    @Override
    protected void onPause() {
        super.onPause();
        scanLeDevice(false);
        deviceListAdapter.clear();
    }


    private synchronized void scanLeDevice(final boolean enable) {
        if (enable) {
            /* Stops scanning after a pre-defined scan period. */
            btHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isScanning) {
                        isScanning = false;
                        btAdapter.stopLeScan(mLeScanCallback);
                    }
                    invalidateOptionsMenu();
                }
            }, SCAN_INTERVAL);

            isScanning = true;
            btAdapter.startLeScan(mLeScanCallback);
            invalidateOptionsMenu();
        } else if (isScanning) {
            isScanning = false;
            btAdapter.stopLeScan(mLeScanCallback);
            invalidateOptionsMenu();
        }
    }

    private void performBTScan()
    {
         /*
         * Ensures Bluetooth is enabled on the device. If Bluetooth is not
         * currently enabled, fire an intent to display a dialog asking the user
         * to grant permission to enable it.
         */
        if (!btAdapter.isEnabled()) {
            if (!btAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(
                        BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
        }

        /* Initializes list view adapter. */
        deviceListAdapter = new BTDeviceListAdapter();
        deviceList.setAdapter(deviceListAdapter);
        scanLeDevice(true);
    }

    /* Device scan callback. */
    private BluetoothAdapter.LeScanCallback mLeScanCallback = new BluetoothAdapter.LeScanCallback() {

        @Override
        public void onLeScan(final BluetoothDevice device, int rssi,
                             byte[] scanRecord) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    deviceListAdapter.addDevice(device);
                    deviceListAdapter.notifyDataSetChanged();
                }
            });
        }
    };

    /* Adapter for holding devices found through scanning. */
    private class BTDeviceListAdapter extends BaseAdapter {
        private ArrayList<BluetoothDevice> mLeDevices;
        private LayoutInflater mInflator;

        public BTDeviceListAdapter() {
            super();
            mLeDevices = new ArrayList<BluetoothDevice>();
            mInflator = PairingActivity.this.getLayoutInflater();
        }

        public void addDevice(BluetoothDevice device) {
            if (!mLeDevices.contains(device)) {
                mLeDevices.add(device);
            }
        }

        public BluetoothDevice getDevice(int position) {
            return mLeDevices.get(position);
        }

        public void clear() {
            mLeDevices.clear();
        }

        @Override
        public int getCount() {
            return mLeDevices.size();
        }

        @Override
        public Object getItem(int i) {
            return mLeDevices.get(i);
        }

        @Override
        public long getItemId(int i) {
            return i;
        }

        @Override
        public View getView(int i, View view, ViewGroup viewGroup) {
            ViewHolder viewHolder;
            /* General ListView optimization code. */
            if (view == null) {
                view = mInflator.inflate(R.layout.listitem_device, null);
                viewHolder = new ViewHolder();
                viewHolder.deviceAddress = (TextView) view
                        .findViewById(R.id.device_address);
                viewHolder.deviceName = (TextView) view
                        .findViewById(R.id.device_name);
                view.setTag(viewHolder);
            } else {
                viewHolder = (ViewHolder) view.getTag();
            }

            BluetoothDevice device = mLeDevices.get(i);
            final String deviceName = device.getName();
            if (deviceName != null && deviceName.length() > 0)
                viewHolder.deviceName.setText(deviceName);
            else
                viewHolder.deviceName.setText("Unknown Device");
            viewHolder.deviceAddress.setText(device.getAddress());

            return view;
        }
    }


    static class ViewHolder {
        TextView deviceName;
        TextView deviceAddress;
    }
}
