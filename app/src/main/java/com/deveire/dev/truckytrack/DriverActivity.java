package com.deveire.dev.truckytrack;

import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Message;
import android.os.ResultReceiver;
import android.provider.Settings;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;


import com.acs.bluetooth.*;

import com.deveire.dev.truckytrack.bleNfc.*;
import com.deveire.dev.truckytrack.bleNfc.card.*;


import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStates;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.GoogleMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;

public class DriverActivity extends FragmentActivity implements GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks, LocationListener, DownloadCallback<String>
{

    private GoogleMap mMap;

    private TextView mapText;
    private EditText nameEditText;
    private EditText kegIDEditText;
    private Button scanKegButton;
    private Button pairReaderButton;

    final static int PAIR_READER_REQUESTCODE = 9;

    private SharedPreferences savedData;
    private String itemName;
    private int itemID;

    private boolean hasState;

    //[BLE Variables]
    private String storedScannerAddress;
    private final static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"; //UUID for changing notify or not
    private int REQUEST_ENABLE_BT;
    private BluetoothManager btManager;
    private BluetoothAdapter btAdapter;
    private BluetoothAdapter.LeScanCallback leScanCallback;

    private BluetoothDevice btDevice;

    private BluetoothGatt btGatt;
    private BluetoothReaderGattCallback btLeGattCallback;
    //[/BLE Variables]

    //[Retreive Keg Data Variables]
    private Boolean pingingServerFor_KegData;
    private TextView kegDataText;
    //[/Retreive Keg Data Variables]


    //[Tile Reader Variables]
    private DeviceManager deviceManager;
    private Scanner mScanner;

    private ProgressDialog dialog = null;

    private BluetoothDevice mNearestBle = null;
    private int lastRssi = -100;

    private int readCardFailCnt = 0;
    private int disconnectCnt = 0;
    private int readCardCnt = 0;
    private String startTimeString;
    private static volatile Boolean getMsgFlag = false;

    //[Tile Reader Variables]

    /*[Bar Reader Variables]
    private String barReaderInput;
    private Boolean barReaderInputInProgress;
    private Timer barReaderTimer;



    //[/Bar Reader Variables] */

    //[Scanner Variables]

    /* Default master key. */
    /*private static final String DEFAULT_1255_MASTER_KEY = "ACR1255U-J1 Auth";

    private static final byte[] AUTO_POLLING_START = { (byte) 0xE0, 0x00, 0x00, 0x40, 0x01 };
    private static final byte[] AUTO_POLLING_STOP = { (byte) 0xE0, 0x00, 0x00, 0x40, 0x00 };
    private static final byte[] GET_UID_APDU_COMMAND = {(byte)0xFF , (byte)0xCA, (byte)0x00, (byte)0x00, (byte)0x00};

    private int scannerConnectionState = BluetoothReader.STATE_DISCONNECTED;
    private BluetoothReaderManager scannerManager;
    private BluetoothReader scannerReader;

    private Timer scannerTimer;

    private static final int MAX_AUTHENTICATION_ATTEMPTS_BEFORE_TIMEOUT = 20;
    private boolean scannerIsAuthenticated;*/

    //[/Scanner Variables]

    //[Network and periodic location update, Variables]
    private GoogleApiClient mGoogleApiClient;
    private Location locationReceivedFromLocationUpdates;
    private Location userLocation;
    private DriverActivity.AddressResultReceiver geoCoderServiceResultReciever;
    private int locationScanInterval;

    LocationRequest request;
    private final int SETTINGS_REQUEST_ID = 8888;
    private final String SAVED_LOCATION_KEY = "79";

