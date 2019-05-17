package mx.davidazar.wandercompass;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.Intent;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;


import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import mx.davidazar.wandercompass.events.BluetoothEvent;
import mx.davidazar.wandercompass.events.LocationEvent;
import mx.davidazar.wandercompass.events.ServerEvent;
import mx.davidazar.wandercompass.services.MainService;


public class MainActivity extends AppCompatActivity implements View.OnClickListener{




//    private FusedLocationProviderClient fusedLocationClient;
//    private LocationRequest locationRequest;
//    private LocationCallback locationCallback;



    private BluetoothAdapter mBluetoothAdapter;
//    private BluetoothGatt mGatt;
//    private boolean mDeviceConnected = false;
//    private Handler mHandler;
//
//    private Context mContext;
//
//
//    private static final long SCAN_PERIOD = 5000;


    private TextView locationTv;
    private TextView updatesTv;
    private Button scanBt;
    private TextView statusTv;
    private TextView serverStatusTv;
    private Button writeCharacteristicLeft;
    private Button writeCharacteristicStraight;
    private Button writeCharacteristicRight;
    private Button gpsToggleBt;
//    private int locationUpdates = 0;
    private boolean mTrackingLocation = false;

//    private Socket mSocket;
//    private static final String URL = "https://wander-compass.herokuapp.com/";
//    private static final String EVENT_SEND_DIRECTIONS = "send-directions";
//
//    private static final int LOCATION_PERMISSION_REQUEST = 0;
    private static final int REQUEST_ENABLE_INTENT = 1;


    //MKR 1010 Version
//    private static final UUID WANDER_COMPASS_UUID = UUID.fromString("19b10010-e8f2-537e-4f6c-d104768a1214");
//    private static final UUID WANDER_COMPASS_DIRECTION_CHARACTERISTIC_UUID = UUID.fromString("19b10013-e8f2-537e-4f6c-d104768a1214");
//    private static final String WANDER_COMPASS_NAME = "Wander Compass";

    //RedBear Nano V2 Version
//    private static final UUID WANDER_COMPASS_UUID = UUID.fromString("713d0000-503e-4c75-ba94-3148f18d941e");
//    private static final UUID WANDER_COMPASS_DIRECTION_CHARACTERISTIC_UUID = UUID.fromString("713d0002-503e-4c75-ba94-3148f18d941e");
//    private static final String WANDER_COMPASS_NAME = "WanderCompass";




    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Prevent phone from sleeping when the Activity is open
//        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        initializeBluetooth();

        Intent gpsService = new Intent(this, MainService.class);
        startService(gpsService);

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

    }


    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);



    }

    @Override
    protected void onStop() {
        super.onStop();
        EventBus.getDefault().unregister(this);
    }

    @Subscribe
    public void onEvent(LocationEvent locationEvent){
        Log.d("Main","Location Event "+locationEvent);

        if(locationEvent.getEvent() == LocationEvent.Event.LOCATION_RESULT){
            locationTv.setText(locationEvent.getLocation());
            updatesTv.setText(String.valueOf(locationEvent.getUpdates()));
        }
    }

    @Subscribe
    public void onEvent(ServerEvent serverEvent){
        Log.d("Main Server Event"," Event:> "+serverEvent.getStatus());
        serverStatusTv.setText(R.string.server_connected);
    }


    @Subscribe
    public void onEvent(BluetoothEvent bluetoothEvent){

        switch(bluetoothEvent.getEvent()){
            case CONNECTED:

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusTv.setText("Connected!");
                        writeCharacteristicLeft.setEnabled(true);
                        writeCharacteristicStraight.setEnabled(true);
                        writeCharacteristicRight.setEnabled(true);
                    }
                });

                break;

            case CONNECTING:
                statusTv.setText("Connecting...");
                break;

            case SCANNING:
                statusTv.setText(R.string.scanning);
                scanBt.setEnabled(false);
                break;

            case STAND_BY:
                break;

            case DEVICE_FOUND:
                statusTv.setText("Scan Successful");
                Log.d("Wander Compass","Lo tengo Desde service post");
                break;

            case DISCONNECTED:
                statusTv.setText(R.string.ble_standby);
                writeCharacteristicLeft.setEnabled(false);
                writeCharacteristicStraight.setEnabled(false);
                writeCharacteristicRight.setEnabled(false);
                break;

            case SCAN_ERROR:
                statusTv.setText("Scan Error");
                break;
        }

    }



//    protected void createLocationRequest(){
//
//        locationRequest = LocationRequest.create();
//        locationRequest.setInterval(2000);
//        locationRequest.setFastestInterval(1000);
//        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
//
//
//        LocationSettingsRequest.Builder settingBuilder = new LocationSettingsRequest.Builder()
//                .addLocationRequest(locationRequest);
//
//        SettingsClient client = LocationServices.getSettingsClient(this);
//        Task <LocationSettingsResponse> task = client.checkLocationSettings(settingBuilder.build());
//
//
//        task.addOnSuccessListener(this, new OnSuccessListener<LocationSettingsResponse>() {
//            @Override
//            public void onSuccess(LocationSettingsResponse locationSettingsResponse) {
//                Log.d("Task Settings","Success");
//            }
//        });
//
//
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
//    }




    private void initializeBluetooth(){

        final BluetoothManager manager = (BluetoothManager)getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = manager.getAdapter();

        if(mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()){
            Intent enableBTIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBTIntent,REQUEST_ENABLE_INTENT);
        }
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
                BluetoothEvent be = new BluetoothEvent(BluetoothEvent.Event.START_SCAN);
                EventBus.getDefault().post(be);
                break;

            case R.id.writeCharacteristicLeft: {
                BluetoothEvent bluetoothEvent = new BluetoothEvent(BluetoothEvent.Event.WRITE_COMMAND, 0);
                EventBus.getDefault().post(bluetoothEvent);
            }
                break;

            case R.id.writeCharacteristicStraight: {

                BluetoothEvent bluetoothEvent = new BluetoothEvent(BluetoothEvent.Event.WRITE_COMMAND, 1);
                EventBus.getDefault().post(bluetoothEvent);
            }
                break;

            case R.id.writeCharacteristicRight:{

                BluetoothEvent bluetoothEvent = new BluetoothEvent(BluetoothEvent.Event.WRITE_COMMAND,2);
                EventBus.getDefault().post(bluetoothEvent);
            }
                break;

            case R.id.locationBt:

                if(!mTrackingLocation){

                    updatesTv.setText(R.string.getting_gps_location);
                    gpsToggleBt.setText(R.string.stop_gps_tracking);

                    LocationEvent locationEvent = new LocationEvent(null,0, LocationEvent.Event.TOGGLE_LOCATION_TRACKING);
                    EventBus.getDefault().post(locationEvent);

                }else{

                    LocationEvent locationEvent = new LocationEvent(null,0, LocationEvent.Event.TOGGLE_LOCATION_TRACKING);
                    EventBus.getDefault().post(locationEvent);

                    gpsToggleBt.setText(R.string.start_gps_tracking);
                    locationTv.setText("");
                    updatesTv.setText("");
                }

                break;

        }


    }

}
