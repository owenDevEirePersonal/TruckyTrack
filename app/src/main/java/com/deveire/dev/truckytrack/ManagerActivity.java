package com.deveire.dev.truckytrack;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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

import org.json.JSONArray;
import org.json.JSONException;

import java.util.ArrayList;

public class ManagerActivity extends FragmentActivity implements AdapterView.OnItemSelectedListener, OnMapReadyCallback, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks, DownloadCallback<String>
{

    private GoogleMap mMap;

    private TextView mapText;
    private Spinner itemsSpinner;
    private EditText countText;

    private ArrayList<String> itemIdsFromServer;
    private String currentItemID;
    private int numberOfResultsToRetrieve;
    private ArrayList<LatLng> allCurrentItemLocations;

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
    private final int pingingServerFor_Nothing = 0;


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
        countText = (EditText) findViewById(R.id.editText);
        countText.setText("" + numberOfResultsToRetrieve);


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

    Runnable periodicRefresher = new Runnable()
    {
        @Override
        public void run()
        {
            try
            {
                Log.i("Network Update", "Launching Refresh ");
                //aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), "https://192.168.1.188:8080/smrttrackerserver-1.0.0-SNAPSHOT/hello?isDoomed=yes");

                serverURL = "http://192.168.1.188:8080/TruckyTrackServlet/TTServlet?request=getitemids";
                pingingServerFor = pingingServerFor_ItemIds;
                aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);

                //loadTestIDs();
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

        // Add a marker in Sydney and move the camera
        mMap.addMarker(new MarkerOptions().position(new LatLng(userLocation.getLatitude(), userLocation.getLongitude())).title("You"));
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

        for (LatLng aloc: allCurrentItemLocations)
        {
            mMap.addMarker(new MarkerOptions().position(aloc).title("Truck 1"));
            Log.i("Map Update", "Placing Marker at " + aloc.toString());
        }
        mMap.addMarker(new MarkerOptions().position(new LatLng(userLocation.getLatitude(), userLocation.getLongitude())).title("You"));

    }



    private void retrieveLocations()
    {

        pingingServerFor = pingingServerFor_Locations;
        serverURL = "http://192.168.1.188:8080/TruckyTrackServlet/TTServlet?request=getinitlocations&id=" + (currentItemID.split(" - ")[0]) + "&count=" + numberOfResultsToRetrieve;
        //lat and long are doubles, will cause issue? nope
        Log.i("Network Update", "Attempting to start download from onLocationChanged." + serverURL);
        aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);

        Log.i("Dropdown Update", "Dropdown select, Loading intial locations for " + (currentItemID.split(" - ")[0]));
        //loadTestLocations(numberOfResultsToRetrieve);
    }

    private void retrieveLocations(LatLng centreOfMap, double radiusInMetres)
    {

        pingingServerFor = pingingServerFor_Extra_Locations;
        serverURL = "http://192.168.1.188:8080/TruckyTrackServlet/TTServlet?request=getlocationswithin&id=" + (currentItemID.split(" - ")[0]) + "&lat=" + centreOfMap.latitude + "&lon=" + centreOfMap.longitude + "&radius=" + radiusInMetres;
        //lat and long are doubles, will cause issue? nope
        Log.i("Network Update", "Attempting to start download to retrieve locations." + serverURL);
        aNetworkFragment = NetworkFragment.getInstance(getSupportFragmentManager(), serverURL);

        Log.i("Dropdown Update", "Map Moved, Loading new locations for " + (currentItemID.split(" - ")[0]) + " from centre " + centreOfMap.toString() + " at radius " + radiusInMetres);
        //loadMoreTestLocations(centreOfMap, radiusInMetres);
    }

