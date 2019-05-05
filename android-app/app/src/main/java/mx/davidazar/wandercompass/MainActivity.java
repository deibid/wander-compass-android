package mx.davidazar.wandercompass;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.SettingsClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;
import static android.bluetooth.BluetoothAdapter.STATE_CONNECTING;
import static android.bluetooth.BluetoothAdapter.STATE_DISCONNECTED;


public class MainActivity extends AppCompatActivity implements View.OnClickListener{




    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;



    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mGatt;
    private boolean mDeviceConnected = false;
    private Handler mHandler;

    private Context mContext;


    private static final long SCAN_PERIOD = 5000;


    private TextView locationTv;
    private TextView updatesTv;
    private Button scanBt;
    private TextView statusTv;
    private TextView serverStatusTv;
    private Button writeCharacteristicLeft;
    private Button writeCharacteristicStraight;
    private Button writeCharacteristicRight;
    private Button gpsToggleBt;
    private int locationUpdates = 0;
    private boolean mTrackingLocation = false;

    private Socket mSocket;
    private static final String URL = "https://wander-compass.herokuapp.com/";
    private static final String EVENT_SEND_DIRECTIONS = "send-directions";

    private static final int LOCATION_PERMISSION_REQUEST = 0;
    private static final int REQUEST_ENABLE_INTENT = 1;


    //MKR 1010 Version
//    private static final UUID WANDER_COMPASS_UUID = UUID.fromString("19b10010-e8f2-537e-4f6c-d104768a1214");
//    private static final UUID WANDER_COMPASS_DIRECTION_CHARACTERISTIC_UUID = UUID.fromString("19b10013-e8f2-537e-4f6c-d104768a1214");
//    private static final String WANDER_COMPASS_NAME = "Wander Compass";

    //RedBear Nano V2 Version
    private static final UUID WANDER_COMPASS_UUID = UUID.fromString("713d0000-503e-4c75-ba94-3148f18d941e");
    private static final UUID WANDER_COMPASS_DIRECTION_CHARACTERISTIC_UUID = UUID.fromString("713d0002-503e-4c75-ba94-3148f18d941e");
    private static final String WANDER_COMPASS_NAME = "WanderCompass";




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        initializeSocket();
        initializeBluetooth();


        mContext = this;
        mHandler = new Handler();


        locationTv = findViewById(R.id.locationTv);
        updatesTv = findViewById(R.id.locationUpdatesTv);

        scanBt = findViewById(R.id.scanBt);
        scanBt.setOnClickListener(this);


        statusTv = findViewById(R.id.scanStatusTv);

        serverStatusTv = findViewById(R.id.serverStatus);


        writeCharacteristicLeft = findViewById(R.id.writeCharacteristicLeft);
        writeCharacteristicLeft.setOnClickListener(this);

        writeCharacteristicStraight = findViewById(R.id.writeCharacteristicStraight);
        writeCharacteristicStraight.setOnClickListener(this);

        writeCharacteristicRight = findViewById(R.id.writeCharacteristicRight);
        writeCharacteristicRight.setOnClickListener(this);

        gpsToggleBt = findViewById(R.id.locationBt);
        gpsToggleBt.setOnClickListener(this);





