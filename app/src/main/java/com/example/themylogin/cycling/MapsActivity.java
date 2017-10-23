package com.example.themylogin.cycling;

import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONObject;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;

    private Marker me;
    private Marker he;

    private Queue<String> outputQueue = new ConcurrentLinkedQueue<String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        final Queue<String> outputQueue = this.outputQueue;

        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                try {
                    JSONObject data = new JSONObject();
                    data.put("latitude", location.getLatitude());
                    data.put("longitude", location.getLongitude());
                    outputQueue.add(data.toString());

                    me.setPosition(new LatLng(location.getLatitude(), location.getLongitude()));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION}, 0);
            return;
        }
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, locationListener);

        Thread networkThread = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        Socket s = new Socket();
                        s.connect(new InetSocketAddress("192.168.4.1", 2228), 5000);
                        s.setSoTimeout(5000);
                        BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()));
                        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(s.getOutputStream()));
                        while (true) {
                            if (in.ready()) {
                                String input = in.readLine();

                                try {
                                    JSONObject data = new JSONObject(input);
                                    final double latitude = data.getDouble("latitude");
                                    final double longitude = data.getDouble("longitude");

                                    Handler mainHandler = new Handler(Looper.getMainLooper());
                                    Runnable myRunnable = new Runnable() {
                                        @Override
                                        public void run() {
                                            he.setPosition(new LatLng(latitude, longitude));
                                        }
                                    };
                                    mainHandler.post(myRunnable);
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            String value = outputQueue.poll();
                            if (value != null) {
                                out.write(value.concat("\n"));
                                out.flush();
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        networkThread.start();
    }


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
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        me = mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Me"));
        he = mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("He"));
    }
}