    private void loadTestIDs()
    {
        /*itemIdsFromServer = new ArrayList<ItemIDs>();
        itemIdsFromServer.add(new ItemIDs(1, "Truck 1"));
        itemIdsFromServer.add(new ItemIDs(2, "Truck 2"));
        itemIdsFromServer.add(new ItemIDs(3, "Trucky Trailer"));

        ArrayList<String> itemNames = new ArrayList<String>();
        for(int i = 0; i < itemIdsFromServer.size(); i++)
        {
            itemNames.add(itemIdsFromServer.get(i).getName());
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(ManagerActivity.this, android.R.layout.simple_spinner_dropdown_item, itemNames);
        itemsSpinner.setAdapter(adapter);

        int currentIndexOfDropdown = 0;
        for (String aID : itemNames)
        {
            if (aID.matches(currentItemID.getName()))
            {
               itemsSpinner.setSelection(currentIndexOfDropdown);
               break;
            }
            currentIndexOfDropdown++;
        }*/
    }

    private void loadTestLocations(int count)
    {
        /*double firstLocationLat = 52.663585;
        double firstLocationLng= -8.636135;
        allCurrentItemLocations = new ArrayList<LatLng>();
        switch (currentItemID)
        {
            case "Truck 1":
                for (int i = 1; i <= count; i++)
                {
                    allCurrentItemLocations.add(new LatLng(firstLocationLat + 0.1 * i, firstLocationLng + 0.1 * i));
                }
                testStoreOfLocations = new ArrayList<LatLng>();
                testStoreOfLocations = (ArrayList<LatLng>) allCurrentItemLocations.clone();
                LatLng lastLoc = allCurrentItemLocations.get(allCurrentItemLocations.size() - 1);
                testStoreOfLocations.add(new LatLng(lastLoc.latitude + 1.1, lastLoc.longitude + 0.1));
                testStoreOfLocations.add(new LatLng(lastLoc.latitude + 2.2, lastLoc.longitude + 0.2));
                testStoreOfLocations.add(new LatLng(lastLoc.latitude + 3.3, lastLoc.longitude + 0.3));
                testStoreOfLocations.add(new LatLng(lastLoc.latitude + 4.4, lastLoc.longitude + 0.4));
                testStoreOfLocations.add(new LatLng(lastLoc.latitude + 5.5, lastLoc.longitude + 0.5));
                testStoreOfLocations.add(new LatLng(lastLoc.latitude + 6.6, lastLoc.longitude + 0.6));
            break;

            case "Truck 2":
                for (int i = 1; i <= count; i++)
                {
                    allCurrentItemLocations.add(new LatLng(firstLocationLat - 0.1 * i, firstLocationLng + 0.1 * i));
                }
                testStoreOfLocations = new ArrayList<LatLng>();
                testStoreOfLocations = (ArrayList<LatLng>) allCurrentItemLocations.clone();
                LatLng lastLoc2 = allCurrentItemLocations.get(allCurrentItemLocations.size() - 1);
                testStoreOfLocations.add(new LatLng(lastLoc2.latitude - 1.1, lastLoc2.longitude + 0.1));
                testStoreOfLocations.add(new LatLng(lastLoc2.latitude - 2.2, lastLoc2.longitude + 0.2));
                testStoreOfLocations.add(new LatLng(lastLoc2.latitude - 3.3, lastLoc2.longitude + 0.3));
                testStoreOfLocations.add(new LatLng(lastLoc2.latitude - 4.4, lastLoc2.longitude + 0.4));
                testStoreOfLocations.add(new LatLng(lastLoc2.latitude - 5.5, lastLoc2.longitude + 0.5));
                testStoreOfLocations.add(new LatLng(lastLoc2.latitude - 6.6, lastLoc2.longitude + 0.6));
            break;

            case "Trucky Trailer":
                for (int i = 1; i <= count; i++)
                {
                    allCurrentItemLocations.add(new LatLng(firstLocationLat - 0.1 * i, firstLocationLng - 0.1 * i));
                }
                testStoreOfLocations = new ArrayList<LatLng>();
                testStoreOfLocations = (ArrayList<LatLng>) allCurrentItemLocations.clone();
                LatLng lastLoc3 = allCurrentItemLocations.get(allCurrentItemLocations.size() - 1);
                testStoreOfLocations.add(new LatLng(lastLoc3.latitude - 1.1, lastLoc3.longitude - 0.1));
                testStoreOfLocations.add(new LatLng(lastLoc3.latitude - 2.2, lastLoc3.longitude - 0.2));
                testStoreOfLocations.add(new LatLng(lastLoc3.latitude - 3.3, lastLoc3.longitude - 0.3));
                testStoreOfLocations.add(new LatLng(lastLoc3.latitude - 4.4, lastLoc3.longitude - 0.4));
                testStoreOfLocations.add(new LatLng(lastLoc3.latitude - 5.5, lastLoc3.longitude - 0.5));
                testStoreOfLocations.add(new LatLng(lastLoc3.latitude - 6.6, lastLoc3.longitude - 0.6));
                break;
        }

        Log.i("Update Map", "Update Map from Intial location loading");
        updateMap(true);
        */
    }