        createLocationRequest();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        locationCallback = new LocationCallback(){

            @Override
            public void onLocationResult(LocationResult locationResult) {
                if(locationResult == null){
                    Log.d("Location Result","No location");
                    return;
                }

                for(Location location : locationResult.getLocations()){
                    Log.d("Location Result Loop","Recibi Locations #"+locationResult.getLocations().size());
                    Log.d("Location Result loop",location.toString());
                    double lng = location.getLongitude();
                    double lat = location.getLatitude();
                    String msg = formatDouble(lng)+" , "+formatDouble(lat);
                    locationTv.setText(msg);
                    sendLocationToServer(lng,lat);
                }

                locationUpdates++;
                updatesTv.setText(String.valueOf(locationUpdates));


            }

        };


    }




    private void startLocationUpdates(){

        mTrackingLocation = true;
        gpsToggleBt.setText(R.string.stop_gps_tracking);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.d("Perm","Not Granted");
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST);

        }else{
            fusedLocationClient.requestLocationUpdates(locationRequest,locationCallback,null);
            Log.d("Perm","Permission granted");
        }


    }


    private void stopLocationUpdates(){
        mTrackingLocation = true;
        gpsToggleBt.setText(R.string.start_gps_tracking);
        locationTv.setText("");
        updatesTv.setText("");
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }



    protected void createLocationRequest(){

        locationRequest = LocationRequest.create();
        locationRequest.setInterval(2000);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);


        LocationSettingsRequest.Builder settingBuilder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

        SettingsClient client = LocationServices.getSettingsClient(this);
        Task <LocationSettingsResponse> task = client.checkLocationSettings(settingBuilder.build());


        task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
            @Override
            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
                Log.d("Task Settings","Success");
            }
        });


        task.addOnFailureListener(this, new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {

                if (e instanceof ResolvableApiException) {
                    try{
                        ResolvableApiException resolvable = (ResolvableApiException) e;
                        resolvable.startResolutionForResult(MainActivity.this, 0);
                    }catch( IntentSender.SendIntentException sendEx){
                    }
                }
            }
        });
    }



    private void initializeSocket(){

        Log.d("socket", "init");

        try{


            mSocket = IO.socket(URL);
            mSocket.connect();


            mSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    Log.d("Socket","CONNECTED");
                    serverStatusTv.setText(R.string.server_connected);
                }
            }).on(EVENT_SEND_DIRECTIONS, new Emitter.Listener() {
                @Override
                public void call(Object... args) {


//                    Log.d("Recibí mesaje", args.toString());
                    JSONObject obj = (JSONObject)args[0];
//                    Log.d("Recibí objeto", obj.toString());

                    try{
                        String commandStr = obj.getString("to");
                        int command = Integer.parseInt(commandStr);

                        writeLECharacteristic(command);
                    }catch(JSONException jsone){
                        Log.w("Error",jsone.getMessage());
                    }


                }
            }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    Log.d("Socket", "Disconneted");
                    serverStatusTv.setText(R.string.server_disconnected);
                }
            });

        }catch(URISyntaxException e){
            Log.d("Error",e.toString());
        }

    }

    private void sendLocationToServer(double lng,double lat){


        try{

            JSONObject obj = new JSONObject();
            obj.put("lng",lng);
            obj.put("lat",lat);

            Log.d("Socket","Sending location to server->  \n"+obj.toString());
            mSocket.emit("new-location-from-phone", obj);

        }catch(JSONException je){
            Log.d("error with JSON", je.toString());
        }

    }



    private void initializeBluetooth(){


        final BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();


        if(mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()){
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTIntent,REQUEST_ENABLE_INTENT);
        }





    }



    private void startLEScan(){


        final BluetoothLeScanner scanner = mBluetoothAdapter.getBluetoothLeScanner();

        List<ScanFilter> leScanFilter = Arrays.asList(new ScanFilter[]{
                        new ScanFilter.Builder()
                                .setDeviceName(WANDER_COMPASS_NAME)
                                .build()
                });

        ScanSettings.Builder builderScanSettings = new ScanSettings.Builder();
        builderScanSettings.setScanMode(ScanSettings.SCAN_MODE_BALANCED);
        builderScanSettings.setReportDelay(0);


//        Log.d("Bluetooth","Scanning...");
        statusTv.setText(R.string.scanning);


        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scanner.stopScan(mLeScanCallback);
                scanBt.setEnabled(true);
                statusTv.setText(R.string.ble_standby);

            }
        },SCAN_PERIOD);

        scanner.startScan(leScanFilter,builderScanSettings.build(),mLeScanCallback);

        scanBt.setEnabled(false);



    }



    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            statusTv.setText("Scan Successful");
            Log.d("Wander Compass","Lo tengo");
            mGatt = result.getDevice().connectGatt(mContext,true, mGattCallback);

        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
//            statusTv.setText("Results go here");
//            Log.d("Bluetooth Scan",results.toString());
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            statusTv.setText("Scan Error");

        }

    };


    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if(newState == STATE_CONNECTED){
                mDeviceConnected = true;
                mGatt.discoverServices();
                statusTv.setText("Connected!");

                writeCharacteristicLeft.setEnabled(true);
                writeCharacteristicStraight.setEnabled(true);
                writeCharacteristicRight.setEnabled(true);


            }

            if(newState == STATE_CONNECTING){
                statusTv.setText("Connecting...");
            }

            if(newState == STATE_DISCONNECTED){
                mDeviceConnected = false;
                statusTv.setText(R.string.ble_standby);
                writeCharacteristicLeft.setEnabled(false);
                writeCharacteristicStraight.setEnabled(false);
                writeCharacteristicRight.setEnabled(false);
            }
        }

//        @Override
//        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
//            super.onServicesDiscovered(gatt, status);
//
//            BluetoothGattCharacteristic characteristic =
//                    gatt.getService(WANDER_COMPASS_UUID).getCharacteristic(WANDER_COMPASS_DIRECTION_CHARACTERISTIC_UUID);
//
//        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d("Wander Compass", "onCharWrite full circle");
        }
    };


    private void writeLECharacteristic(int number){

        if(!mDeviceConnected)return;

        BluetoothGattCharacteristic characteristic =
                mGatt.getService(WANDER_COMPASS_UUID).getCharacteristic(WANDER_COMPASS_DIRECTION_CHARACTERISTIC_UUID);


        characteristic.setValue(number,BluetoothGattCharacteristic.FORMAT_UINT8,0);
        mGatt.writeCharacteristic(characteristic);

    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode){
            case REQUEST_ENABLE_INTENT:
                Toast.makeText(this,"Bluetooth enabled",Toast.LENGTH_LONG);
                break;
        }

    }


    @Override
    public void onClick(View v) {

        switch (v.getId()){

            case R.id.scanBt:
                startLEScan();
                break;

            case R.id.writeCharacteristicLeft:
                writeLECharacteristic(0);
                break;

            case R.id.writeCharacteristicStraight:
                writeLECharacteristic(1);
                break;

            case R.id.writeCharacteristicRight:
                writeLECharacteristic(2);
                break;

            case R.id.locationBt:

                if(!mTrackingLocation){
                    updatesTv.setText(R.string.getting_gps_location);
                    startLocationUpdates();
                }else{
                    stopLocationUpdates();
                }

                break;

        }


    }




    private String formatDouble(double d){

        DecimalFormat df = new DecimalFormat("#.######");
        return df.format(d);


    }

}
