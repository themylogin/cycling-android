package com.example.themylogin.cycling;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
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

import java.io.ByteArrayOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONObject;

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;

    private Marker me;
    private Marker he;

    private Queue<byte[]> outputQueue = new ConcurrentLinkedQueue<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        final Queue<byte[]> outputQueue = this.outputQueue;

        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                try {
                    byte[] latitude = ByteBuffer.allocate(4).putFloat((float)location.getLatitude()).array();
                    byte[] longitude = ByteBuffer.allocate(4).putFloat((float)location.getLatitude()).array();
                    byte[] speed = ByteBuffer.allocate(4).putFloat(location.getSpeed()).array();
                    outputQueue.add(new byte[]{
                            0x01,                   // Send packet
                            0x1, 0x00, 0x00, 0x00,  // Network #1
                            0x00,                   // Location & speed
                            (byte)latitude[0],
                            (byte)latitude[1],
                            (byte)latitude[2],
                            (byte)latitude[3],
                            (byte)longitude[0],
                            (byte)longitude[1],
                            (byte)longitude[2],
                            (byte)longitude[3],
                            (byte)speed[0],
                            (byte)speed[1],
                            (byte)speed[2],
                            (byte)speed[3],
                    });

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

        Thread bluetoothThread = new Thread(new Runnable() {
            public void run() {
                while (true) {
                    try {
                        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

                        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
                        BluetoothDevice device = null;
                        for (BluetoothDevice pairedDevice : pairedDevices) {
                            if (pairedDevice.getName().startsWith("VPU "))
                            {
                                device = pairedDevice;
                                break;
                            }
                        }
                        if (device == null)
                        {
                            throw new Exception("No paired VPU found");
                        }

                        String deviceAddress[] = device.getAddress().split(":");


                        Method m = device.getClass().getMethod("createInsecureRfcommSocket", new Class[] { int.class });
                        BluetoothSocket s = (BluetoothSocket)m.invoke(device, Integer.valueOf(1));
                        s.connect();
                        try {
                            InputStream in = s.getInputStream();
                            OutputStream out = (s.getOutputStream());

                            out.write(new byte[]{
                                    0x00, // Set networks
                                    0x1, 0x00, 0x00, 0x00, // Network #1
                                    (byte)Integer.parseInt(deviceAddress[2], 16),
                                        (byte)Integer.parseInt(deviceAddress[3], 16),
                                        (byte)Integer.parseInt(deviceAddress[4], 16),
                                        (byte)Integer.parseInt(deviceAddress[5], 16),
                                        // Device address #1
                            });
                            out.flush();

                            while (true) {
                                if (in.available() > 0) {
                                    in.mark(258);
                                    int size;
                                    byte packet[] = new byte[256];
                                    while (true)
                                    {
                                        byte c_size[] = new byte[2];
                                        if (in.read(c_size, 0, 2) != 2)
                                        {
                                            in.reset();
                                            continue;
                                        }
                                        size = (int)c_size[0] + (int)c_size[1] * 256;
                                        if (in.read(packet, 0, size) != size)
                                        {
                                            in.reset();
                                            continue;
                                        }
                                        break;
                                    }

                                    ByteBuffer buffer = ByteBuffer.wrap(packet);
                                    buffer.order(ByteOrder.LITTLE_ENDIAN);

                                    byte btPacketTypeBuf[] = new byte[1];
                                    buffer.get(btPacketTypeBuf, 0, 1);
                                    if (btPacketTypeBuf[0] == 0) {
                                        int src = buffer.getInt();

                                        byte packetTypeBuf[] = new byte[1];
                                        buffer.get(packetTypeBuf, 0, 1);
                                        if (packetTypeBuf[0] == 0) {
                                            final float latitude = buffer.getFloat();
                                            final float longitude = buffer.getFloat();
                                            float speed = buffer.getFloat();

                                            Handler mainHandler = new Handler(Looper.getMainLooper());
                                            Runnable myRunnable = new Runnable() {
                                                @Override
                                                public void run() {
                                                    he.setPosition(new LatLng(latitude, longitude));
                                                }
                                            };
                                            mainHandler.post(myRunnable);
                                        }
                                    }
                                }

                                byte value[] = outputQueue.poll();
                                if (value != null) {
                                    out.write(value);
                                    out.flush();
                                }
                            }
                        } finally {
                            s.close();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        bluetoothThread.start();
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