    private boolean pingingServer;
    private final String serverIPAddress = "http://192.168.1.188:8080/TruckyTrackServlet/TTServlet";
    //private final String serverIPAddress = "http://api.eirpin.com/api/TTServlet";
    private String serverURL;
    private NetworkFragment aNetworkFragment;
    //[/Network and periodic location update, Variables]


    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_driver);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.



        mapText = (TextView) findViewById(R.id.mapText);
        nameEditText = (EditText) findViewById(R.id.nameEditText);
        kegIDEditText = (EditText) findViewById(R.id.kegIDEditText);
        scanKegButton = (Button) findViewById(R.id.scanKegButton);


        scanKegButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                //scanKeg();
                //Log.i("Scanner Connection", "current card status = " + currentCardStatus);
                //transmitApdu();
            }
        });

        pairReaderButton = (Button) findViewById(R.id.pairReaderButton);
        pairReaderButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                storedScannerAddress = null;
                Intent pairReaderIntent = new Intent(getApplicationContext(), PairingActivity.class);
                startActivityForResult(pairReaderIntent, PAIR_READER_REQUESTCODE);
                /*if(btAdapter != null)
                {
                    btAdapter.startLeScan(leScanCallback);
                }*/
            }
        });

        hasState = true;

        userLocation = new Location("Truck");
        userLocation.setLatitude(0);
        userLocation.setLongitude(0);

        savedData = this.getApplicationContext().getSharedPreferences("TruckyTrack SavedData", Context.MODE_PRIVATE);
        itemName = savedData.getString("itemName", "Unknown");
        itemID = savedData.getInt("itemID", 0);
        nameEditText.setText(itemName);


        pingingServer = false;

        //aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), "https://192.168.1.188:8080/smrttrackerserver-1.0.0-SNAPSHOT/hello?isDoomed=yes");
        serverURL = serverIPAddress + "?request=storelocation" + Settings.Secure.ANDROID_ID.toString() + "&name=" + itemName + "&lat=" + 0000 + "&lon=" + 0000;


        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mGoogleApiClient.connect();

        locationScanInterval = 60;//in seconds


        request = new LocationRequest();
        request.setInterval(locationScanInterval * 1000);//in mileseconds
        request.setFastestInterval(5000);//caps how fast the locations are recieved, as other apps could be triggering updates faster than our app.
        request.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY); //accurate to 100 meters.

        LocationSettingsRequest.Builder requestBuilder = new LocationSettingsRequest.Builder().addLocationRequest(request);

        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient,
                        requestBuilder.build());

        result.setResultCallback(new ResultCallback<LocationSettingsResult>()
        {
            @Override
            public void onResult(@NonNull LocationSettingsResult aResult)
            {
                final Status status = aResult.getStatus();
                final LocationSettingsStates states = aResult.getLocationSettingsStates();
                switch (status.getStatusCode())
                {
                    case LocationSettingsStatusCodes.SUCCESS:
                        // All location settings are satisfied. The client can
                        // initialize location requests here.

                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied, but this can be fixed
                        // by showing the user a dialog.
                        try
                        {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(DriverActivity.this, SETTINGS_REQUEST_ID);
                        } catch (IntentSender.SendIntentException e)
                        {
                            // Ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way
                        // to fix the settings so we won't show the dialog.
                        break;
                }
            }
        });

        geoCoderServiceResultReciever = new DriverActivity.AddressResultReceiver(new Handler());


        pingingServerFor_KegData = false;
        kegDataText = (TextView) findViewById(R.id.kegDataText);

        setupTileScanner();
        //setupBluetoothScanner();
        /*
        barReaderTimer = new Timer();
        barReaderInput = "";
        barReaderInputInProgress = false;
        kegIDEditText.requestFocus();*/

        restoreSavedValues(savedInstanceState);

    }

    @Override
    protected void onResume()
    {
        super.onResume();
        hasState = true;

        final IntentFilter intentFilter = new IntentFilter();

        //barReaderTimer = new Timer();

        /*
        /* Start to monitor bond state change /
        intentFilter.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
        registerReceiver(mBroadcastReceiver, intentFilter);

        setupBluetoothScanner();
        */
    }

    @Override
    protected void onPause()
    {
        hasState = false;
        if(aNetworkFragment != null)
        {
            aNetworkFragment.cancelDownload();
        }

        /*
        barReaderTimer.cancel();
        barReaderTimer.purge();
        */

        //[Scanner onPause]
        /*
        /* Stop to monitor bond state change /
        unregisterReceiver(mBroadcastReceiver);

        scannerIsAuthenticated = false;

        /* Disconnect Bluetooth reader /
        disconnectReader();

        scannerTimer.cancel();
        scannerTimer.purge();
        */
        //[/Scanner On pause]

        super.onPause();
        //finish();
    }

    @Override
    protected void onStop()
    {
        hasState = false;

        SharedPreferences.Editor edit = savedData.edit();
        edit.putString("itemName", nameEditText.getText().toString());
        edit.putInt("itemID", itemID);

        //edit.putString("ScannerMacAddress", storedScannerAddress);


        edit.commit();

        /*
        if(btGatt != null)
        {
            btGatt.disconnect();
            btGatt.close();
        }
        */

        super.onStop();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        // Check which request we're responding to
        Log.i("PairingResult", "Call received to onActivity Result with reqyestCode: " + requestCode);
        if (requestCode == PAIR_READER_REQUESTCODE) {
            Log.i("PairingResult", "Received Pairing requestCode");
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                Log.i("PairingResult", "Recieved Result Ok");
                storedScannerAddress = data.getStringExtra("BTMacAddress");
                SharedPreferences.Editor edit = savedData.edit();
                edit.putString("ScannerMacAddress", storedScannerAddress);
                edit.commit();
                Log.i("Pairing Result", "Recieved scannerMacAddress of : " + storedScannerAddress);


            }
        }
    }

    private void scanKeg()
    {
        String kegUUID = "ERROR";
        if(!kegIDEditText.getText().toString().matches(""))
        {
            kegUUID = kegIDEditText.getText().toString();

            serverURL = serverIPAddress + "?request=storekeg" + "&id=" + itemID + "&kegid=" + kegUUID + "&lat=" + locationReceivedFromLocationUpdates.getLatitude() + "&lon=" + locationReceivedFromLocationUpdates.getLongitude();
            //lat and long are doubles, will cause issue? nope
            Log.i("Network Update", "Attempting to start download from scanKeg. " + serverURL);
            aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);
        }
        else
        {
            Log.e("kegScan Error", "invalid uuid entered.");
        }
    }

    private void scanKeg(String kegIDin)
    {
        if(!kegIDin.matches(""))
        {
            kegIDin = kegIDin.replace(' ', '_');
            serverURL = serverIPAddress + "?request=storekeg" + "&id=" + itemID + "&kegid=" + kegIDin + "&lat=" + locationReceivedFromLocationUpdates.getLatitude() + "&lon=" + locationReceivedFromLocationUpdates.getLongitude();
            //lat and long are doubles, will cause issue? nope
            Log.i("Network Update", "Attempting to start download from scanKeg. " + serverURL);
            aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);
        }
        else
        {
            Log.e("kegScan Error", "invalid uuid entered.");
        }
    }

    /*public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        Log.i("BarReader   ", "OnKeyUp Triggered");

        switch (keyCode)
        {
            case KeyEvent.KEYCODE_0: barReaderInput += "0"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_1: barReaderInput += "1"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_2: barReaderInput += "2"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_3: barReaderInput += "3"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_4: barReaderInput += "4"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_5: barReaderInput += "5"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_6: barReaderInput += "6"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_7: barReaderInput += "7"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_8: barReaderInput += "8"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_9: barReaderInput += "9"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_Q: barReaderInput += "Q"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_W: barReaderInput += "W"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_E: barReaderInput += "E"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_R: barReaderInput += "R"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_T: barReaderInput += "T"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_Y: barReaderInput += "Y"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_U: barReaderInput += "U"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_I: barReaderInput += "I"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_O: barReaderInput += "O"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_P: barReaderInput += "P"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_A: barReaderInput += "A"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_S: barReaderInput += "S"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_D: barReaderInput += "D"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_F: barReaderInput += "F"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_G: barReaderInput += "G"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_H: barReaderInput += "H"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_J: barReaderInput += "J"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_K: barReaderInput += "K"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_L: barReaderInput += "L"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_Z: barReaderInput += "Z"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_X: barReaderInput += "X"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_C: barReaderInput += "C"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_V: barReaderInput += "V"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_B: barReaderInput += "B"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_N: barReaderInput += "N"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_M: barReaderInput += "M"; barScannerSheduleUpload(); Log.i("BarReader   ", "Current Input equals: " + barReaderInput); break;
            case KeyEvent.KEYCODE_BACK: Log.i("BarReader   ", "Current Input equals Back"); finish(); break;
            default: Log.i("BarReader   ", "Unidentified symbol: " + keyCode); break;
        }

        return true;
    }*/

    /*private void barScannerSheduleUpload()
    {
        int delay = 1500;
        if(!barReaderInputInProgress)
        {
            barReaderInputInProgress = true;
            barReaderTimer.schedule(new TimerTask()
            {
                @Override
                public void run()
                {
                    scanKeg(barReaderInput);
                    Log.i("BarReader   ", "Final Input equals: " + barReaderInput);
                    barReaderInputInProgress = false;
                    final String barReaderInputToRead = barReaderInput;
                    barReaderInput = "";
                    barReaderTimer.schedule(new TimerTask()
                    {
                        @Override
                        public void run()
                        {
                            Log.i("BarReader  ", "Launching Data Request");
                            retrieveKegData(barReaderInputToRead);
                        }
                    }, 2000);
                }
            }, delay);
        }
    }*/

    private void retrieveKegData(String kegIDin)
    {
        if(!kegIDin.matches(""))
        {
            kegIDin = kegIDin.replace(' ', '_');
            serverURL = serverIPAddress + "?request=getkegdata" + "&kegid=" + kegIDin;
            //lat and long are doubles, will cause issue? nope
            Log.i("Network Update", "Attempting to start download from retrieveKegData " + serverURL);
            pingingServerFor_KegData = true;
            aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);
        }
        else
        {
            Log.e("kegData Error", "invalid uuid entered. " + kegIDin);
        }
    }


