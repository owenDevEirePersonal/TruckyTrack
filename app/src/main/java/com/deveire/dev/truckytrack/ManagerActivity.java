package com.deveire.dev.truckytrack;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.location.Location;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.vision.barcode.Barcode;

import org.json.JSONArray;
import org.json.JSONException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;

public class ManagerActivity extends FragmentActivity implements AdapterView.OnItemSelectedListener, OnMapReadyCallback, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks, DownloadCallback<String>
{

    private GoogleMap mMap;

    private TextView mapText;
    private Spinner itemsSpinner;
    private EditText countText;
    private Button dateButton;

    Dialog datePickerDialog;
    private final int datePickerDialogID = 99991;
    private String startDate;
    private String endDate;

    private ArrayList<String> itemIdsFromServer;
    private String currentItemID;
    private int numberOfResultsToRetrieve;
    private ArrayList<LatLng> allCurrentItemLocations;

    private ArrayList<String> allCurrentKegIDs;

    private Location userLocation;

    //[Network and periodic location update, Variables]
    private GoogleApiClient mGoogleApiClient;
    private Location usersLocation;
    private int locationScanInterval;

    private final String SAVED_LOCATION_KEY = "79";

    private int pingingServerFor;

    private Handler refreshHandler;

    private final int pingingServerFor_ItemIds = 1;
    private final int pingingServerFor_Locations = 2;
    private final int pingingServerFor_Extra_Locations = 3;
    private final int pingingServerFor_Keg_Last_Locations = 4;
    private final int pingingServerFor_Nothing = 0;


    private final String serverIPAddress = "http://192.168.1.188:8080/TruckyTrackServlet/TTServlet";
    //private final String serverIPAddress = "http://api.eirpin.com/api/TTServlet";
    private String serverURL;
    private NetworkFragment aNetworkFragment;
    //[/Network and periodic location update, Variables]

