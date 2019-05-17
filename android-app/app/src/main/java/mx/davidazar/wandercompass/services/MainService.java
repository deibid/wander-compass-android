package mx.davidazar.wandercompass.services;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
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
import android.graphics.Color;
import android.location.Location;
import android.os.Handler;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.Log;

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

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URISyntaxException;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import mx.davidazar.wandercompass.MainActivity;
import mx.davidazar.wandercompass.R;
import mx.davidazar.wandercompass.events.BluetoothEvent;
import mx.davidazar.wandercompass.events.LocationEvent;
import mx.davidazar.wandercompass.events.ServerEvent;

import static android.bluetooth.BluetoothAdapter.STATE_CONNECTED;
import static android.bluetooth.BluetoothAdapter.STATE_CONNECTING;
import static android.bluetooth.BluetoothAdapter.STATE_DISCONNECTED;

public class MainService extends Service {


    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mGatt;
    private boolean mDeviceConnected = false;

    private int locationUpdates = 0;
    private boolean mTrackingLocation = false;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationRequest locationRequest;
    private LocationCallback locationCallback;

    private Socket mSocket;
    private static final String URL = "https://wander-compass.herokuapp.com/";
    private static final String EVENT_SEND_DIRECTIONS = "send-directions";

    private static final String TAG = "gps-service";

    private static final UUID WANDER_COMPASS_UUID = UUID.fromString("713d0000-503e-4c75-ba94-3148f18d941e");
    private static final UUID WANDER_COMPASS_DIRECTION_CHARACTERISTIC_UUID = UUID.fromString("713d0002-503e-4c75-ba94-3148f18d941e");
    private static final String WANDER_COMPASS_NAME = "WanderCompass";

    private static final int NOTIFICATION_ID = 1001;
    private static final long SCAN_PERIOD = 5000;
    private static final int LOCATION_TRACKING_INTERVAL = 1000;
    private static final int LOCATION_PERMISSION_REQUEST = 0;

    //MKR 1010 Version
//    private static final UUID WANDER_COMPASS_UUID = UUID.fromString("19b10010-e8f2-537e-4f6c-d104768a1214");
//    private static final UUID WANDER_COMPASS_DIRECTION_CHARACTERISTIC_UUID = UUID.fromString("19b10013-e8f2-537e-4f6c-d104768a1214");
//    private static final String WANDER_COMPASS_NAME = "Wander Compass";


    private Handler mHandler;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG,"onCreate");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG,"onStartCommand");

        mHandler = new Handler();

        EventBus.getDefault().register(this);
        initializeSocket();

        final BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();

        Notification notification = createNotification();
        startForeground(NOTIFICATION_ID,notification);


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
                    Log.d("Location Result Loop Service","Recibi Locations #"+locationResult.getLocations().size());
                    Log.d("Location Result loop",location.toString());
                    double lng = location.getLongitude();
                    double lat = location.getLatitude();
                    String msg = formatDouble(lng)+" , "+formatDouble(lat);


                    locationUpdates++;
                    LocationEvent le = new LocationEvent(msg,locationUpdates, LocationEvent.Event.LOCATION_RESULT);
                    EventBus.getDefault().post(le);
                    sendLocationToServer(lng,lat);
                }

            }

        };


        return START_STICKY;

    }


    @Override
    public void onDestroy() {
        Log.d(TAG,"onDestroy");
        EventBus.getDefault().unregister(this);
        super.onDestroy();
    }


    @Subscribe
    public void onEvent(BluetoothEvent bluetoothEvent){

        if(bluetoothEvent.getEvent() == BluetoothEvent.Event.START_SCAN){
            Log.d("Service","Bluetooth Event Start Scan");
            startLEScan();
        }

        if(bluetoothEvent.getEvent() == BluetoothEvent.Event.WRITE_COMMAND){
            writeLECharacteristic(bluetoothEvent.getCommand());
        }
    }

    @Subscribe
    public void onEvent(LocationEvent locationEvent){
        Log.d("Service","Location Event");
        if(locationEvent.getEvent() == LocationEvent.Event.TOGGLE_LOCATION_TRACKING){
            if(!mTrackingLocation) startLocationUpdates();
            else stopLocationUpdates();

        }
    }


    private Notification createNotification(){

        String NOTIFICATION_CHANNEL_ID = "mx.davidazar.wandercompass";
        String channelName = "GPS Service Channel";

        NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
        chan.setLightColor(Color.BLUE);
        chan.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        assert manager != null;
        manager.createNotificationChannel(chan);

        Notification notification = new Notification.Builder(this,NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Wander Compass is running")
                .setTicker("Wander Compass")
                .setContentText("Go explore...")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .build();

        return notification;

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
//        statusTv.setText(R.string.scanning);


        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                scanner.stopScan(mLeScanCallback);
                BluetoothEvent be = new BluetoothEvent(BluetoothEvent.Event.STAND_BY);
                EventBus.getDefault().post(be);
//                scanBt.setEnabled(true);
//                statusTv.setText(R.string.ble_standby);

            }
        },SCAN_PERIOD);

        scanner.startScan(leScanFilter,builderScanSettings.build(),mLeScanCallback);

        BluetoothEvent be = new BluetoothEvent(BluetoothEvent.Event.SCANNING);
        EventBus.getDefault().post(be);