//+++[TileScanner Code]
    private void setupTileScanner()
    {
        dialog = new ProgressDialog(DriverActivity.this);
        //Set processing bar style(round,revolving)
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        //Set a Button for ProgressDialog
        dialog.setButton("Cancel", new ProgressDialog.OnClickListener(){
            @Override
            public void onClick(DialogInterface dialog, int which) {
                deviceManager.requestDisConnectDevice();
            }
        });
        //set if the processing bar of ProgressDialog is indeterminate
        dialog.setIndeterminate(false);

        //Initial device operation classes
        mScanner = new Scanner(DriverActivity.this, scannerCallback);
        deviceManager = new DeviceManager(DriverActivity.this);
        deviceManager.setCallBack(deviceManagerCallback);
        connectToTileScanner();
    }

    //Scanner CallBack
    private ScannerCallback scannerCallback = new ScannerCallback() {
        @Override
        public void onReceiveScanDevice(BluetoothDevice device, int rssi, byte[] scanRecord) {
            super.onReceiveScanDevice(device, rssi, scanRecord);
            System.out.println("Activity found a device：" + device.getName() + "Signal strength：" + rssi );
            //Scan bluetooth and record the one has the highest signal strength
            if ( (device.getName() != null) && (device.getName().contains("UNISMES") || device.getName().contains("BLE_NFC")) ) {
                if (mNearestBle != null) {
                    if (rssi > lastRssi) {
                        mNearestBle = device;
                    }
                }
                else {
                    mNearestBle = device;
                    lastRssi = rssi;
                }
            }
        }

        @Override
        public void onScanDeviceStopped() {
            super.onScanDeviceStopped();
        }
    };

    //Callback function for device manager
    private DeviceManagerCallback deviceManagerCallback = new DeviceManagerCallback()
    {
        @Override
        public void onReceiveConnectBtDevice(boolean blnIsConnectSuc) {
            super.onReceiveConnectBtDevice(blnIsConnectSuc);
            if (blnIsConnectSuc) {
                Log.i("TileScanner", "Activity Connection successful");
                Log.i("TileScanner", "Connection successful!\r\n");
                Log.i("TileScanner", "SDK version：" + deviceManager.SDK_VERSIONS + "\r\n");

                // Send order after 500ms delay
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                handler.sendEmptyMessage(3);
            }
        }

        @Override
        public void onReceiveDisConnectDevice(boolean blnIsDisConnectDevice) {
            super.onReceiveDisConnectDevice(blnIsDisConnectDevice);
            Log.i("TileScanner", "Activity Unlink");
            Log.i("TileScanner", "Unlink!");
            handler.sendEmptyMessage(5);
        }

        @Override
        public void onReceiveConnectionStatus(boolean blnIsConnection) {
            super.onReceiveConnectionStatus(blnIsConnection);
            System.out.println("Activity Callback for Connection Status");
        }

        @Override
        public void onReceiveInitCiphy(boolean blnIsInitSuc) {
            super.onReceiveInitCiphy(blnIsInitSuc);
        }

        @Override
        public void onReceiveDeviceAuth(byte[] authData) {
            super.onReceiveDeviceAuth(authData);
        }

        @Override
        public void onReceiveRfnSearchCard(boolean blnIsSus, int cardType, byte[] bytCardSn, byte[] bytCarATS) {
            super.onReceiveRfnSearchCard(blnIsSus, cardType, bytCardSn, bytCarATS);
            if (!blnIsSus) {
                return;
            }
            StringBuffer stringBuffer = new StringBuffer();
            for (int i=0; i<bytCardSn.length; i++) {
                stringBuffer.append(String.format("%02x", bytCardSn[i]));
            }

            StringBuffer stringBuffer1 = new StringBuffer();
            for (int i=0; i<bytCarATS.length; i++) {
                stringBuffer1.append(String.format("%02x", bytCarATS[i]));
            }
            kegDataText.setText(stringBuffer);
            Log.i("TileScanner","Activity Activate card callback received：UID->" + stringBuffer + " ATS->" + stringBuffer1);
        }

        @Override
        public void onReceiveRfmSentApduCmd(byte[] bytApduRtnData) {
            super.onReceiveRfmSentApduCmd(bytApduRtnData);

            StringBuffer stringBuffer = new StringBuffer();
            for (int i=0; i<bytApduRtnData.length; i++) {
                stringBuffer.append(String.format("%02x", bytApduRtnData[i]));
            }
            Log.i("TileScanner", "Activity APDU callback received：" + stringBuffer);
        }

        @Override
        public void onReceiveRfmClose(boolean blnIsCloseSuc) {
            super.onReceiveRfmClose(blnIsCloseSuc);
        }
    };


    private void connectToTileScanner()
    {
        if (deviceManager.isConnection()) {
            deviceManager.requestDisConnectDevice();
            return;
        }
        Log.i("TileScanner", "connect To Update: Searching Devices");
        //handler.sendEmptyMessage(0);
        if (!mScanner.isScanning()) {
            mScanner.startScan(0);
            mNearestBle = null;
            lastRssi = -100;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    int searchCnt = 0;
                    while ((mNearestBle == null) && (searchCnt < 50000) && (mScanner.isScanning())) {
                        searchCnt++;
                        try {
                            Thread.sleep(1);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }

                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    mScanner.stopScan();
                    if (mNearestBle != null && !deviceManager.isConnection()) {
                        mScanner.stopScan();
                        Log.i("TileScanner", "connect To Update: Connecting to Device");
                        handler.sendEmptyMessage(0);
                        deviceManager.requestConnectBleDevice(mNearestBle.getAddress());
                    }
                    else {
                        Log.i("TileScanner", "connect To Update: Cannot Find Devices");
                        handler.sendEmptyMessage(0);
                    }
                }
            }).start();
        }
    }

    //Read card Demo
    private void readCardDemo() {
        readCardCnt++;
        System.out.println("Activity Send scan/activate order");
        deviceManager.requestRfmSearchCard((byte) 0x00, new DeviceManager.onReceiveRfnSearchCardListener() {
            @Override
            public void onReceiveRfnSearchCard(final boolean blnIsSus, int cardType, byte[] bytCardSn, byte[] bytCarATS) {
                deviceManager.mOnReceiveRfnSearchCardListener = null;
                if ( !blnIsSus ) {
                    Log.i("TileScanner", "No card is found！Please put ShenZhen pass on the bluetooth card reading area first");
                    handler.sendEmptyMessage(0);
                    System.out.println("No card is found！");
                    readCardFailCnt++;
                    handler.sendEmptyMessage(4);
                    return;
                }
                if ( cardType == DeviceManager.CARD_TYPE_ISO4443_B ) {   //Find ISO14443-B card（identity card）
                    final Iso14443bCard card = (Iso14443bCard)deviceManager.getCard();
                    if (card != null) {

                        Log.i("TileScanner", "found ISO14443-B card->UID:(Identity card send 0036000008 order to get UID)\r\n");
                        handler.sendEmptyMessage(0);
                        //Order stream to get Identity card DN code
                        final byte[][] sfzCmdBytes = {
                                {0x00, (byte)0xa4, 0x00, 0x00, 0x02, 0x60, 0x02},
                                {0x00, 0x36, 0x00, 0x00, 0x08},
                                {(byte)0x80, (byte)0xB0, 0x00, 0x00, 0x20},
                        };
                        System.out.println("Send order stream");
                        Handler readSfzHandler = new Handler(DriverActivity.this.getMainLooper()) {
                            @Override
                            public void handleMessage(Message msg) {
                                final Handler theHandler = msg.getTarget();
                                if (msg.what < sfzCmdBytes.length) {  // Execute order stream recurrently
                                    final int index = msg.what;
                                    StringBuffer stringBuffer = new StringBuffer();
                                    for (int i=0; i<sfzCmdBytes[index].length; i++) {
                                        stringBuffer.append(String.format("%02x", sfzCmdBytes[index][i]));
                                    }
                                    Log.i("TileScanner", "Send：" + stringBuffer + "\r\n");
                                    handler.sendEmptyMessage(0);
                                    card.bpduExchange(sfzCmdBytes[index], new Iso14443bCard.onReceiveBpduExchangeListener() {
                                        @Override
                                        public void onReceiveBpduExchange(boolean isCmdRunSuc, byte[] bytBpduRtnData) {
                                            if (!isCmdRunSuc) {
                                                card.close(null);
                                                return;
                                            }
                                            StringBuffer stringBuffer = new StringBuffer();
                                            for (int i=0; i<bytBpduRtnData.length; i++) {
                                                stringBuffer.append(String.format("%02x", bytBpduRtnData[i]));
                                            }
                                            Log.i("TileScanner", "Return：" + stringBuffer + "\r\n");
                                            handler.sendEmptyMessage(0);
                                            theHandler.sendEmptyMessage(index + 1);
                                        }
                                    });
                                }
                                else{ //Order stream has been excuted,shut antenna down
                                    card.close(null);
                                    handler.sendEmptyMessage(4);
                                }
                            }
                        };
                        readSfzHandler.sendEmptyMessage(0);  //Start to execute the first order
                    }
                }
                else if (cardType == DeviceManager.CARD_TYPE_ISO4443_A){  //Find ACPU card
                    Log.i("TileScanner", "Card activation status：" + blnIsSus);
                    Log.i("TileScanner", "Send APDU order - Select main file");

                    final CpuCard card = (CpuCard)deviceManager.getCard();
                    if (card != null) {
                        Log.i("TileScanner", "Found CPU card->UID:" + card.uidToString() + "\r\n");
                        handler.sendEmptyMessage(0);
                        card.apduExchange(SZTCard.getSelectMainFileCmdByte(), new CpuCard.onReceiveApduExchangeListener() {
                            @Override
                            public void onReceiveApduExchange(boolean isCmdRunSuc, byte[] bytApduRtnData) {
                                if (!isCmdRunSuc) {
                                    Log.i("TileScanner", "Main file selection failed");
                                    card.close(null);
                                    readCardFailCnt++;
                                    handler.sendEmptyMessage(4);
                                    return;
                                }
                                Log.i("TileScanner", "Send APDU order- read balance");
                                card.apduExchange(SZTCard.getBalanceCmdByte(), new CpuCard.onReceiveApduExchangeListener() {
                                    @Override
                                    public void onReceiveApduExchange(boolean isCmdRunSuc, byte[] bytApduRtnData) {
                                        if (SZTCard.getBalance(bytApduRtnData) == null) {
                                            Log.i("TileScanner", "This is not ShenZhen Pass！");
                                            handler.sendEmptyMessage(0);
                                            Log.i("TileScanner", "This is not ShenZhen Pass！");
                                            card.close(null);
                                            readCardFailCnt++;
                                            handler.sendEmptyMessage(4);
                                            return;
                                        }
                                        Log.i("TileScanner", "ShenZhen Pass balance：" + SZTCard.getBalance(bytApduRtnData));
                                        handler.sendEmptyMessage(0);
                                        System.out.println("Balance：" + SZTCard.getBalance(bytApduRtnData));
                                        System.out.println("Send APDU order -read 10 trading records");
                                        Handler readSztHandler = new Handler(DriverActivity.this.getMainLooper()) {
                                            @Override
                                            public void handleMessage(Message msg) {
                                                final Handler theHandler = msg.getTarget();
                                                if (msg.what <= 10) {  //Read 10 trading records recurrently
                                                    final int index = msg.what;
                                                    card.apduExchange(SZTCard.getTradeCmdByte((byte) msg.what), new CpuCard.onReceiveApduExchangeListener() {
                                                        @Override
                                                        public void onReceiveApduExchange(boolean isCmdRunSuc, byte[] bytApduRtnData) {
                                                            if (!isCmdRunSuc) {
                                                                card.close(null);
                                                                readCardFailCnt++;
                                                                handler.sendEmptyMessage(4);
                                                                return;
                                                            }
                                                            Log.i("TileScanner", "\r\n" + SZTCard.getTrade(bytApduRtnData));
                                                            handler.sendEmptyMessage(0);
                                                            theHandler.sendEmptyMessage(index + 1);
                                                        }
                                                    });
                                                }
                                                else if (msg.what == 11){ //Shut antenna down
                                                    card.close(null);
                                                    handler.sendEmptyMessage(4);
                                                }
                                            }
                                        };
                                        readSztHandler.sendEmptyMessage(1);
                                    }
                                });
                            }
                        });
                    }
                    else {
                        readCardFailCnt++;
                        handler.sendEmptyMessage(4);
                    }
                }
                else if (cardType == DeviceManager.CARD_TYPE_FELICA) { //find Felica card
                    FeliCa card = (FeliCa) deviceManager.getCard();
                    if (card != null) {
                        Log.i("TileScanner", "Read data block 0000 who serves 008b：\r\n");
                        handler.sendEmptyMessage(0);
                        byte[] pServiceList = {(byte) 0x8b, 0x00};
                        byte[] pBlockList = {0x00, 0x00, 0x00};
                        card.read((byte) 1, pServiceList, (byte) 1, pBlockList, new FeliCa.onReceiveReadListener() {
                            @Override
                            public void onReceiveRead(boolean isSuc, byte pRxNumBlocks, byte[] pBlockData) {
                                if (isSuc) {
                                    StringBuffer stringBuffer = new StringBuffer();
                                    for (int i = 0; i < pBlockData.length; i++) {
                                        stringBuffer.append(String.format("%02x", pBlockData[i]));
                                    }
                                    Log.i("TileScanner", stringBuffer + "\r\n");
                                    handler.sendEmptyMessage(0);
                                }
                                else {
                                    Log.i("TileScanner", "\r\n READing FeliCa FAILED");
                                    handler.sendEmptyMessage(0);
                                }
                            }
                        });

//                        card.write((byte) 1, pServiceList, (byte) 1, pBlockList, new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x18, 0x19, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55}, new FeliCa.onReceiveWriteListener() {
//                            @Override
//                            public void onReceiveWrite(boolean isSuc, byte[] returnBytes) {
//                                msgBuffer.append("" + isSuc + returnBytes);
//                                handler.sendEmptyMessage(0);
//                            }
//                        });
                    }
                }
                else if (cardType == DeviceManager.CARD_TYPE_ULTRALIGHT) { //find Ultralight卡
                    final Ntag21x card  = (Ntag21x) deviceManager.getCard();
                    if (card != null) {
                        Log.i("TileScanner", "find Ultralight card ->UID:" + card.uidToString() + "\r\n");
                        Log.i("TileScanner", "Read tag NDEFText\r\n");
                        handler.sendEmptyMessage(0);

                        card.NdefTextRead(new Ntag21x.onReceiveNdefTextReadListener() {
                            @Override
                            public void onReceiveNdefTextRead(String eer, String returnString) {
                                if (returnString != null) {
                                    Log.i("TileScanner", "read NDEFText successfully：\r\n" + returnString);
                                }
                                if (eer != null) {
                                    Log.i("TileScanner", "reading NDEFText failed：" + eer);
                                }
                                handler.sendEmptyMessage(0);
                                card.close(null);
                            }
                        });
                    }
                }
                else if (cardType == DeviceManager.CARD_TYPE_MIFARE) {
                    final Mifare card = (Mifare)deviceManager.getCard();
                    if (card != null) {
                        Log.i("TileScanner", "Found Mifare card->UID:" + card.uidToString() + "\r\n");
                        Log.i("TileScanner", "Start to verify the first password block\r\n");
                        handler.sendEmptyMessage(0);
                        byte[] key = {(byte) 0xff, (byte) 0xff,(byte) 0xff,(byte) 0xff,(byte) 0xff,(byte) 0xff,};
                        card.authenticate((byte) 1, Mifare.MIFARE_KEY_TYPE_A, key, new Mifare.onReceiveAuthenticateListener() {
                            @Override
                            public void onReceiveAuthenticate(boolean isSuc) {
                                if (!isSuc) {
                                    Log.i("TileScanner", "Verifying password failed\r\n");
                                    handler.sendEmptyMessage(0);
                                }
                                else {
                                    Log.i("TileScanner", "Verify password successfully\r\n");

                                    Log.i("TileScanner", "Charge e-Wallet block 1 1000 Chinese yuan\r\n");
                                    handler.sendEmptyMessage(0);
                                    card.decrementTransfer((byte) 1, (byte) 1, card.getValueBytes(1000), new Mifare.onReceiveDecrementTransferListener() {
                                        @Override
                                        public void onReceiveDecrementTransfer(boolean isSuc) {
                                            if (!isSuc) {
                                                Log.i("TileScanner", "e-Walle is not initialized!\r\n");
                                                handler.sendEmptyMessage(0);
                                                card.close(null);
                                            }
                                            else {
                                                Log.i("TileScanner", "Charge successfully！\r\n");
                                                handler.sendEmptyMessage(0);
                                                card.readValue((byte) 1, new Mifare.onReceiveReadValueListener() {
                                                    @Override
                                                    public void onReceiveReadValue(boolean isSuc, byte address, byte[] valueBytes) {
                                                        if (!isSuc || (valueBytes == null) || (valueBytes.length != 4)) {
                                                            Log.i("TileScanner", "Reading e-Wallet balance failed！\r\n");
                                                            handler.sendEmptyMessage(0);
                                                            card.close(null);
                                                        }
                                                        else {
                                                            int value = card.getValue(valueBytes);
                                                            Log.i("TileScanner", "e-Wallet balance is：" + (value & 0x0ffffffffl) + "\r\n");
                                                            handler.sendEmptyMessage(0);
                                                            card.close(null);
                                                        }
                                                    }
                                                });
                                            }
                                        }
                                    });

//                                    //Increase value
//                                    card.incrementTransfer((byte) 1, (byte) 1, card.getValueBytes(1000), new Mifare.onReceiveIncrementTransferListener() {
//                                        @Override
//                                        public void onReceiveIncrementTransfer(boolean isSuc) {
//                                            if (!isSuc) {
//                                                msgBuffer.append("e-Walle is not initialized!\r\n");
//                                                handler.sendEmptyMessage(0);
//                                                card.close(null);
//                                            }
//                                            else {
//                                                msgBuffer.append("Charge successfully！\r\n");
//                                                handler.sendEmptyMessage(0);
//                                                card.readValue((byte) 1, new Mifare.onReceiveReadValueListener() {
//                                                    @Override
//                                                    public void onReceiveReadValue(boolean isSuc, byte address, byte[] valueBytes) {
//                                                        if (!isSuc || (valueBytes == null) || (valueBytes.length != 4)) {
//                                                            msgBuffer.append("Reading e-Wallet balance failed！\r\n");
//                                                            handler.sendEmptyMessage(0);
//                                                            card.close(null);
//                                                        }
//                                                        else {
//                                                            int value = card.getValue(valueBytes);
//                                                            msgBuffer.append("e-Wallet balance is：" + (value & 0x0ffffffffl) + "\r\n");
//                                                            handler.sendEmptyMessage(0);
//                                                            card.close(null);
//                                                        }
//                                                    }
//                                                });
//                                            }
//                                        }
//                                    });

//                                    //Test read and write block
//                                    msgBuffer.append("write 00112233445566778899001122334455 to block 1\r\n");
//                                    handler.sendEmptyMessage(0);
//                                    card.write((byte) 1, new byte[]{0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99, 0x00, 0x11, 0x22, 0x33, 0x44, 0x55}, new Mifare.onReceiveWriteListener() {
//                                        @Override
//                                        public void onReceiveWrite(boolean isSuc) {
//                                            if (isSuc) {
//                                                msgBuffer.append("Write successfully！\r\n");
//                                                msgBuffer.append("read data from block 1\r\n");
//                                                handler.sendEmptyMessage(0);
//                                                card.read((byte) 1, new Mifare.onReceiveReadListener() {
//                                                    @Override
//                                                    public void onReceiveRead(boolean isSuc, byte[] returnBytes) {
//                                                        if (!isSuc) {
//                                                            msgBuffer.append("reading data from block 1 failed！\r\n");
//                                                            handler.sendEmptyMessage(0);
//                                                        }
//                                                        else {
//                                                            StringBuffer stringBuffer = new StringBuffer();
//                                                            for (int i=0; i<returnBytes.length; i++) {
//                                                                stringBuffer.append(String.format("%02x", returnBytes[i]));
//                                                            }
//                                                            msgBuffer.append("Block 1 data:\r\n" + stringBuffer);
//                                                            handler.sendEmptyMessage(0);
//                                                        }
//                                                        card.close(null);
//                                                    }
//                                                });
//                                            }
//                                            else {
//                                                msgBuffer.append("Write fails！\r\n");
//                                                handler.sendEmptyMessage(0);
//                                            }
//                                        }
//                                    });
                                }
                            }
                        });
                    }
                }
            }
        });
    }

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            getMsgFlag = true;
            SimpleDateFormat formatter = new SimpleDateFormat ("yyyy MM dd HH:mm:ss ");
            Date curDate = new Date(System.currentTimeMillis());//Get current time
            String str = formatter.format(curDate);

            if (deviceManager.isConnection()) {
                Log.i("TileScanner", "Disconnect");
            }
            else {
                Log.i("TileScanner", "Search device");
            }

            if (msg.what == 1) {
                dialog.show();
            }
            else if (msg.what == 2) {
                dialog.dismiss();
            }
            else if (msg.what == 3) {
                handler.sendEmptyMessage(4);
//                deviceManager.requestVersionsDevice(new DeviceManager.onReceiveVersionsDeviceListener() {
//                    @Override
//                    public void onReceiveVersionsDevice(byte versions) {
//                        msgBuffer.append("Device version:" + String.format("%02x", versions) + "\r\n");
//                        handler.sendEmptyMessage(0);
//                        deviceManager.requestBatteryVoltageDevice(new DeviceManager.onReceiveBatteryVoltageDeviceListener() {
//                            @Override
//                            public void onReceiveBatteryVoltageDevice(double voltage) {
//                                msgBuffer.append("Device battery voltage:" + String.format("%.2f", voltage) + "\r\n");
//                                if (voltage < 3.4) {
//                                    msgBuffer.append("Device has low battery, please charge！");
//                                }
//                                else {
//                                    msgBuffer.append("Device has enough battery！");
//                                }
//                                handler.sendEmptyMessage(4);
//                            }
//                        });
//                    }
//                });
            }
            else if (msg.what == 4) {
                if (deviceManager.isConnection()) {
                    getMsgFlag = false;
                    readCardDemo();
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            if (getMsgFlag == false) {
                                handler.sendEmptyMessage(4);
                            }
                        }
                    }).start();
                }
            }
            else if (msg.what == 5) {
                disconnectCnt++;
                //searchButton.performClick();
                connectToTileScanner();
            }
        }
    };