    private void loadMoreTestLocations(LatLng centre, double radius)
    {
        if(allCurrentItemLocations.size() > 0)
        {
            Location locCentre = new Location("");
            locCentre.setLatitude(centre.latitude);
            locCentre.setLongitude(centre.longitude);

            float[] distanceBetween = new float[1];
            for (LatLng aloc : testStoreOfLocations)
            {
                Location test = new Location("");
                test.setLatitude(aloc.latitude);
                test.setLongitude(aloc.longitude);
                Location.distanceBetween(centre.latitude, centre.longitude, aloc.latitude, aloc.longitude, distanceBetween);
                Log.i("LoadUpdate", "distancebetween point and centre is " + test.distanceTo(locCentre) + " against radius of " + radius);
                if (test.distanceTo(locCentre) < radius && !arrayContains(allCurrentItemLocations, aloc))
                {
                    allCurrentItemLocations.add(aloc);
                }
            }
            Log.i("Update Map", "Update Map from More location loading");
            updateMap(false);
        }

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
        //TODO: replace ID, name system with single string consiting of "id - name"
        //[/Get currently selected id from dropdown menu]

        Log.i("Dropdown Update", "Dropdown select, ItemID now equals " + parent.getItemAtPosition(pos));
        try
        {
            numberOfResultsToRetrieve = Integer.getInteger(countText.getText().toString());
        }
        catch (Exception e)
        {
            numberOfResultsToRetrieve = 10;
            countText.setText("" + numberOfResultsToRetrieve);
        }
        retrieveLocations();
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
            numberOfResultsToRetrieve = Integer.getInteger(countText.getText().toString());
        }
        catch (Exception e)
        {
            numberOfResultsToRetrieve = 10;
            countText.setText("" + numberOfResultsToRetrieve);
        }
        retrieveLocations();
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
                        //jsonResultFromServer = new JSONArray(result);
                        Log.i("Network JSON", "pingingServerFor_ItemIds, lets begin, shall we...");
                        itemIdsFromServer = new ArrayList<String>();
                        for(int i = 0; i < jsonResultFromServer.length(); i++)
                        {
                            itemIdsFromServer.add(jsonResultFromServer.getJSONObject(i).getString("name"));
                        }



                        ArrayAdapter<String> adapter = new ArrayAdapter<String>(ManagerActivity.this, android.R.layout.simple_spinner_dropdown_item, itemIdsFromServer);
                        itemsSpinner.setAdapter(adapter);

                        break;

                    case pingingServerFor_Locations:
                        //jsonResultFromServer = new JSONArray(result);
                        allCurrentItemLocations = new ArrayList<LatLng>();
                        for(int i = 0; i < jsonResultFromServer.length(); i++)
                        {
                            LatLng aloc = new LatLng(jsonResultFromServer.getJSONObject(i).getDouble("lat"), jsonResultFromServer.getJSONObject(i).getDouble("lon"));
                            allCurrentItemLocations.add(aloc);
                            Log.i("Boop Test", "BOOP LOCATION LOADED" + aloc.toString());
                        }

                        Log.i("Location Update", "Recieved locations.");
                        updateMap(true);
                        mapText.setText("Receving Locations");

                        break;

                    case pingingServerFor_Extra_Locations:
                        //jsonResultFromServer = new JSONArray(result);
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

            retrieveLocations(viewportCentre, viewportRadius);
        }
    }
}















/*


 */