    //[Testing Variables]
    private ArrayList<LatLng> testStoreOfLocations;
    //[/Testing Variables]

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manager);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);



        mapText = (TextView) findViewById(R.id.mapText);
        itemsSpinner = (Spinner) findViewById(R.id.spinner);
        itemsSpinner.setOnItemSelectedListener(this);

        itemIdsFromServer = new ArrayList<String>();
        allCurrentItemLocations = new ArrayList<LatLng>();

        currentItemID = "";
        numberOfResultsToRetrieve = 10;

        mapText = (TextView) findViewById(R.id.mapText);
        itemsSpinner = (Spinner) findViewById(R.id.spinner);
        //countText = (EditText) findViewById(R.id.editText);
        //countText.setText("" + numberOfResultsToRetrieve);

        dateButton = (Button) findViewById(R.id.dateButton);
        dateButton.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                openDatePickerDialog(false);
            }
        });


        Calendar adate = Calendar.getInstance();
        SimpleDateFormat adt = new SimpleDateFormat("yyyy-MM-dd");
        startDate = adt.format(adate.getTime());
        adate.add(Calendar.DATE, 1);
        endDate = adt.format(adate.getTime());
        Log.i("Date", startDate + "     " + endDate);


        userLocation = new Location("Truck Manager");
        userLocation.setLatitude(52.663585);
        userLocation.setLongitude(-8.636135);

        pingingServerFor = pingingServerFor_Nothing;

        refreshHandler = new Handler();
        startRepeatingRefresh();


        //0000,0000 is a location in the middle of the atlantic occean south of western africa and unlikely to contain a golf course.

        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();

        mGoogleApiClient.connect();

        locationScanInterval = 60;//in seconds




        restoreSavedValues(savedInstanceState);

    }

    @Override
    protected void onPause()
    {
        if(aNetworkFragment != null)
        {
            aNetworkFragment.cancelDownload();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        stopRepeatingRefresh();
    }

    private void startRepeatingRefresh()
    {
        periodicRefresher.run();
    }

    private void stopRepeatingRefresh()
    {
        refreshHandler.removeCallbacks(periodicRefresher);
    }

    //TODO: Disable this when the user navigates away from the app
    Runnable periodicRefresher = new Runnable()
    {
        @Override
        public void run()
        {
            try
            {
                Log.i("Network Update", "Launching Refresh ");
                serverURL = serverIPAddress + "?request=getitemids";
                pingingServerFor = pingingServerFor_ItemIds;
                aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);
            }
            finally
            {
                refreshHandler.postDelayed(periodicRefresher, locationScanInterval * 1000);
            }
        }
    };


    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap)
    {
        mMap = googleMap;

        // Add a markers and move the camera
        //mMap.addMarker(new MarkerOptions().position(new LatLng(userLocation.getLatitude(), userLocation.getLongitude())).title("You"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(userLocation.getLatitude(), userLocation.getLongitude()), 7));
        mMap.setOnCameraIdleListener(new mapScrolledListener());
        retrieveLocations();
        //updateMap(false);
    }

    private void updateMap(boolean clearPrevious)
    {
        if(clearPrevious)
        {
            mMap.clear();
        }

        int i = 0;
        for (LatLng aloc: allCurrentItemLocations)
        {
            if(currentItemID.matches("Current Keg Locations") || currentItemID.matches("Keg Deliveries Between"))
            {
                mMap.addMarker(new MarkerOptions().position(aloc).title(allCurrentKegIDs.get(i)));
                Log.i("Map Update", "Placing Keg Marker " + allCurrentKegIDs.get(i) + " at " + aloc.toString());
            }
            else
            {
                mMap.addMarker(new MarkerOptions().position(aloc).title("Truck 1"));
                Log.i("Map Update", "Placing Marker at " + aloc.toString()  + "\n");
            }
            i++;
        }
        //mMap.addMarker(new MarkerOptions().position(new LatLng(userLocation.getLatitude(), userLocation.getLongitude())).title("You"));

    }



    private void retrieveLocations()
    {
        pingingServerFor = pingingServerFor_Locations;
        serverURL = serverIPAddress + "?request=getinitlocations&id=" + (currentItemID.split(" - ")[0]) + "&count=" + numberOfResultsToRetrieve;
        //lat and long are doubles, will cause issue? nope
        Log.i("Network Update", "Attempting to start download from retrievelocations." + serverURL);
        aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);

        Log.i("Dropdown Update", "Dropdown select, Loading intial locations for " + (currentItemID.split(" - ")[0]));
    }

    private void retrieveLocations(LatLng centreOfMap, double radiusInMetres)
    {
        pingingServerFor = pingingServerFor_Extra_Locations;
        serverURL = serverIPAddress + "?request=getlocationswithin&id=" + (currentItemID.split(" - ")[0]) + "&lat=" + centreOfMap.latitude + "&lon=" + centreOfMap.longitude + "&radius=" + radiusInMetres;
        //lat and long are doubles, will cause issue? nope
        Log.i("Network Update", "Attempting to start download to retrieve locations(centreOfMap, radiusInMetres)." + serverURL);
        aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);

        Log.i("Dropdown Update", "Map Moved, Loading new locations for " + (currentItemID.split(" - ")[0]) + " from centre " + centreOfMap.toString() + " at radius " + radiusInMetres);
    }

    private void retrieveKegLastLocations()
    {
        pingingServerFor = pingingServerFor_Keg_Last_Locations;
        serverURL = serverIPAddress + "?request=getkegslastlocations";
        //lat and long are doubles, will cause issue? nope
        Log.i("Network Update", "Attempting to start download from retrieveKegLastLocations. " + serverURL);
        aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);

        Log.i("Dropdown Update", "Dropdown select, Loading retrieveKegLastLocations");
    }

    private void retrieveKegLastLocationsOnDate(String inDate)
    {
        pingingServerFor = pingingServerFor_Keg_Last_Locations;
        serverURL = serverIPAddress + "?request=getkegslastlocationsonadate&date=" + inDate;
        //lat and long are doubles, will cause issue? nope
        Log.i("Network Update", "Attempting to start download from retrieveKegLastLocations. " + serverURL);
        aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);

        Log.i("Dropdown Update", "Dropdown select, Loading retrieveKegLastLocations");
    }

    private void retrieveKegAllLocationsOnDate(String inDate)
    {
        pingingServerFor = pingingServerFor_Keg_Last_Locations;
        serverURL = serverIPAddress + "?request=getkegslastlocationsonadate&date=" + inDate;
        //lat and long are doubles, will cause issue? nope
        Log.i("Network Update", "Attempting to start download from retrieveKegLastLocations. " + serverURL);
        aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);

        Log.i("Dropdown Update", "Dropdown select, Loading retrieveKegLastLocations");
    }

    private void retrieveKegAllLocationsBetweenDates(String inStartDate, String inEndDate)
    {
        pingingServerFor = pingingServerFor_Keg_Last_Locations;
        serverURL = serverIPAddress + "?request=getkegslocationsbetweendates&startdate=" + inStartDate + "&enddate=" + inEndDate;
        //lat and long are doubles, will cause issue? nope
        Log.i("Network Update", "Attempting to start download from retrieveKegLocationsBetweenDates. " + serverURL);
        aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);

        Log.i("Dropdown Update", "Dropdown select, Loading retrieveKegLastLocations");
    }

    private boolean arrayContains(ArrayList<LatLng> array, LatLng bLatLng)
    {
        for (LatLng aLatLng: array)
        {
            if(bLatLng.latitude == aLatLng.latitude && bLatLng.longitude == aLatLng.longitude)
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id)
    {
        //[Get currently selected id from dropdown menu]
        currentItemID = itemIdsFromServer.get(pos);
        //[/Get currently selected id from dropdown menu]

        //if item selected is not the last item(all kegs) or 2nd last then behave normally
        if(pos == itemIdsFromServer.size() - 1)
        {
            Log.i("Dropdown Update", "Dropdown select, ItemID now equals All Kegs");
            retrieveKegLastLocations();
        }
        else if(pos == itemIdsFromServer.size() - 2)
        {
            Log.i("Dropdown Update", "Dropdown select, ItemID now equals Keg Deliveries Between");
            retrieveKegAllLocationsBetweenDates(startDate, endDate);
        }
        else
        {
            Log.i("Dropdown Update", "Dropdown select, ItemID now equals " + parent.getItemAtPosition(pos));
            try
            {
                //numberOfResultsToRetrieve = Integer.getInteger(countText.getText().toString());
            } catch (Exception e)
            {
                numberOfResultsToRetrieve = 10;
                //countText.setText("" + numberOfResultsToRetrieve);
            }
            retrieveLocations();
        }

    }

    @Override
    public void onNothingSelected(AdapterView<?> parent)
    {

    }

    private void selectItemOnStartup()
    {
        //[Get currently selected id from dropdown menu]
        currentItemID = itemIdsFromServer.get(0);
        //TODO: replace ID, name system with single string consiting of "id - name"
        //[/Get currently selected id from dropdown menu]

        try
        {
            //numberOfResultsToRetrieve = Integer.getInteger(countText.getText().toString());
        }
        catch (Exception e)
        {
            numberOfResultsToRetrieve = 10;
            //countText.setText("" + numberOfResultsToRetrieve);
        }
        retrieveLocations();
    }

    private void openDatePickerDialog(boolean isEndDatePicker)
    {
        Context context = ManagerActivity.this;
        datePickerDialog = new Dialog(context);
        datePickerDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        datePickerDialog.setContentView(R.layout.dialog_datepicker);

        Button okButton = (Button) datePickerDialog.findViewById(R.id.okButton);
        TextView datePickerText = (TextView) datePickerDialog.findViewById(R.id.pickDateText);
        final DatePicker aDatePicker = (DatePicker) datePickerDialog.findViewById(R.id.startDatePicker);
        if(isEndDatePicker)
        {
            datePickerText.setText("Pick End Date");
            okButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    String dateString = aDatePicker.getDayOfMonth() + " " + (aDatePicker.getMonth() + 1) + " " + aDatePicker.getYear();
                    SimpleDateFormat dt = new SimpleDateFormat("dd MM yyyy");
                    try
                    {
                        Date adate = dt.parse(dateString);
                        dt = new SimpleDateFormat("yyyy-MM-dd");
                        dateString = dt.format(adate);
                    }
                    catch (ParseException e)
                    {
                        Log.e("Date", "error");
                    }
                    Log.i("Date", "New End Date: " + dateString);
                    endDate = dateString;
                    datePickerDialog.dismiss();
                    if(currentItemID.matches("Keg Deliveries Between"))
                    {
                        retrieveKegAllLocationsBetweenDates(startDate, endDate);
                    }
                }
            });
        }
        else
        {
            datePickerText.setText("Pick Start Date");
            okButton.setOnClickListener(new View.OnClickListener()
            {
                @Override
                public void onClick(View v)
                {
                    String dateString = aDatePicker.getDayOfMonth() + " " + (aDatePicker.getMonth() + 1) + " " + aDatePicker.getYear();
                    SimpleDateFormat dt = new SimpleDateFormat("dd MM yyyy");
                    try
                    {
                        Date adate = dt.parse(dateString);
                        dt = new SimpleDateFormat("yyyy-MM-dd");
                        dateString = dt.format(adate);
                    }
                    catch (ParseException e)
                    {
                        Log.e("Date", "error");
                    }
                    Log.i("Date", "New Start Date: " + dateString);
                    startDate = dateString;
                    datePickerDialog.dismiss();
                    openDatePickerDialog(true);
                }
            });
        }


        datePickerDialog.show();

    }


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
            usersLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
        }
    }

    @Override
    public void onConnectionSuspended(int i)
    {
        //put other stuff here
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        super.onActivityResult(requestCode, resultCode, data);
        //receive request changed.
    }

    @Override
    public void onSaveInstanceState(Bundle savedState)
    {
        savedState.putParcelable(SAVED_LOCATION_KEY, usersLocation);
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
                usersLocation = savedInstanceState.getParcelable(SAVED_LOCATION_KEY);
            }

        }
    }

    //Update activity based on the results sent back by the servlet.
    @Override
    public void updateFromDownload(String result) {
        //intervalTextView.setText("Interval: " + result);
        Log.i("Download Update", "\n Starting UpdateFromDownload \n \n");
        try
        {
            if(result != null)
            {
                JSONArray jsonResultFromServer = new JSONArray(result);
                switch (pingingServerFor)
                {
                    case pingingServerFor_ItemIds:
                        Log.i("Network JSON", "pingingServerFor_ItemIds, lets begin, shall we...");
                        itemIdsFromServer = new ArrayList<String>();
                        for(int i = 0; i < jsonResultFromServer.length(); i++)
                        {
                            itemIdsFromServer.add(jsonResultFromServer.getJSONObject(i).getString("name"));
                        }

                        itemIdsFromServer.add("Keg Deliveries Between");
                        itemIdsFromServer.add("Current Keg Locations");


                        ArrayAdapter<String> adapter = new ArrayAdapter<String>(ManagerActivity.this, android.R.layout.simple_spinner_dropdown_item, itemIdsFromServer);
                        itemsSpinner.setAdapter(adapter);

                        break;

                    case pingingServerFor_Locations:
                        allCurrentItemLocations = new ArrayList<LatLng>();

                        for(int i = 0; i < jsonResultFromServer.length(); i++)
                        {
                            LatLng aloc = new LatLng(jsonResultFromServer.getJSONObject(i).getDouble("lat"), jsonResultFromServer.getJSONObject(i).getDouble("lon"));
                            allCurrentItemLocations.add(aloc);
                            Log.i("Boop Test", "BOOP LOCATION LOADED " + aloc.toString());
                        }
                        Log.i("Location Update", "Recieved locations.");
                        updateMap(true);
                        mapText.setText("Receving Locations");

                        break;

                    case pingingServerFor_Extra_Locations:
                        allCurrentItemLocations = new ArrayList<LatLng>();

                        for(int i = 0; i < jsonResultFromServer.length(); i++)
                        {
                            LatLng aloc = new LatLng(jsonResultFromServer.getJSONObject(i).getDouble("lat"), jsonResultFromServer.getJSONObject(i).getDouble("lon"));
                            allCurrentItemLocations.add(aloc);
                        }

                        Log.i("Location Update", "Recieved extra locations.");
                        updateMap(false);
                        mapText.setText("Receving Locations");

                        break;

                    case pingingServerFor_Keg_Last_Locations:
                        allCurrentItemLocations = new ArrayList<LatLng>();
                        allCurrentKegIDs = new ArrayList<String>();

                        for(int i = 0; i < jsonResultFromServer.length(); i++)
                        {
                            LatLng aloc = new LatLng(jsonResultFromServer.getJSONObject(i).getDouble("lat"), jsonResultFromServer.getJSONObject(i).getDouble("lon"));
                            allCurrentItemLocations.add(aloc);
                            allCurrentKegIDs.add(jsonResultFromServer.getJSONObject(i).getString("kegID"));
                            Log.i("Boop Test", "BOOP KEG LOCATION LOADED " + aloc.toString());
                        }
                        Log.i("Location Update", "Recieved locations.");
                        updateMap(true);
                        mapText.setText("Receving Keg Locations");

                        break;


                    default: Log.e("Network Update", "PingingServerFor value does not match any known type"); break;
                }


            }
            else
            {
                mapText.setText("Error: network unavaiable");
            }

        }
        catch(JSONException e)
        {
            Log.e("DownloadUpdate Error", "JSONEception: " + e);
        }



        Log.e("Download Output", "" + result);
        // Update your UI here based on result of download.
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
        pingingServerFor = pingingServerFor_Nothing;
        Log.i("Network Update", "finished Downloading");
        if (aNetworkFragment != null) {
            Log.e("Network Update", "network fragment found, canceling download");
            aNetworkFragment.cancelDownload();
        }
    }

