/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothlegatt;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ExpandableListView;
import android.widget.SimpleExpandableListAdapter;
import android.widget.TextView;

import com.orbotix.ConvenienceRobot;
import com.orbotix.DualStackDiscoveryAgent;
import com.orbotix.command.RGBLEDOutputCommand;
import com.orbotix.common.DiscoveryException;
import com.orbotix.common.Robot;
import com.orbotix.common.RobotChangedStateListener;
import com.orbotix.macro.MacroObject;
import com.orbotix.macro.cmd.Delay;
import com.orbotix.macro.cmd.RawMotor;
import com.orbotix.macro.cmd.Roll;
import com.orbotix.macro.cmd.RawMotor.DriveMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;

/**
 * For a given BLE device, this Activity provides the user interface to connect, display data,
 * and display GATT services and characteristics supported by the device.  The Activity
 * communicates with {@code BluetoothLeService}, which in turn interacts with the
 * Bluetooth LE API.
 */
public class DeviceControlActivity extends Activity implements RobotChangedStateListener{
    private final static String TAG = DeviceControlActivity.class.getSimpleName();

    public static final String EXTRAS_DEVICE_NAME = "DEVICE_NAME";
    public static final String EXTRAS_DEVICE_ADDRESS = "DEVICE_ADDRESS";
    private ConvenienceRobot mRobot;

    private TextView mConnectionState;
    private TextView mDataField;
    private String mDeviceName;
    private String mDeviceAddress;
    private ExpandableListView mGattServicesList;
    private Button spheroButton;
    private BluetoothLeService mBluetoothLeService;
    private ArrayList<ArrayList<BluetoothGattCharacteristic>> mGattCharacteristics =
            new ArrayList<ArrayList<BluetoothGattCharacteristic>>();
    private boolean mConnected = false;
    private BluetoothGattCharacteristic mNotifyCharacteristic;
    private static final float ROBOT_VELOCITY = 0.6f;
    private static final int REQUEST_CODE_LOCATION_PERMISSION = 42;


    private final String LIST_NAME = "NAME";
    private final String LIST_UUID = "UUID";
    int spheroState = 0;

    // Code to manage Service lifecycle.
    private final ServiceConnection mServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder service) {
            mBluetoothLeService = ((BluetoothLeService.LocalBinder) service).getService();
            if (!mBluetoothLeService.initialize()) {
                Log.e(TAG, "Unable to initialize Bluetooth");
                finish();
            }
            // Automatically connects to the device upon successful start-up initialization.
            mBluetoothLeService.connect(mDeviceAddress);
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            mBluetoothLeService = null;
        }
    };

    // Handles various events fired by the Service.
    // ACTION_GATT_CONNECTED: connected to a GATT server.
    // ACTION_GATT_DISCONNECTED: disconnected from a GATT server.
    // ACTION_GATT_SERVICES_DISCOVERED: discovered GATT services.
    // ACTION_DATA_AVAILABLE: received data from the device.  This can be a result of read
    //                        or notification operations.
    private final BroadcastReceiver mGattUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (BluetoothLeService.ACTION_GATT_CONNECTED.equals(action)) {
                mConnected = true;
                updateConnectionState(R.string.connected);
                invalidateOptionsMenu();
            } else if (BluetoothLeService.ACTION_GATT_DISCONNECTED.equals(action)) {
                mConnected = false;
                updateConnectionState(R.string.disconnected);
                invalidateOptionsMenu();
                clearUI();
            } else if (BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED.equals(action)) {
                // Show all the supported services and characteristics on the user interface.
                displayGattServices(mBluetoothLeService.getSupportedGattServices());
            } else if (BluetoothLeService.ACTION_DATA_AVAILABLE.equals(action)) {
                displayData(intent.getStringExtra(BluetoothLeService.EXTRA_DATA));
            }
        }
    };

    // If a given GATT characteristic is selected, check for supported features.  This sample
    // demonstrates 'Read' and 'Notify' features.  See
    // http://d.android.com/reference/android/bluetooth/BluetoothGatt.html for the complete
    // list of supported characteristic features.
    private final ExpandableListView.OnChildClickListener servicesListClickListner =
            new ExpandableListView.OnChildClickListener() {
                @Override
                public boolean onChildClick(ExpandableListView parent, View v, int groupPosition,
                                            int childPosition, long id) {
                    if (mGattCharacteristics != null) {
                        final BluetoothGattCharacteristic characteristic =
                                mGattCharacteristics.get(groupPosition).get(childPosition);
                        final int charaProp = characteristic.getProperties();
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                            // If there is an active notification on a characteristic, clear
                            // it first so it doesn't update the data field on the user interface.
                            if (mNotifyCharacteristic != null) {
                                mBluetoothLeService.setCharacteristicNotification(
                                        mNotifyCharacteristic, false);
                                mNotifyCharacteristic = null;
                            }
                            mBluetoothLeService.readCharacteristic(characteristic);
                        }
                        if ((charaProp | BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                            mNotifyCharacteristic = characteristic;
                            mBluetoothLeService.setCharacteristicNotification(
                                    characteristic, true);
                        }
                        return true;
                    }
                    return false;
                }
    };

    private void clearUI() {
        mGattServicesList.setAdapter((SimpleExpandableListAdapter) null);
        mDataField.setText(R.string.no_data);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.gatt_services_characteristics);
