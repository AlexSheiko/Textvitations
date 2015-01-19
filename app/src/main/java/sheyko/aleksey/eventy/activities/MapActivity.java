package sheyko.aleksey.eventy.activities;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.content.SharedPreferences.Editor;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GooglePlayServicesClient;
import com.google.android.gms.location.LocationClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.IOException;
import java.util.List;

import sheyko.aleksey.eventy.R;

public class MapActivity extends Activity implements GoogleMap.OnMapClickListener, GoogleMap.OnMyLocationButtonClickListener, GooglePlayServicesClient.ConnectionCallbacks, GooglePlayServicesClient.OnConnectionFailedListener {

    public static final int DEFAULTZOOM = 13;

    private GoogleMap map;
    private Button confirmButton;
    private EditText locationField;

    private String street;
    private String city;

    SharedPreferences sharedPref;
    SharedPreferences.Editor editor;

    private double lat;
    private double lng;

    private LocationClient mLocationClient;
    private Location mCurrentLocation;
    private final static int
            CONNECTION_FAILURE_RESOLUTION_REQUEST = 9000;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);


        sharedPref = getSharedPreferences(
                getString(R.string.preference_file_key), Context.MODE_APPEND);

        street = sharedPref.getString("street", null);
        city = sharedPref.getString("city", null);


        confirmButton = (Button) findViewById(R.id.confirmLocationButton);
        locationField = (EditText) findViewById(R.id.locationField);

        map = ((MapFragment) getFragmentManager()
                .findFragmentById(R.id.map)).getMap();

        map.setMyLocationEnabled(true);
        map.setOnMapClickListener(this);
        map.setOnMyLocationButtonClickListener(this);


        if (street != null && city != null) {
            locationField.setText(street + " " + city);


            Geocoder gc = new Geocoder(this);
            List<Address> list;
            try {
                list = gc.getFromLocationName(street + " " + city, 1);
                Address add = list.get(0);

                lat = add.getLatitude();
                lng = add.getLongitude();

                goToLocation(lat, lng, DEFAULTZOOM);

                confirmButton.setVisibility(View.VISIBLE);

                map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), DEFAULTZOOM));

            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            lat = -33.867;
            lng = 151.206;

            LatLng sydney = new LatLng(-33.867, 151.206);
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(sydney, DEFAULTZOOM));
            return;
        }

        locationField.setOnKeyListener(new View.OnKeyListener() {
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if ((keyCode == KeyEvent.KEYCODE_ENTER)) {
                    searchForAddress(v);
                    return true;
                }
                return false;
            }
        });
    }


    public void onMapClick(final LatLng point) {

        hideSoftKeyBoard(locationField);

        map.clear();

        map.addMarker(new MarkerOptions()
                .title("Event")
                .position(point));

        map.animateCamera(CameraUpdateFactory.newLatLng(point));

        Geocoder gc = new Geocoder(this);
        List<Address> list;
        try {
            list = gc.getFromLocation(point.latitude, point.longitude, 1);
            Address add = list.get(0);

            String street = add.getAddressLine(0);
            String city = add.getAddressLine(1);

            locationField.setText(street + " " + city);

            confirmButton.setVisibility(View.VISIBLE);

            editor = sharedPref.edit();
            putDouble(editor, "latitude", lat);
            putDouble(editor, "longitude", lng);
            editor.putString("street", street);
            editor.putString("city", city);
            editor.putString("location", street + " " + city);
            editor.apply();

        } catch (Exception ignored) {
        }

    }

    public void searchForAddress(View view) {
        hideSoftKeyBoard(view);

        EditText et = (EditText) findViewById(R.id.locationField);
        String location = et.getText().toString();

        Geocoder gc = new Geocoder(this);
        List<Address> list;

        try {
            list = gc.getFromLocationName(location, 1);
            Address add = list.get(0);

            lat = add.getLatitude();
            lng = add.getLongitude();


            confirmButton.setVisibility(View.VISIBLE);

            String street = add.getAddressLine(0);
            String city = add.getAddressLine(1);

            editor = sharedPref.edit();
            putDouble(editor, "latitude", lat);
            putDouble(editor, "longitude", lng);
            editor.putString("street", street);
            editor.putString("city", city);
            editor.putString("location", street + " " + city);
            editor.apply();

            goToLocation(lat, lng, DEFAULTZOOM);
        } catch (Exception e) {
            Toast.makeText(MapActivity.this, "No results found for " + location, Toast.LENGTH_SHORT).show();
        }


    }

    Editor putDouble(final Editor edit, final String key, final double value) {
        return edit.putLong(key, Double.doubleToRawLongBits(value));
    }

    private void goToLocation(double lat, double lng, int zoom) {

        map.moveCamera(CameraUpdateFactory.newLatLngZoom(new LatLng(lat, lng), zoom));

        map.clear();

        map.addMarker(new MarkerOptions()
                .title("Event")
                .position(new LatLng(lat, lng)));


        Geocoder gc = new Geocoder(this);
        List<Address> list;
        try {
            list = gc.getFromLocation(lat, lng, 1);
            Address add = list.get(0);

            String street = add.getAddressLine(0);
            String city = add.getAddressLine(1);

            locationField.setText(street + " " + city);

            confirmButton.setVisibility(View.VISIBLE);

            editor = sharedPref.edit();
            putDouble(editor, "latitude", lat);
            putDouble(editor, "longitude", lng);
            editor.putString("street", street);
            editor.putString("city", city);
            editor.putString("location", street + " " + city);
            editor.apply();

        } catch (Exception ignored) {
        }

        confirmButton.setVisibility(View.VISIBLE);

    }

    private void hideSoftKeyBoard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public void confirmLocation(View view) {

        startActivity(new Intent(MapActivity.this, InputActivity.class));

    }

    @Override
    public boolean onMyLocationButtonClick() {

        LocationManager lm;
        boolean gps_enabled = false;

        lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

        try {
            gps_enabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
        } catch (Exception ignored) {
        }

        if (!gps_enabled) {

            AlertDialog.Builder dialog = new AlertDialog.Builder(this);
            dialog.setTitle("Access to my location");
            dialog.setMessage("Please enable both Google's location service and GPS satellites in Settings.");
            dialog.setIcon(R.drawable.ic_action_place_light);
            dialog.setPositiveButton("Open settings", new DialogInterface.OnClickListener() {

                @Override
                public void onClick(DialogInterface paramDialogInterface, int paramInt) {
                    // Open GPS settings
                    Intent myIntent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
                    startActivity(myIntent);
                }
            });

            dialog.show();

            return false;
        }

        mLocationClient = new LocationClient(this, this, this);
        mLocationClient.connect();

        return false;
    }

    @Override
    public void onConnected(Bundle bundle) {

        mCurrentLocation = mLocationClient.getLastLocation();

        if (mCurrentLocation != null) {
            lat = mCurrentLocation.getLatitude();
            lng = mCurrentLocation.getLongitude();

            goToLocation(lat, lng, DEFAULTZOOM);
        } else {
            Toast.makeText(this, "Please wait while searching for GPS", Toast.LENGTH_SHORT).show();
        }

    }


    @Override
    public void onDisconnected() {
        Toast.makeText(this, "Disconnected. Please re-connect.",
                Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onConnectionFailed(ConnectionResult connectionResult) {
    /*
    /*
         * Google Play services can resolve some errors it detects.
         * If the error has a resolution, try sending an Intent to
         * start a Google Play services activity that can resolve
         * error.
         */
        if (connectionResult.hasResolution()) {
            try {
                // Start an Activity that tries to resolve the error
                connectionResult.startResolutionForResult(
                        this,
                        CONNECTION_FAILURE_RESOLUTION_REQUEST);
                /*
                 * Thrown if Google Play services canceled the original
                 * PendingIntent
                 */
            } catch (IntentSender.SendIntentException e) {
                // Log the error
                e.printStackTrace();
            }
        }
    }


    @Override
    protected void onStop() {
        // Disconnecting the client invalidates it.
        if (mLocationClient != null) {
            mLocationClient.disconnect();
        }
        super.onStop();
    }

}

