package com.example.blutoothsppsampleapp;

/*
PCのteratermにAndroidから文字列を送受信する
PC側はteratermを想定しているが，通常のCOMポートとのserial通信
1.PCとAndroidのbluetoothペアリングを行う
　PC側のBluetoothをONにして，Android側からペアリング要求する　合言葉ナンバの一致で設定
2.PCの設定のBluetooth　→　その他のBluetooth設定　→　COMポートに「着信」を追加してCOMポートを得る
3.得られたCOMポートでteratermをシリアル通信起動
　9600 8bit paritynone stop1bit
  ですがbaud rateの設定は無視されるようだ　なにを設定しても通信OK
4.先にPCのteratermを起動してから，Android側のこのアプリを起動する
5.このアプリで更新相手のMACアドレスを選択する
 */

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {
    private final String TAG = "MainActivity";
    private static final UUID BT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");
    private static final int CHECK_PERMISSION = 1001;
    private String TargetMACAddress = "No device is connected";
    private BluetoothAdapter mBtAdapter;
    private BluetoothDevice mBtDevice;
    private BluetoothSocket mBtSocket;
    private OutputStream mOutput;
    private InputStream mInput;
    private TextView tv1;
    private TextView tv2;
    private Button selectDeviceButton;
    private Button connectButton;
    private Button sendHelloButton;
    private Button sendBonjourButton;
    private AlertDialog.Builder mAlertDialog;
    private Intent enableBtIntent;
    private ActivityResultLauncher<Intent> launcher;
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private Runnable mRunnable;

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i(TAG, "onCreate 0");
        tv1 = (TextView) findViewById(R.id.textView1);
        tv2 = (TextView) findViewById(R.id.textView2);
        tv2.setMovementMethod(new ScrollingMovementMethod());
        selectDeviceButton = (Button) findViewById(R.id.buttonSelect);
        connectButton = (Button) findViewById(R.id.buttonConnect);
        sendHelloButton = (Button) findViewById(R.id.button1);
        sendBonjourButton = (Button) findViewById(R.id.button2);
        mAlertDialog = new AlertDialog.Builder(this);
        mAlertDialog.setTitle("Alert");
        mAlertDialog.setPositiveButton("OK", null);

        tv1.setText(TargetMACAddress);
        sendHelloButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mOutput.write("Hello\r\n".getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    mAlertDialog.setMessage("メッセージHelloを送信することが出来ませんでした。");
                    mAlertDialog.show();
                    e.printStackTrace();
                }
            }
        });
        sendBonjourButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    mOutput.write("Bonjour\r\n".getBytes(StandardCharsets.UTF_8));
                } catch (IOException e) {
                    mAlertDialog.setMessage("メッセージHelloを送信することが出来ませんでした。");
                    mAlertDialog.show();
                    e.printStackTrace();
                }
            }
        });
        //selectボタン
        selectDeviceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getTargetAddress();
            }
        });
        //connectボタン
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                prepareSerialCommunication();
            }
        });

        enableBtIntent = new Intent( BluetoothAdapter.ACTION_REQUEST_ENABLE );
        launcher= registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    Log.i(TAG,"result");
                    if (result.getResultCode() == Activity.RESULT_CANCELED) {
                        Toast.makeText(this, "Bluetooth usage was not allowed.", Toast.LENGTH_LONG).show();
                        mAlertDialog.setMessage("Bluetoothの利用を拒否されました。これ以上何もできないので，アプリを終了してください。");
                        mAlertDialog.show();
                        //finish();    // アプリ終了宣言
                    } else {
                        Log.i(TAG, "onActivityResult() Bluetooth function is available.");
                    }
                });

        if (!checkBluetoothPermission()) {
            return;
        }
        launcher.launch(enableBtIntent);
    }
    private boolean checkBluetoothPermission() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, CHECK_PERMISSION);
            return false;
        } else {
            return true;
        }
    }


    private void prepareSerialCommunication() {
        // 交信先が指定されたBTデバイスのインスタンスを取得
        try {
            mBtDevice = mBtAdapter.getRemoteDevice(TargetMACAddress);
        } catch (Exception e) {
            mAlertDialog.setMessage("通信相手の取得に失敗しました。これ以上何もできないので，アプリを終了してください。");
            mAlertDialog.show();
            return;
        }
        // BTソケットのインスタンスを取得
        // 接続に使用するプロファイルを指定
        try {
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (!checkBluetoothPermission()) {
                    return;
                }
            }
            mBtSocket = mBtDevice.createRfcommSocketToServiceRecord(BT_UUID);
            mBtSocket.connect();
        } catch (IOException e) {
            e.printStackTrace();
            mAlertDialog.setMessage("ソケットへの接続が出来ませんでした。これ以上何もできないので，アプリを終了してください。\n"
                    + "通信相手のMACアドレスが正しくないのかもしれません。\n"
                    + "あるいは通信先シリアル通信アプリが起動していないのかもしれません。通信先を起動してから本アプリを起動してください。");
            mAlertDialog.show();
            selectDeviceButton.setEnabled(true);
            connectButton.setEnabled(false);
            return;
        }

        Log.i(TAG, "connect socket");
        //ソケットを接続する
        try {
            mOutput = mBtSocket.getOutputStream();//出力ストリームオブジェクト
        } catch (IOException e) {
            e.printStackTrace();
        }

        // 受信用のポーリング　Handlerを使用して，一定間隔で起動することでポーリングを実施
        mHandler = new Handler(Looper.getMainLooper());
        mRunnable = new Runnable() {
            @Override
            public void run() {
                byte[] buffer = new byte[1024];
                int nBytes = 0;

                try {
                    mInput = mBtSocket.getInputStream();
                    if (mInput.available() != 0)
                        nBytes = mInput.read(buffer, 0, 1024);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                String readMsg = new String(buffer, 0, nBytes);
                if (readMsg.length() != 0) {
                    String tmp = tv2.getText().toString() + readMsg;
                    // 改行コードの違いを吸収
                    tmp = tmp.replaceAll("\r","\r\n");
                    tmp = tmp.replaceAll("\r\n\n","\r\n");
                    tmp = tmp.replaceAll("\n","\r\n");
                    tmp = tmp.replaceAll("\r\r\n","\r\n");
                    tv2.setText(tmp);
                }
                mHandler.postDelayed(this, 50);
            }
        };
        mHandler.post(mRunnable);
        connectButton.setEnabled(false);
        sendHelloButton.setEnabled(true);
        sendBonjourButton.setEnabled(true);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                mAlertDialog.setMessage("権限が許可されませんでした。これ以上何もできないので，アプリを終了してください。");
                mAlertDialog.show();
                return;
            }
        }
        launcher.launch(enableBtIntent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBtSocket != null) {
            try {
                mBtSocket.close();
            } catch (IOException connectException) {/*ignore*/}
            mBtSocket = null;
        }
        mHandler.removeCallbacks(mRunnable);
    }

    private String selectedDevice = "";
    private void getTargetAddress() {
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        //BTアダプタのインスタンスを取得
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        mBtAdapter = bluetoothManager.getAdapter();
        Log.i(TAG,"getTargetAddress() mBtAdapter==null?" + (mBtAdapter==null));
        //交信先の候補の取得（ペアリングリスト中から）
        Set<BluetoothDevice> pairedDevices = mBtAdapter.getBondedDevices();
        int number = pairedDevices.size();
        String[] deviceAddressList = new String[number];
        String[] deviceSelectionList = new String[number];
        if (pairedDevices.size() > 1) {
            int i = 0;
            for (BluetoothDevice device : pairedDevices) {
                deviceSelectionList[i] = device.getName() + " " + device.getAddress();
                deviceAddressList[i] = device.getAddress();
                Log.i(TAG, "getTargetAddress() paired Device " + deviceSelectionList[i]);
                i++;
            }
            TargetMACAddress = deviceAddressList[0];
            selectedDevice = deviceSelectionList[0];
        } else {
            Log.i(TAG,"getTargetAddress() cant found any devices");
            return;
        }

        new android.app.AlertDialog.Builder(MainActivity.this)
                .setTitle("Select Bluetooth Device")
                .setSingleChoiceItems(deviceSelectionList, 0, (dialog, item) -> {
                    //アイテムを選択したらここに入る
                    TargetMACAddress = deviceAddressList[item];
                    selectedDevice = deviceSelectionList[item];
                })
                .setPositiveButton("Select", (dialog, id) -> {
                    //Selectを押したらここに入る
                    Log.i(TAG, "selectDevice() Selected Device " + selectedDevice);
                    tv1.setText(selectedDevice);
                    selectDeviceButton.setEnabled(false);
                    connectButton.setEnabled(true);
                })
                .setNegativeButton("Cancel", (dialog, id) -> {
                    //Cancelを押したらここに入る
                })
                .show();
    }
}