//**********[/Location Update and server pinging Code]

    class mapScrolledListener implements GoogleMap.OnCameraIdleListener
    {
        @Override
        public void onCameraIdle()
        {
            LatLng viewportCentre = mMap.getCameraPosition().target;

            Location a = new Location("");
            a.setLatitude(mMap.getProjection().getVisibleRegion().latLngBounds.northeast.latitude);
            a.setLongitude(mMap.getProjection().getVisibleRegion().latLngBounds.northeast.longitude);
            Location b = new Location("");
            b.setLatitude(viewportCentre.latitude);
            b.setLongitude(viewportCentre.longitude);
            double viewportRadius = a.distanceTo(b);

            //[JURY RIGGED irregular distances between long and lat, solution]
            //TODO: Replace this system.
            viewportRadius = Math.max(Math.abs(a.getLatitude() - b.getLatitude()), Math.abs(a.getLongitude() - b.getLongitude()));
            //[JURY RIGGED irregular distances between long and lat, solution]

            //if the user is looking at the current last known locations of kegs then do nothing, as ALL keg locations are retrieve when the user selects that option. else retrieve the locations within viewport.
            if(!(currentItemID.matches("Current Keg Locations") || currentItemID.matches("Keg Deliveries Between")))
            {
                retrieveLocations(viewportCentre, viewportRadius);
            }
        }
    }
}















/*

 */