//        scanBt.setEnabled(false);

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
                    ServerEvent se = new ServerEvent(ServerEvent.Status.CONNECTED);
                    EventBus.getDefault().post(se);

                }
            }).on(EVENT_SEND_DIRECTIONS, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    Log.d("Recibí mesaje en Service", args.toString());
                    JSONObject obj = (JSONObject)args[0];
                    try{

                        String commandStr = obj.getString("to");
                        int command = Integer.parseInt(commandStr);
                        writeLECharacteristic(command);

                    }catch(JSONException je){
                        Log.w("Error",je.getMessage());
                    }


                }
            }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    Log.d("Socket", "Disconneted");
                    ServerEvent se = new ServerEvent(ServerEvent.Status.DISCONNECTED);
                    EventBus.getDefault().post(se);
                }
            });

        }catch(URISyntaxException e){
            Log.d("Error",e.toString());
        }

    }

    private void startLocationUpdates(){

        mTrackingLocation = true;


//        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
//                != PackageManager.PERMISSION_GRANTED) {
//            Log.d("Perm","Not Granted");
//            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
//                    LOCATION_PERMISSION_REQUEST);
//
//        }else{
            fusedLocationClient.requestLocationUpdates(locationRequest,locationCallback,null);
            Log.d("Perm","Permission granted");
//        }


    }


    private void stopLocationUpdates(){
        mTrackingLocation = false;
        fusedLocationClient.removeLocationUpdates(locationCallback);
    }


    protected void createLocationRequest(){

        locationRequest = LocationRequest.create();
        locationRequest.setInterval(LOCATION_TRACKING_INTERVAL);
        locationRequest.setFastestInterval(1000);
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);


        LocationSettingsRequest.Builder settingBuilder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);

//        SettingsClient client = LocationServices.getSettingsClient(this);
//        Task<LocationSettingsResponse> task = client.checkLocationSettings(settingBuilder.build());


//        task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
//            @Override
//            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
//                Log.d("Task Settings","Success");
//            }
//        });


//        task.addOnFailureListener(this, new OnFailureListener() {
//            @Override
//            public void onFailure(@NonNull Exception e) {
//
//                if (e instanceof ResolvableApiException) {
//                    try{
//                        ResolvableApiException resolvable = (ResolvableApiException) e;
//                        resolvable.startResolutionForResult(MainActivity.this, 0);
//                    }catch( IntentSender.SendIntentException sendEx){
//                    }
//                }
//            }
//        });
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

    private ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);

            BluetoothEvent be = new BluetoothEvent(BluetoothEvent.Event.DEVICE_FOUND);
            EventBus.getDefault().post(be);

            mGatt = result.getDevice().connectGatt(getApplicationContext(),true, mGattCallback);

//            statusTv.setText("Scan Successful");
//            Log.d("Wander Compass","Lo tengo");

        }


        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);

            BluetoothEvent be = new BluetoothEvent(BluetoothEvent.Event.SCAN_ERROR);
            EventBus.getDefault().post(be);
        }

    };

    private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            if(newState == STATE_CONNECTED){
                mDeviceConnected = true;
                mGatt.discoverServices();

                BluetoothEvent be = new BluetoothEvent(BluetoothEvent.Event.CONNECTED);
                EventBus.getDefault().post(be);

            }

            if(newState == STATE_CONNECTING){

                BluetoothEvent be = new BluetoothEvent(BluetoothEvent.Event.CONNECTING);
                EventBus.getDefault().post(be);

            }

            if(newState == STATE_DISCONNECTED){
                mDeviceConnected = false;

                BluetoothEvent be = new BluetoothEvent(BluetoothEvent.Event.DISCONNECTED);
                EventBus.getDefault().post(be);
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);
            Log.d("Wander Compass", "onCharWrite full circle en Service");
        }
    };


    private void writeLECharacteristic(int number){

        if(!mDeviceConnected)return;

        BluetoothGattCharacteristic characteristic =
                mGatt.getService(WANDER_COMPASS_UUID).getCharacteristic(WANDER_COMPASS_DIRECTION_CHARACTERISTIC_UUID);


        characteristic.setValue(number,BluetoothGattCharacteristic.FORMAT_UINT8,0);
        mGatt.writeCharacteristic(characteristic);

    }


    private String formatDouble(double d){

        DecimalFormat df = new DecimalFormat("#.######");
        return df.format(d);


    }




}