//+++[/TileScanner Code]


//++++++++++[Bluetooth BLE Code]
    /*
    private void setupBluetoothScanner()
    {
        scannerTimer = new Timer();

        REQUEST_ENABLE_BT = 1;

        btManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);

        btAdapter = btManager.getAdapter();
        if(btAdapter != null && !btAdapter.isEnabled())
        {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
        }


        storedScannerAddress = savedData.getString("ScannerMacAddress", "None");
        Log.i("Pairing Update", "Retrieved from storage, scannerMacAddress of : " + storedScannerAddress);
        if(btAdapter != null)
        {
            if(storedScannerAddress.matches("None"))
            {
                //btAdapter.startLeScan(leScanCallback);
            }
            else
            {
                btAdapter.getRemoteDevice(storedScannerAddress);
            }
        }

        btLeGattCallback = new BluetoothReaderGattCallback();

        btLeGattCallback.setOnConnectionStateChangeListener(new BluetoothReaderGattCallback.OnConnectionStateChangeListener()
        {
            @Override
            public void onConnectionStateChange(final BluetoothGatt inGatt, final int state, final int newState)
            {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run()
                    {
                        if (state != BluetoothGatt.GATT_SUCCESS)
                        {
                                    /*
                                     * Show the message on fail to
                                     * connect/disconnect.
                                     /
                            scannerConnectionState = BluetoothReader.STATE_DISCONNECTED;

                            if (newState == BluetoothReader.STATE_CONNECTED) {
                                Log.e("ScannerConnection", "Bt Reader Failled to connect");

                            } else if (newState == BluetoothReader.STATE_DISCONNECTED) {
                                Log.e("ScannerConnection", "Bt Reader Failed To Disconnect");
                                mapText.setText("Connection Error \n Try waking your \n device up or \n try re-pairing it");

                            }


                            return;
                        }

                        scannerConnectionState = newState;
                        Log.i("ScannerConnection", "Bt Reader ConnectedChanged to : " + scannerConnectionState);


                        if (newState == BluetoothProfile.STATE_CONNECTED) {
                                    /* Detect the connected reader. /
                            if (scannerManager != null) {
                                scannerManager.detectReader(inGatt, btLeGattCallback);
                            }
                        } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                            scannerReader = null;
                                    /*
                                     * Release resources occupied by Bluetooth
                                     * GATT client.
                                     /
                            if (btGatt != null) {
                                btGatt.close();
                                btGatt = null;
                            }
                        }
                    }
                });
            }
        });

        /* Initialize mBluetoothReaderManager. /
        scannerManager = new BluetoothReaderManager();

        /* Register BluetoothReaderManager's listeners /
        scannerManager.setOnReaderDetectionListener(new BluetoothReaderManager.OnReaderDetectionListener() {

                    @Override
                    public void onReaderDetection(BluetoothReader reader) {


                        if (reader instanceof Acr3901us1Reader) {
                            /* The connected reader is ACR3901U-S1 reader. /
                            Log.v("Reader Connection", "On Acr3901us1Reader Detected.");
                        } else if (reader instanceof Acr1255uj1Reader) {
                            /* The connected reader is ACR1255U-J1 reader. /
                            Log.v("Reader Connection", "On Acr1255uj1Reader Detected.");
                        } else {
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    Log.e("Reader Connection", "The device is not supported!");

                                    /* Disconnect Bluetooth reader /
                                    Log.e("Reader Coonection", "Disconnect reader!!!");
                                    disconnectReader();
                                    scannerConnectionState = BluetoothReader.STATE_DISCONNECTED;
                                }
                            });
                            return;
                        }

                        scannerReader = reader;
                        setListener(reader);
                        activateReader(reader);
                    }
                });

        /* Connect the reader. /
        connectReader();
    }
    */

    /*
    private void setListener(BluetoothReader reader) {
        /* Update status change listener /
        if (scannerReader instanceof Acr3901us1Reader) {
            ((Acr3901us1Reader) scannerReader)
                    .setOnBatteryStatusChangeListener(new Acr3901us1Reader.OnBatteryStatusChangeListener() {

                        @Override
                        public void onBatteryStatusChange(
                                BluetoothReader bluetoothReader,
                                final int batteryStatus) {

                            Log.i("Scanner Connection", "mBatteryStatusListener data: " + batteryStatus);

                        }

                    });
        } else if (scannerReader instanceof Acr1255uj1Reader) {
            ((Acr1255uj1Reader) scannerReader)
                    .setOnBatteryLevelChangeListener(new Acr1255uj1Reader.OnBatteryLevelChangeListener() {

                        @Override
                        public void onBatteryLevelChange(
                                BluetoothReader bluetoothReader,
                                final int batteryLevel) {

                            Log.i("Scanner Connection", "mBatteryLevelListener data: " + batteryLevel);

                        }

                    });
        }

        //THIS METHOD DOES NOT DETECT CHANGES TO BluetoothReader.CARD_STATUS_POWERED FOR SOME UNGODLY REASON
        scannerReader.setOnCardStatusChangeListener(new BluetoothReader.OnCardStatusChangeListener() {


                    @Override
                    public void onCardStatusChange(
                            BluetoothReader bluetoothReader, final int cardStatus) {

                        Log.i("Scanner Connection", "Card Status changed to : " + cardStatus);

                        if(cardStatus == BluetoothReader.CARD_STATUS_PRESENT)
                        {
                            Log.i("Scanner Connection", "about to transmit APDU : " + cardStatus);
                            //try
                            {
                                scannerTimer.schedule(new TimerTask() {
                                    @Override
                                    public void run() {
                                        transmitApdu();
                                    }
                                }, 100);

                            }
                            /*catch (InterruptedException e)
                            {
                                Log.e("Scanner Connection", "Delay for between card present and powered interupted, aborting transmit");
                            }/
                        }

                    }

                });

        /* Wait for authentication completed. /
        scannerReader.setOnAuthenticationCompleteListener(new BluetoothReader.OnAuthenticationCompleteListener() {

                    @Override
                    public void onAuthenticationComplete(BluetoothReader bluetoothReader, final int errorCode)
                    {

                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (errorCode == BluetoothReader.ERROR_SUCCESS) {
                                    Log.i("Scanner Connection", "Succesess");
                                    //mAuthentication.setEnabled(false);
                                } else {

                                    Log.i("Scanner Connection", "Fail");
                                }
                            }
                        });
                                if (errorCode == BluetoothReader.ERROR_SUCCESS)
                                {
                                    scannerIsAuthenticated = true;
                                    Log.i("Scanner Connection", "Authentication Success! Starting Polling ");
                                    startPolling();

                                }
                                else
                                {
                                    Log.i("Scanner Connection", "Authentication Failed!");
                                }
                    }

                });


        /* Wait for response APDU. /
        Log.i("Scanner Connection", "Response Listener setting up");
        scannerReader
                .setOnResponseApduAvailableListener(new BluetoothReader.OnResponseApduAvailableListener() {

                    @Override
                    public void onResponseApduAvailable(
                            BluetoothReader bluetoothReader, final byte[] apdu,
                            final int errorCode) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Log.i("LISTENER RESPONSE", "response apdu recieved");
                                if(errorCode != BluetoothReader.ERROR_SUCCESS)
                                {
                                    Log.e("LISTENER RESPONSE", "response apdu error:" + errorCode);
                                }
                                else
                                {
                                    Log.i("LISTENER RESPONSE", "response apdu success with: " + apdu + "\n Translates to: " + toHexString(apdu));
                                    scanKeg(toHexString(apdu));
                                }
                                //mTxtResponseApdu.setText(getResponseString(apdu, errorCode));
                            }
                        });
                    }

                });
        Log.i("Scanner Connection", "Response Listener set up complete");




        /* Handle on battery status available. /
        if (scannerReader instanceof Acr3901us1Reader)
        {
            ((Acr3901us1Reader) scannerReader).setOnBatteryStatusAvailableListener(new Acr3901us1Reader.OnBatteryStatusAvailableListener()
            {
                        @Override
                        public void onBatteryStatusAvailable(BluetoothReader bluetoothReader, final int batteryStatus, int status)
                        {
                            Log.i("Scanner Connection", "Battery Status String: " + batteryStatus);
                        }
            });
        }

        /* Handle on slot status available. /
        scannerReader.setOnCardStatusAvailableListener(new BluetoothReader.OnCardStatusAvailableListener()
        {
             @Override
             public void onCardStatusAvailable(BluetoothReader bluetoothReader, final int cardStatus, final int errorCode)
             {
                 if (errorCode != BluetoothReader.ERROR_SUCCESS)
                 {
                     Log.i("Scanner Connection", "CardStatusError : " + errorCode );
                 }
                 else
                 {
                     Log.i("Scanner Connection", "Card Status Avaiable: " + (cardStatus));

                 }
             }
        });

        scannerReader.setOnEnableNotificationCompleteListener(new BluetoothReader.OnEnableNotificationCompleteListener()
        {

                    @Override
                    public void onEnableNotificationComplete(BluetoothReader bluetoothReader, final int result)
                    {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                if (result != BluetoothGatt.GATT_SUCCESS) {
                                    /* Fail /
                                    Log.i("Scanner Connection", "The device is unable to set notification!");
                                }
                                else
                                {
                                    Log.i("Scanner Connection", "The device is ready to use!");
                                    //ScannerAuthLoop is recursive
                                    scannerAuthLoop(500);
                                }
                            }
                        });
                    }
        });


    }

    //This recursive method queues a new attempt to Authenticate the scanner every 500ms until the scanner is successfully authenicated.
    //This method is nessary to circumvente the Authentication process sometimes failing, and the OnAuthenticationComplete listener failing to detect
    // or respond to failures to authenticate most, but not all, of the time.
    //TODO: Consider adding a hard limit of the amount of times this can run to the method.
    private void scannerAuthLoop(final int delay)
    {
        scannerTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                if(!scannerIsAuthenticated)
                {
                    scannerAuthenticate();
                    scannerAuthLoop(delay + 500);
                }
            }
        }, delay);
    }

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            BluetoothAdapter bluetoothAdapter = null;
            BluetoothManager bluetoothManager = null;
            final String action = intent.getAction();

            if (!(scannerReader instanceof Acr3901us1Reader)) {
                /* Only ACR3901U-S1 require bonding. /
                return;
            }

            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(action))
            {
                Log.i("Scanner Connection", "ACTION_BOND_STATE_CHANGED");

                /* Get bond (pairing) state /
                if (scannerManager == null) {
                    Log.w("Scanner Connection", "Unable to initialize BluetoothReaderManager.");
                    return;
                }

                bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
                if (bluetoothManager == null) {
                    Log.w("Scanner Connection", "Unable to initialize BluetoothManager.");
                    return;
                }

                bluetoothAdapter = bluetoothManager.getAdapter();
                if (bluetoothAdapter == null) {
                    Log.w("Scanner Connection", "Unable to initialize BluetoothAdapter.");
                    return;
                }

                final BluetoothDevice device = bluetoothAdapter
                        .getRemoteDevice(storedScannerAddress);

                if (device == null) {
                    mapText.setText("Device not found.\n Wake up the device or \nTry Pairing the device again.");
                    Log.e("Scanner Connection", "Device not found, likely no address stored.");
                    return;
                }

                final int bondState = device.getBondState();

                // TODO: remove log message
                Log.i("", "BroadcastReceiver - getBondState. state = "
                        + bondState);

                /* Enable notification /
                if (bondState == BluetoothDevice.BOND_BONDED) {
                    if (scannerReader != null) {
                        Log.i("Scanner Connection", "Notifictions enabled");
                        scannerReader.enableNotification(true);
                    }
                }


            }
        }

    };*/

    /*
    private boolean connectReader() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        if (bluetoothManager == null) {
            Log.w("Scanner Connection", "Unable to initialize BluetoothManager.");
            scannerConnectionState = BluetoothReader.STATE_DISCONNECTED;
            return false;
        }

        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null) {
            Log.w("Scanner Connection", "Unable to obtain a BluetoothAdapter.");
            scannerConnectionState = BluetoothReader.STATE_DISCONNECTED;
            return false;
        }

        /*
         * Connect Device.
         */
        /* Clear old GATT connection. /
        if (btGatt != null) {
            Log.i("Scanner Connection", "Clear old GATT connection");
            btGatt.disconnect();
            btGatt.close();
            btGatt = null;
        }
        else
        {
            Log.i("Scanner Connection", "old GATT connection already clear");
        }

        /* Create a new connection. /
        BluetoothDevice device = null;

        if(!storedScannerAddress.matches("None"))
        {
            device = bluetoothAdapter.getRemoteDevice(storedScannerAddress);
        }

        if (device == null) {
            Log.e("Scanner Connection", "Device not found. Unable to connect.");
            mapText.setText("Device not found.\n Wake up the \ndevice or \nTry Pairing the \ndevice again.");
            return false;
        }

        /* Connect to GATT server. /
        scannerConnectionState = BluetoothReader.STATE_CONNECTING;
        btGatt = device.connectGatt(this, false, btLeGattCallback);
        return true;
    }

    /* Disconnects an established connection. /
    private void disconnectReader() {
        if (btGatt == null) {
            scannerConnectionState = BluetoothReader.STATE_DISCONNECTED;
            return;
        }
        scannerConnectionState = BluetoothReader.STATE_DISCONNECTING;
        stopPolling();
        btGatt.disconnect();
        Log.i("Scanner Connection", "Disconnected from scanner");
    }

    /* Start the process to enable the reader's notifications. /
    private void activateReader(BluetoothReader reader) {
        if (reader == null) {
            return;
        }

        if (reader instanceof Acr3901us1Reader) {
            /* Start pairing to the reader. /
            ((Acr3901us1Reader) scannerReader).startBonding();
        } else if (scannerReader instanceof Acr1255uj1Reader) {
            /* Enable notification. /
            scannerReader.enableNotification(true);
            Log.i("Scanner Connection", "Notifications enabled");
        }
    }

    private void scannerAuthenticate()
    {
        if (scannerReader == null) {
            Log.e("Scanner Connection", "card_reader_not_ready");
            return;
        }

                /* Retrieve master key from edit box. /
        byte masterKey[] = null;

        try
        {
            masterKey = getEditTextinHexBytes(toHexString(DEFAULT_1255_MASTER_KEY.getBytes("UTF-8")));
        }
        catch (Exception e)
        {
            Log.e("Scanner Connection", "UnsupportedEncodingException :" + e.toString());
            return;
        }
                    /* Start authentication. /
        Log.i("Scanner Connection", "Authenticating with key: " + masterKey);
        if (!scannerReader.authenticate(masterKey))
        {
            Log.e("Scanner Connection", "Authenticate Error: card_reader_not_ready");
        }
        else
        {

            Log.i("Scanner Connection", "Authenticating...");
        }

    }

    /* Start polling card. /
    private void startPolling()
    {
        if (scannerReader == null)
        {

            Log.e("Scanner Connection", "Polling Error, unable to start polling, reader not found");
            return;
        }
        if (!scannerReader.transmitEscapeCommand(AUTO_POLLING_START))
        {
            Log.e("Scanner Connection", "Polling Error, unable to start polling");
        }
    }

    /* Stop polling card. /
    private void stopPolling()
        {
        if (scannerReader == null) {
            Log.e("Scanner Connection", "Polling Stop Error, unable to stop polling, reader not found");;
            return;
        }
        if (!scannerReader.transmitEscapeCommand(AUTO_POLLING_STOP)) {
            Log.e("Scanner Connection", "Polling Stop Error, unable to stop polling");
        }
    }

    private void transmitApdu()
    {
        Log.i("Scanner Connection", "APDU Transmiting started.");
        /* Check for detected reader. /
        if (scannerReader == null)
        {
            Log.e("Scanner Connection", "APDU Transmit Error, scanner not found");
            return;
        }

                /* Retrieve APDU command from edit box. /
        byte apduCommand[] = GET_UID_APDU_COMMAND;

        if (apduCommand != null && apduCommand.length > 0)
        {

                    /* Transmit APDU command. /
            if (!scannerReader.transmitApdu(apduCommand))
            {
                Log.e("Scanner Connection", "APDU Transmit Error, Card not ready");
            }
            else
            {
                Log.i("Scanner Connection", "APDU Transmit Successful with command : " + apduCommand + "\nTranslation : " + toHexString(apduCommand));
            }
        }
        else
        {
            Log.e("Scanner Connection", "APDU Transmit Error, Character Format Error");
        }
    }

    private String toHexString(byte[] array)
    {

        String bufferString = "";

        if (array != null) {
            for (int i = 0; i < array.length; i++) {
                String hexChar = Integer.toHexString(array[i] & 0xFF);
                if (hexChar.length() == 1) {
                    hexChar = "0" + hexChar;
                }
                bufferString += hexChar.toUpperCase(Locale.US) + " ";
            }
        }
        return bufferString;
    }

    private byte[] getEditTextinHexBytes(String key)
    {
        String rawdata = key;


        Log.i("Scanner Connection", "gettingHexBytes with rawData: " + rawdata);

        if (rawdata == null || rawdata.isEmpty()) {

            return null;
        }

        String command = rawdata.replace(" ", "").replace("\n", "");

        Log.i("Scanner Connection", "gettingHexBytes with command: " + command);

        if (command.isEmpty() || command.length() % 2 != 0 || isHexNumber(command) == false)
        {
            return null;
        }

        return hexString2Bytes(command);
    }

    private byte[] hexString2Bytes(String string) {
        Log.i("Converting Key", "hexString in :" + string);
        if (string == null)
            throw new NullPointerException("string was null");

        int len = string.length();

        if (len == 0)
            return new byte[0];
        if (len % 2 == 1)
            throw new IllegalArgumentException(
                    "string length should be an even number");

        byte[] ret = new byte[len / 2];
        byte[] tmp = string.getBytes();

        for (int i = 0; i < len; i += 2) {
            if (!isHexNumber(tmp[i]) || !isHexNumber(tmp[i + 1])) {
                throw new NumberFormatException(
                        "string contained invalid value");
            }
            ret[i / 2] = uniteBytes(tmp[i], tmp[i + 1]);
        }
        return ret;
    }

    private static byte uniteBytes(byte src0, byte src1) {
        byte _b0 = Byte.decode("0x" + new String(new byte[] { src0 }))
                .byteValue();
        _b0 = (byte) (_b0 << 4);
        byte _b1 = Byte.decode("0x" + new String(new byte[] { src1 }))
                .byteValue();
        byte ret = (byte) (_b0 ^ _b1);
        return ret;
    }

    private boolean isHexNumber(String string) {
        if (string == null)
            throw new NullPointerException("string was null");

        boolean flag = true;

        for (int i = 0; i < string.length(); i++) {
            char cc = string.charAt(i);
            if (!isHexNumber((byte) cc)) {
                flag = false;
                break;
            }
        }
        return flag;
    }

    private boolean isHexNumber(byte value) {
        if (!(value >= '0' && value <= '9') && !(value >= 'A' && value <= 'F')
                && !(value >= 'a' && value <= 'f')) {
            return false;
        }
        return true;
    }

    /* Get the Error string. /
    private String getErrorString(int errorCode) {
        if (errorCode == BluetoothReader.ERROR_SUCCESS) {
            return "";
        } else if (errorCode == BluetoothReader.ERROR_INVALID_CHECKSUM) {
            return "The checksum is invalid.";
        } else if (errorCode == BluetoothReader.ERROR_INVALID_DATA_LENGTH) {
            return "The data length is invalid.";
        } else if (errorCode == BluetoothReader.ERROR_INVALID_COMMAND) {
            return "The command is invalid.";
        } else if (errorCode == BluetoothReader.ERROR_UNKNOWN_COMMAND_ID) {
            return "The command ID is unknown.";
        } else if (errorCode == BluetoothReader.ERROR_CARD_OPERATION) {
            return "The card operation failed.";
        } else if (errorCode == BluetoothReader.ERROR_AUTHENTICATION_REQUIRED) {
            return "Authentication is required.";
        } else if (errorCode == BluetoothReader.ERROR_LOW_BATTERY) {
            return "The battery is low.";
        } else if (errorCode == BluetoothReader.ERROR_CHARACTERISTIC_NOT_FOUND) {
            return "Error characteristic is not found.";
        } else if (errorCode == BluetoothReader.ERROR_WRITE_DATA) {
            return "Write command to reader is failed.";
        } else if (errorCode == BluetoothReader.ERROR_TIMEOUT) {
            return "Timeout.";
        } else if (errorCode == BluetoothReader.ERROR_AUTHENTICATION_FAILED) {
            return "Authentication is failed.";
        } else if (errorCode == BluetoothReader.ERROR_UNDEFINED) {
            return "Undefined error.";
        } else if (errorCode == BluetoothReader.ERROR_INVALID_DATA) {
            return "Received data error.";
        } else if (errorCode == BluetoothReader.ERROR_COMMAND_FAILED) {
            return "The command failed.";
        }
        return "Unknown error.";
    }

    /*private class scannerGattCallBack extends BluetoothGattCallback
    {

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
        {
            super.onConnectionStateChange(gatt, status, newState);

            runOnUiThread(new Runnable() {
                @Override
                public void run()
                {
                    if (state != BluetoothGatt.GATT_SUCCESS)
                    {
                                     *
                                     * Show the message on fail to
                                     * connect/disconnect.
                                     *
                        mConnectState = BluetoothReader.STATE_DISCONNECTED;

                        if (newState == BluetoothReader.STATE_CONNECTED) {
                            Log.e("ScannerConnection", "Bt Reader Failled to connect");
                        } else if (newState == BluetoothReader.STATE_DISCONNECTED) {
                            Log.e("ScannerConnection", "Bt Reader Failed To Disconnect");

                        }


                        return;
                    }

                    updateConnectionState(newState);

                    scannerSetup();
                }
            });

        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
        {
            super.onCharacteristicChanged(gatt, characteristic);

            //Insert get characteristic logic to retrieve the uuid scanned here.
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status)
        {
            super.onServicesDiscovered(gatt, status);
            List<BluetoothGattService> services = btGatt.getServices();
            for (BluetoothGattService service: services)
            {
                List<BluetoothGattCharacteristic> characteristics = service.getCharacteristics();
                for (BluetoothGattCharacteristic aCharacteristic: characteristics)
                {
                    //if(aCharacteristic == TheCharacteristicWeWant)
                    {
                        for (BluetoothGattDescriptor descriptor : aCharacteristic.getDescriptors())
                        {
                            if(descriptor.getUuid() == UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG))
                            {
                                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                                btGatt.writeDescriptor(descriptor);
                                break;
                            }
                        }
                        break;
                    }
                }
            }
        }
    }*/