//        commented for sphero
        DualStackDiscoveryAgent.getInstance().addRobotStateListener( this );
        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
            int hasLocationPermission = checkSelfPermission( Manifest.permission.ACCESS_COARSE_LOCATION );
            if( hasLocationPermission != PackageManager.PERMISSION_GRANTED ) {
                Log.e( "Sphero", "Location permission has not already been granted" );
                List<String> permissions = new ArrayList<String>();
                permissions.add( Manifest.permission.ACCESS_COARSE_LOCATION);
                requestPermissions(permissions.toArray(new String[permissions.size()] ), REQUEST_CODE_LOCATION_PERMISSION );
            } else {
                Log.d( "Sphero", "Location permission already granted" );
            }
        }
        final Intent intent = getIntent();
        mDeviceName = intent.getStringExtra(EXTRAS_DEVICE_NAME);
        mDeviceAddress = intent.getStringExtra(EXTRAS_DEVICE_ADDRESS);

        // Sets up UI references.
        ((TextView) findViewById(R.id.device_address)).setText(mDeviceAddress);
        mGattServicesList = (ExpandableListView) findViewById(R.id.gatt_services_list);
        mGattServicesList.setOnChildClickListener(servicesListClickListner);
        mConnectionState = (TextView) findViewById(R.id.connection_state);
        mDataField = (TextView) findViewById(R.id.data_value);
        spheroButton = (Button) findViewById(R.id.connect_sphero_button);
        getActionBar().setTitle(mDeviceName);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        Intent gattServiceIntent = new Intent(this, BluetoothLeService.class);
        bindService(gattServiceIntent, mServiceConnection, BIND_AUTO_CREATE);

        spheroButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                        || checkSelfPermission( Manifest.permission.ACCESS_COARSE_LOCATION ) == PackageManager.PERMISSION_GRANTED ) {
                    startDiscovery();
                }

            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(mGattUpdateReceiver, makeGattUpdateIntentFilter());
        if (mBluetoothLeService != null) {
            final boolean result = mBluetoothLeService.connect(mDeviceAddress);
            Log.d(TAG, "Connect request result=" + result);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mGattUpdateReceiver);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(mServiceConnection);
        mBluetoothLeService = null;
    }

    @Override
    protected void onStart() {
        super.onStart();

    }

    private void startDiscovery() {
        //If the DiscoveryAgent is not already looking for robots, start discovery.
        if( !DualStackDiscoveryAgent.getInstance().isDiscovering() ) {
            try {
                DualStackDiscoveryAgent.getInstance().startDiscovery( this );
            } catch (DiscoveryException e) {
                Log.e("Sphero", "DiscoveryException: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onStop() {
        //If the DiscoveryAgent is in discovery mode, stop it.
        if( DualStackDiscoveryAgent.getInstance().isDiscovering() ) {
            DualStackDiscoveryAgent.getInstance().stopDiscovery();
        }

        //If a robot is connected to the device, disconnect it
        if( mRobot != null ) {
            mRobot.disconnect();
            mRobot = null;
        }

        super.onStop();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.gatt_services, menu);
        if (mConnected) {
            menu.findItem(R.id.menu_connect).setVisible(false);
            menu.findItem(R.id.menu_disconnect).setVisible(true);
        } else {
            menu.findItem(R.id.menu_connect).setVisible(true);
            menu.findItem(R.id.menu_disconnect).setVisible(false);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            case R.id.menu_connect:
                mBluetoothLeService.connect(mDeviceAddress);
                return true;
            case R.id.menu_disconnect:
                mBluetoothLeService.disconnect();
                return true;
            case android.R.id.home:
                onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateConnectionState(final int resourceId) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mConnectionState.setText(resourceId);
            }
        });
    }

    private boolean convertBoolean(float sensorReading){
        if(sensorReading>3000.0)
            return true;
        return false;
    }

    private float getRotation(float sensorReadingA, float sensorReadingB){
        float ratio;
        boolean positive;
        if (sensorReadingA>sensorReadingB) {
            ratio = sensorReadingB / sensorReadingA;
            positive = false;
        }
        else{
            ratio = sensorReadingA / sensorReadingB;
            positive = true;
        }
        float angle = (1.0f-ratio)*90.0f;
        if (positive)
            return angle+15.0f;
        else
            return 360.0f-angle-15.0f;
    }

    private float getVelocity(float sensorReadingA, float sensorReadingB){
        float maxReading = Math.max(sensorReadingA, sensorReadingB);
        return Math.min(1.0f,0.2f+Math.min(maxReading-3000.0f, 45000.0f)/45000.0f);
    }

    private float getFullRotation(float sensorReadingA, float sensorReadingB){
        double max_d = 48000.0f;
        // distance between sensors
        double d = 3000.0f;
        // distance between supposed center line
        double alpha = 6000.0f;
        double radius = Math.sqrt((d*d/4)+(alpha*alpha));
        double a = max_d - sensorReadingA;
        double b = max_d - sensorReadingB;
        if((b<=alpha)&&(b<=a)) {
            double cos = ((a * a) - (b * b)) / (2 * d * radius);
            if (cos > 1.0)
                cos = 1.0f;
            else if (cos < -1.0f)
                cos = -1.0f;
            double angle = Math.acos(cos);
            double degrees = 90.0f-Math.toDegrees(angle);
            Log.v("Pallette1", ":" + degrees + ":" + angle + ":" + d + ":" + radius + ":" + cos + ":" + a + ":" + b);
        }
        else if((a<=alpha)&&(a<=b)) {
            double cos = ((b * b) - (a * a)) / (2 * d * radius);
            if (cos > 1.0)
                cos = 1.0f;
            else if (cos < -1.0f)
                cos = -1.0f;
            double angle = Math.acos(cos);
            double degrees = 270.0f + Math.toDegrees(angle);
            Log.v("Pallette2", ":" + degrees + ":" + angle + ":" + d + ":" + radius + ":" + cos + ":" + a + ":" + b);
        }
        else if((b>=alpha)&&(b<=a)) {
            double cos = ((a * a) - (b * b)) / (2 * d * radius);
            if (cos > 1.0)
                cos = 1.0f;
            else if (cos < -1.0f)
                cos = -1.0f;
            double angle = Math.acos(cos);
            double degrees = 90.0f+Math.toDegrees(angle);
            Log.v("Pallette3", ":" + degrees + ":" + angle + ":" + d + ":" + radius + ":" + cos + ":" + a + ":" + b);
        }
        else if((a>=alpha)&&(a<=b)) {
            double cos = ((b * b) - (a * a)) / (2 * d * radius);
            if (cos > 1.0)
                cos = 1.0f;
            else if (cos < -1.0f)
                cos = -1.0f;
            double angle = Math.acos(cos);
            double degrees = 180.0f+Math.toDegrees(angle);
            Log.v("Pallette4", ":" + degrees + ":" + angle + ":" + d + ":" + radius + ":" + cos + ":" + a + ":" + b);
        }
        return 0.0f;
    }


    private void displayData(String data) {
//        mRobot.drive( 90.0f, ROBOT_VELOCITY );
        if (data != null) {
            if(mRobot!=null){
                String[] values = data.split("\t");
                ArrayList<Integer> sensors = new ArrayList<Integer>();
                for(String s: values)
                    sensors.add(Integer.parseInt(s));

                float sensor_a = sensors.get(0);
                float sensor_b = sensors.get(1);
                boolean bool_sensor_a = convertBoolean( sensor_a );
                boolean bool_sensor_b = convertBoolean( sensor_b );
                if (bool_sensor_a && bool_sensor_b){
//                    driveAngle(getRotation(sensor_a,sensor_b),getVelocity(sensor_a,sensor_b));
                    getFullRotation(sensor_a, sensor_b);
                }
            }

            mDataField.setText(data);
        }
    }

    private void changeColor(){

        mRobot.sendCommand( new RGBLEDOutputCommand( getRandom(), getRandom(), getRandom() ) );
    }

    private float getRandom(){
        Random rn = new Random();
        return rn.nextInt(2);

    }
    private void driveAngle(float rotation, float velocity){
        Log.v("Pallette", "Drive angle"+rotation+" Speed:"+velocity);
        mRobot.drive( rotation, velocity );
    }

    private void driveLeft(){
        Log.v("Pallette", "Drive left");
        mRobot.drive( 270.0f, ROBOT_VELOCITY );
    }

    private void driveRight(){
        Log.v("Pallette", "Drive right");
        mRobot.drive( 90.0f, ROBOT_VELOCITY );
    }

    private void driveForward(){
        Log.v("Pallette", "Drive forward");

        mRobot.drive( 0.0f, ROBOT_VELOCITY );
    }

    private void driveBackward(){
        Log.v("Pallette", "Drive backward");
        mRobot.drive( 180.0f, ROBOT_VELOCITY );
    }

    private void drive(float velocity){
        mRobot.drive( 0.0f, velocity );
    }

    private void jump(){
        MacroObject macro = new MacroObject();
        macro.addCommand(new Roll(0.15f, 0, 0));
        macro.addCommand(new RawMotor(DriveMode.BRAKE, 255, DriveMode.FORWARD, 253, 255));
        macro.addCommand(new Delay(1000));
        macro.playMacro();

    }

    // Demonstrates how to iterate through the supported GATT Services/Characteristics.
    // In this sample, we populate the data structure that is bound to the ExpandableListView
    // on the UI.
    private void displayGattServices(List<BluetoothGattService> gattServices) {
        if (gattServices == null) return;
        String uuid = null;
        String unknownServiceString = getResources().getString(R.string.unknown_service);
        String unknownCharaString = getResources().getString(R.string.unknown_characteristic);
        ArrayList<HashMap<String, String>> gattServiceData = new ArrayList<HashMap<String, String>>();
        ArrayList<ArrayList<HashMap<String, String>>> gattCharacteristicData
                = new ArrayList<ArrayList<HashMap<String, String>>>();
        mGattCharacteristics = new ArrayList<ArrayList<BluetoothGattCharacteristic>>();

        // Loops through available GATT Services.
        for (BluetoothGattService gattService : gattServices) {
            HashMap<String, String> currentServiceData = new HashMap<String, String>();
            uuid = gattService.getUuid().toString();
            currentServiceData.put(
                    LIST_NAME, SampleGattAttributes.lookup(uuid, unknownServiceString));
            currentServiceData.put(LIST_UUID, uuid);
            gattServiceData.add(currentServiceData);

            ArrayList<HashMap<String, String>> gattCharacteristicGroupData =
                    new ArrayList<HashMap<String, String>>();
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            ArrayList<BluetoothGattCharacteristic> charas =
                    new ArrayList<BluetoothGattCharacteristic>();

            // Loops through available Characteristics.
            for (BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                charas.add(gattCharacteristic);
                HashMap<String, String> currentCharaData = new HashMap<String, String>();
                uuid = gattCharacteristic.getUuid().toString();
                currentCharaData.put(
                        LIST_NAME, SampleGattAttributes.lookup(uuid, unknownCharaString));
                currentCharaData.put(LIST_UUID, uuid);
                gattCharacteristicGroupData.add(currentCharaData);
            }
            mGattCharacteristics.add(charas);
            gattCharacteristicData.add(gattCharacteristicGroupData);
        }

        SimpleExpandableListAdapter gattServiceAdapter = new SimpleExpandableListAdapter(
                this,
                gattServiceData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 },
                gattCharacteristicData,
                android.R.layout.simple_expandable_list_item_2,
                new String[] {LIST_NAME, LIST_UUID},
                new int[] { android.R.id.text1, android.R.id.text2 }
        );
        mGattServicesList.setAdapter(gattServiceAdapter);
    }

    private static IntentFilter makeGattUpdateIntentFilter() {
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_CONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_DISCONNECTED);
        intentFilter.addAction(BluetoothLeService.ACTION_GATT_SERVICES_DISCOVERED);
        intentFilter.addAction(BluetoothLeService.ACTION_DATA_AVAILABLE);
        return intentFilter;
    }

    @Override
    public void handleRobotChangedState(Robot robot, RobotChangedStateNotificationType type) {
        switch (type) {
            case Online: {
                //Save the robot as a ConvenienceRobot for additional utility methods
                mRobot = new ConvenienceRobot(robot);
                break;
            }
        }
    }

}
