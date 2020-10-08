package com.example.sensorreader;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    final String deviceAddress = "00:81:F9:4A:4D:B3";
    final String serviceUUID = "f0001234-0451-4000-b000-00000000";

    final String temperatureUUID = "f0002345-0451-4000-b000-000000000000";
    final String humidityUUID = "f0003456-0451-4000-b000-000000000000";
    final String heartRateUUID = "f0004567-0451-4000-b000-000000000000";
    final String bloodOxygenUUID = "f0005678-0451-4000-b000-000000000000";

    public void updateStatus(String status){
        Message msg = new Message();
        msg.obj=status;
        statusHandler.sendMessage(msg);
    }

    @SuppressLint("HandlerLeak")
    Handler statusHandler = new Handler(){
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            TextView status = (TextView)findViewById(R.id.status_lbl);
            status.setText(msg.obj.toString());

        }
    };

    @SuppressLint("HandlerLeak")
    Handler updateHandler = new Handler(){
        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            Bundle data = (Bundle) msg.obj;
            String characteristic = data.getString("characteristic");
            String value = data.getString("value");
            assert characteristic != null;
            switch (characteristic) {
                case temperatureUUID: {
                    int val = Integer.parseInt(value);
                    int progress = val * 30 / 3000;
                    TextView tmpConverted = findViewById(R.id.temperatureConverted_lbl);
                    TextView temperatureRaw = findViewById(R.id.temperatureRaw_lbl);
                    ProgressBar pb = findViewById(R.id.temperature_pb);
                    tmpConverted.setText(progress + "ºC");
                    temperatureRaw.setText(value + "mV");
                    pb.setProgress(progress);
                    break;
                }
                case humidityUUID: {
                    int val = Integer.parseInt(value);
                    int progress = val * 100 / 3000;
                    TextView humidityConv = findViewById(R.id.humidityConverted_lbl);
                    TextView humidityRaw = findViewById(R.id.humidityRaw_lbl);
                    ProgressBar pb = findViewById(R.id.humidity_pb);
                    humidityConv.setText(progress + "%");
                    humidityRaw.setText(val + "mV");
                    pb.setProgress(progress);
                    break;
                }
                case heartRateUUID: {
                    int val = Integer.parseInt(value);
                    int progress = val * 100 / 3000; //40-200
                    TextView humidityConv = findViewById(R.id.heart_rate_converted);
                    TextView humidityRaw = findViewById(R.id.heart_rate_raw);
                    ProgressBar pb = findViewById(R.id.heart_rate_pb);
                    humidityRaw.setText(val + "mV");
                    pb.setProgress(progress);
                    progress = progress * 16 / 10 + 40;
                    humidityConv.setText(progress + "bpm");
                    break;
                }
                case bloodOxygenUUID: {
                    int val = Integer.parseInt(value);
                    int progress = val * 100 / 3000;    //60-100
                    TextView humidityConv = findViewById(R.id.blood_oxygen_converted);
                    TextView humidityRaw = findViewById(R.id.blood_oxygen_raw);
                    ProgressBar pb = findViewById(R.id.blood_oxygen_pb);
                    humidityRaw.setText(val + "mV");
                    pb.setProgress(progress);
                    progress = progress * 20 / 100 + 80;
                    humidityConv.setText(progress + "%");
                    break;
                }
            }
        }
    };

    private static final int REQUEST_ENABLE_BT = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initializes Bluetooth adapter.
        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();

        // Ensures Bluetooth is available on the device and it is enabled. If not,
        // displays a dialog requesting user permission to enable Bluetooth.
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
    }

    private BluetoothLeScanner bluetoothLeScanner =
            BluetoothAdapter.getDefaultAdapter().getBluetoothLeScanner();
    private boolean mScanning;
    private Handler handler = new Handler();

    // Stops scanning after 10 seconds.
    private static final long SCAN_PERIOD = 10000;

    public void stopScanning(){
        bluetoothLeScanner.stopScan(leScanCallback);
        mScanning = false;
        Log.d("BLE", "stopped scanning");
    }

    public void scan (View v){
        if (!mScanning) {
            Log.d("BLE", "scanning");
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    // After some time stop scanning if device hasn't been found
                    if(mScanning){
                        stopScanning();
                    }
                }
            }, SCAN_PERIOD);

            updateStatus("Scanning...");
            mScanning = true;
            bluetoothLeScanner.startScan(leScanCallback);
        } else {
            stopScanning();
        }
    }

    BluetoothDevice device;
    BluetoothGatt bluetoothGatt;

    // Device scan callback.
    private ScanCallback leScanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType, ScanResult result) {
                    super.onScanResult(callbackType, result);
                    if(result.getDevice().getAddress().equals(deviceAddress)){
                        if(mScanning)
                            bluetoothLeScanner.stopScan(leScanCallback);
                        mScanning = false;
                        updateStatus("Device found");

                        device = result.getDevice();
                        bluetoothGatt = device.connectGatt(getApplicationContext(), false, gattCallback);
                    }
                }
            };

    private final BluetoothGattCallback gattCallback =
            new BluetoothGattCallback() {
                @Override
                public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
                    switch (newState){
                        case BluetoothAdapter.STATE_CONNECTED:
                            Log.d("BLE", "Connected");
                            bluetoothGatt.discoverServices();
                        case BluetoothAdapter.STATE_CONNECTING:
                            Log.d("BLE", "Connecting");
                        case BluetoothAdapter.STATE_DISCONNECTED:
                            Log.d("BLE", "Disconnected");
                        case BluetoothAdapter.STATE_DISCONNECTING:
                            Log.d("BLE", "Disconnecting");
                        default:
                            Log.d("BLE", "Unknown state " + newState);
                    }
                }

                List<BluetoothGattCharacteristic> characteristicList;
                int i = 0;
                public void readCharacteristic(final BluetoothGatt gatt){

                    final int index = i;
                    i = ++i % characteristicList.size();
                    try {
                        Thread.sleep(10);
                        gatt.readCharacteristic(characteristicList.get(index));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                // New services discovered
                public void onServicesDiscovered(BluetoothGatt gatt, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        BluetoothGattService service = gatt.getService(UUID.fromString(serviceUUID));
                        Log.d("BLE", "Discovered Service: " +  service.getUuid().toString());
                        mScanning = true;

                        updateStatus("Connected");

                        characteristicList = service.getCharacteristics();
                        readCharacteristic(gatt);
                    } else {
                        Log.w("BLE", "onServicesDiscovered received: " + status);
                    }
                }

                @Override
                // Result of a characteristic read operation
                public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        int characteristicValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT16, 0);

                        Bundle bundle = new Bundle();
                        bundle.putString("value", Integer.toString(characteristicValue));
                        bundle.putString("characteristic", characteristic.getUuid().toString());

                        Message message = new Message();
                        message.obj = bundle;
                        updateHandler.sendMessage(message);

                        readCharacteristic(gatt);
                    }
                    else{
                        Log.w("BLE", "onCharacteristicRead: " + status);
                    }
                }
            };
}