//++++++++++[/Bluetooth BLE CODE]


//**********[Location Update and server pinging Code]
    @Override
    public void onConnectionFailed(ConnectionResult result) {
        // An unresolvable error has occurred and a connection to Google APIs
        // could not be established. Display an error message, or handle
        // the failure silently

        // ...
    }

    @Override
    public void onConnected(@Nullable Bundle bundle)
    {
        if(ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        {
            locationReceivedFromLocationUpdates = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, request, this);
            if(locationReceivedFromLocationUpdates != null)
            {
                //YES, lat and long are multi digit.
                if(Geocoder.isPresent())
                {
                    startIntentService();
                }
                else
                {
                    Log.e("ERROR:", "Geocoder is not avaiable");
                }
            }
            else
            {

            }


        }



    }

    @Override
    public void onConnectionSuspended(int i)
    {
        //put other stuff here
    }

    //update app based on the new location data, and then begin pinging servlet with the new location
    @Override
    public void onLocationChanged(Location location)
    {
        locationReceivedFromLocationUpdates = location;
        userLocation = locationReceivedFromLocationUpdates;
        //locationReceivedFromLocationUpdates = fakeUserLocation;


        if(locationReceivedFromLocationUpdates != null)
        {
            String userName = "A Truck";
            if(!nameEditText.getText().toString().matches(""))
            {
                userName = nameEditText.getText().toString();
            }
            serverURL = serverIPAddress + "?request=storelocation" + "&id=" + itemID + "&name=" + itemName.replace(' ', '_') + "&lat=" + locationReceivedFromLocationUpdates.getLatitude() + "&lon=" + locationReceivedFromLocationUpdates.getLongitude();
            //lat and long are doubles, will cause issue? nope
            Log.i("Network Update", "Attempting to start download from onLocationChanged. " + serverURL);

            if(hasState)//if the activity is currently not paused/stopped
            {
                aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);
            }


            //startDownload();
        }
        else
        {

            Log.e("ERROR", "Unable to send location to sevrver, current location = null");
        }

    }


    @Override
    public void onSaveInstanceState(Bundle savedState)
    {
        savedState.putParcelable(SAVED_LOCATION_KEY, locationReceivedFromLocationUpdates);
        super.onSaveInstanceState(savedState);
    }

    private void restoreSavedValues(Bundle savedInstanceState)
    {
        if (savedInstanceState != null)
        {

            // Update the value of mCurrentLocation from the Bundle and update the
            // UI to show the correct latitude and longitude.
            if (savedInstanceState.keySet().contains(SAVED_LOCATION_KEY))
            {
                // Since LOCATION_KEY was found in the Bundle, we can be sure that
                // mCurrentLocationis not null.
                locationReceivedFromLocationUpdates = savedInstanceState.getParcelable(SAVED_LOCATION_KEY);
            }

        }
    }

    protected void startIntentService() {
        Intent intent = new Intent(this, geoCoderIntent.class);
        intent.putExtra(Constants.RECEIVER, geoCoderServiceResultReciever);
        intent.putExtra(Constants.LOCATION_DATA_EXTRA, locationReceivedFromLocationUpdates);
        startService(intent);
    }

    //Update activity based on the results sent back by the servlet.
    @Override
    public void updateFromDownload(String result) {
        //intervalTextView.setText("Interval: " + result);

        if(result != null)
        {
            Log.i("Network UPDATE", "Non null result received." );
            //mapText.setText("We're good");
            if(pingingServerFor_KegData)
            {
                pingingServerFor_KegData = false;
                result = result.replace("was Picked up at:", "\nwas picked up at:");
                result = result.replace(") at ", ") \nat ");
                result = result.replace("then Dropped at:", "\nthen dropped at:");
                result = result.replace("by Driver:", "\nby driver:");

                result = result.replace("is being transported by truck", "\nis being transported by driver: ");
                result = result.replace("currently at ", "\ncurrently at: ");
                kegDataText.setText(result);

            }
            else
            {
                if (itemID == 0 && !result.matches(""))//if app has no assigned id, receive id from servlet.
                {
                    try
                    {
                        JSONArray jin = new JSONArray(result);
                        JSONObject obj = jin.getJSONObject(0);
                        itemID = obj.getInt("id");
                    } catch (JSONException e)
                    {
                        Log.e("JSON ERROR", "Error retrieving id from servlet with exception: " + e.toString());
                    }
                }
            }


        }
        else
        {
            mapText.setText("Error: network unavaiable");
            Log.e("Network UPDATE", "Error: network unavaiable");
        }

        Log.e("Download Output", "" + result);
    }

    @Override
    public NetworkInfo getActiveNetworkInfo() {
        ConnectivityManager connectivityManager =
                (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        return networkInfo;
    }

    @Override
    public void onProgressUpdate(int progressCode, int percentComplete) {
        switch(progressCode) {
            // You can add UI behavior for progress updates here.
            case Progress.ERROR:
                Log.e("Progress Error", "there was an error during a progress report at: " + percentComplete + "%");
                break;
            case Progress.CONNECT_SUCCESS:
                Log.i("Progress ", "connection successful during a progress report at: " + percentComplete + "%");
                break;
            case Progress.GET_INPUT_STREAM_SUCCESS:
                Log.i("Progress ", "input stream acquired during a progress report at: " + percentComplete + "%");
                break;
            case Progress.PROCESS_INPUT_STREAM_IN_PROGRESS:
                Log.i("Progress ", "input stream in progress during a progress report at: " + percentComplete + "%");
                break;
            case Progress.PROCESS_INPUT_STREAM_SUCCESS:
                Log.i("Progress ", "input stream processing successful during a progress report at: " + percentComplete + "%");
                break;
        }
    }

    @Override
    public void finishDownloading() {
        pingingServer = false;
        Log.i("Network Update", "finished Downloading");
        if (aNetworkFragment != null) {
            Log.e("Network Update", "network fragment found, canceling download");
            aNetworkFragment.cancelDownload();
        }
    }

    class AddressResultReceiver extends ResultReceiver
    {
        public AddressResultReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {

            // Display the address string
            // or an error message sent from the intent service.
            resultData.getString(Constants.RESULT_DATA_KEY);


            // Show a toast message if an address was found.
            if (resultCode == Constants.SUCCESS_RESULT)
            {
                Log.i("Success", "Address found");
            }
            else
            {
                Log.e("Network Error:", "in OnReceiveResult in AddressResultReceiver: " +  resultData.getString(Constants.RESULT_DATA_KEY));
            }

        }
    }
//**********[/Location Update and server pinging Code]
}
