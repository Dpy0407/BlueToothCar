package com.bluetooth.admin.balencecar;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.graphics.drawable.AnimationDrawable;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.Switch;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

enum BluetoothConnectingStatus {
    STATUS_NOT_CONNECT,
    STATUS_CONNECTING,
    STATUS_CONNECTED
}

public class MainActivity extends Activity {

    static final String TAG = "GoodixCar";

    private BluetoothAdapter bluetoothAdapter;
    private Switch bluetoothSwitch;
    private Button bluetoothConnect;
    private ArrayAdapter<String> adapter;
    private List<String> list = new ArrayList<String>();
    private String strMacAddress;
    private BluetoothConnectingStatus connectingStatus = BluetoothConnectingStatus.STATUS_NOT_CONNECT;

    /*方向按钮定义*/
    private Button mButtonRun;
    private Button mButtonBack;
    private Button mButtonLeft;
    private Button mButtonRight;
    private Button mButtonStop;
    private Button mButtonLevo;
    private Button mButtonDextro;
    /*功能按钮*/

    private static final int MSG_SYSTEM_BLUETOOTH = 0xA000;
    private static final int MSG_BLUETOOTH_ENABLED = 0xA100;
    private static final int MSG_BLUETOOTH_NOT_ENABLE = 0xA101;
    private static final int MSG_BLUETOOTH_CONNECTING = 0xA102;

    //msg 定义
    private static final int msgShowConnect = 1;

    /**************service 命令*********/
    static final int CMD_STOP_SERVICE = 0x01;       // Main -> service
    static final int CMD_SEND_DATA = 0x02;          // Main -> service
    static final int CMD_SYSTEM_EXIT = 0x03;         // service -> Main
    static final int CMD_SHOW_TOAST = 0x04;          // service -> Main
    static final int CMD_CONNECT_BLUETOOTH = 0x05;  // Main -> service
    static final int CMD_RECEIVE_DATA = 0x06;       // service -> Main

    private AlertDialog blueToothDialog;
    private AnimationDrawable connectingAnimation;

    private MyReceiver receiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        /*设置全屏*/
        //  requestWindowFeature(Window.FEATURE_NO_TITLE);//隐藏标题
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);//设置全屏
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);  // keep screen on
        setContentView(R.layout.activity_main);

        bluetoothSwitch = (Switch) findViewById(R.id.bluetoothswitch);
        bluetoothConnect = (Button) findViewById(R.id.buttonConnect);

        /*按钮监听按下弹起*/
        ButtonListener b = new ButtonListener();
        mButtonRun = (Button) findViewById(R.id.button_run);
        mButtonBack = (Button) findViewById(R.id.button_back);
        mButtonLeft = (Button) findViewById(R.id.button_left);
        mButtonRight = (Button) findViewById(R.id.button_right);
        mButtonStop = (Button) findViewById(R.id.button_stop);
        mButtonLevo = (Button) findViewById(R.id.button_levo);
        mButtonDextro = (Button) findViewById(R.id.button_dextro);

        //mButtonRun.setOnClickListener(b);
        mButtonRun.setOnTouchListener(b);
        mButtonBack.setOnTouchListener(b);
        mButtonLeft.setOnTouchListener(b);
        mButtonRight.setOnTouchListener(b);
        mButtonStop.setOnTouchListener(b);
        mButtonLevo.setOnTouchListener(b);
        mButtonDextro.setOnTouchListener(b);

        /*按钮监听按下弹起*/

        /*检查手机是否支持蓝牙*/
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            //表明此手机不支持蓝牙
            Toast.makeText(MainActivity.this, "未发现蓝牙设备", Toast.LENGTH_SHORT).show();
            return;
        }
        if (bluetoothAdapter.isEnabled()) {
            bluetoothSwitch.setChecked(true);
        }

        /*添加蓝牙列表*/
        adapter = new ArrayAdapter<String>(this, R.layout.bluetooth_dialog_item);
        View popView = View.inflate(MainActivity.this, R.layout.bluetooth_pop_selector, null);
        ListView listView = (ListView) popView.findViewById(R.id.bluetooth_item);
        listView.setAdapter(adapter);

        final AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this, R.style.AlertDialog_AppCompat_BluetoothSelector);
        builder.setView(popView);
        blueToothDialog = builder.create();

        listView.setOnItemClickListener(new ListView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
                String addr = adapter.getItem(arg2);
                blueToothDialog.dismiss();
                Toast.makeText(MainActivity.this, "正在连接设备：" + addr, Toast.LENGTH_SHORT).show();
                doBlueToothConnect(addr);
            }
        });


//        adapter.setDropDownViewResource(R.layout.bluetooth_dialog_item);
//        bluetoothList.setAdapter(adapter);
//        bluetoothList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
//            @Override
//            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
//                strMacAddress = adapter.getItem(i);
//                adapterView.setVisibility(View.VISIBLE);
//
//            }
//
//            @Override
//            public void onNothingSelected(AdapterView<?> adapterView) {
//
//            }
//        });

        /*蓝牙总开关*/
        bluetoothSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean checked) {
                if (!compoundButton.isPressed()) {
                    return;
                }

                if (checked) {
                    if (bluetoothAdapter == null) {
                        Toast.makeText(MainActivity.this, "未发现蓝牙设备", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (!bluetoothAdapter.isEnabled()) { //蓝牙未开启，则开启蓝牙
                        Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                       // startActivityForResult(enableIntent, MSG_SYSTEM_BLUETOOTH);
                        bluetoothAdapter.enable();  // 强制打开，无提示
                        new Thread(new checkBlueToothThread()).start();

                    } else {
                        Toast.makeText(MainActivity.this, "蓝牙已开启", Toast.LENGTH_SHORT).show();
                    }

                } else {
                    bluetoothAdapter.disable();
                    Toast.makeText(MainActivity.this, "蓝牙已关闭", Toast.LENGTH_SHORT).show();
                }
            }
        });


        /*蓝牙连接或断开*/
        bluetoothConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (connectingStatus == BluetoothConnectingStatus.STATUS_CONNECTED) {  // 断开蓝牙连接
                    bluetoothConnect.setBackgroundResource(R.drawable.bluetooth_not_connect_selector);
                    Intent intent = new Intent();//创建Intent对象
                    intent.setAction("android.intent.action.cmd");
                    intent.putExtra("cmd", CMD_STOP_SERVICE);
                    sendBroadcast(intent);//发送广播连接蓝牙
                } else if (connectingStatus == BluetoothConnectingStatus.STATUS_CONNECTING) {
                    // TODO: stop connecting
                } else {
                    if (!bluetoothAdapter.isEnabled() || !bluetoothSwitch.isChecked()) {
                        Toast.makeText(MainActivity.this, "请打开蓝牙开关", Toast.LENGTH_SHORT).show();
                    } else {
                        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
                        if (pairedDevices.size() > 0) {
                            adapter.clear();
                            for (BluetoothDevice device : pairedDevices) {
                                adapter.add(device.getAddress());
                            }
                            blueToothDialog.show();
                        } else {
                            Toast.makeText(MainActivity.this, "未发现匹配的设备", Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }
        });

    }

    private Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            if (msg.what == MSG_BLUETOOTH_NOT_ENABLE) {
                Toast.makeText(MainActivity.this, "蓝牙开启失败，请重试", Toast.LENGTH_SHORT).show();
                bluetoothSwitch.setChecked(false);
            }

            if (msg.what == MSG_BLUETOOTH_ENABLED) {
                if (bluetoothAdapter.isEnabled()) {
                    Toast.makeText(MainActivity.this, "蓝牙已开启", Toast.LENGTH_SHORT).show();
                    Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
                    if (pairedDevices.size() > 0) {
                        adapter.clear();
                        for (BluetoothDevice device : pairedDevices) {
                            adapter.add(device.getAddress());
                        }
                    } else {
                        //注册，当一个设备被发现时调用mReceive
//                        Log.i(TAG, "pairedDevices.size() == " + pairedDevices.size());
//                        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
//                        registerReceiver(mReceiver, filter);
                    }

                    blueToothDialog.show();
                } else {
                    Toast.makeText(MainActivity.this, "蓝牙开启失败，请重试", Toast.LENGTH_SHORT).show();
                    bluetoothSwitch.setChecked(false);
                }
            }

            if (msg.what == MSG_BLUETOOTH_CONNECTING) {
                bluetoothConnect.setBackgroundResource(R.drawable.connecting_animation);
                connectingAnimation = (AnimationDrawable) bluetoothConnect.getBackground();
                connectingAnimation.start();
            }
        }
    };

    public class checkBlueToothThread implements Runnable {
        @Override
        public void run() {
            boolean flagEnable = false;
            for (int i = 0; i < 5; i++) {
                try {
                    Thread.sleep(1000);
                    if (bluetoothAdapter.isEnabled()) {
                        flagEnable = true;
                        break;
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            if (flagEnable) {
                Message message = new Message();
                message.what = MSG_BLUETOOTH_ENABLED;
                handler.sendMessage(message);
            } else {

                Message message = new Message();
                message.what = MSG_BLUETOOTH_NOT_ENABLE;
                handler.sendMessage(message);
            }
        }
    }

    private void doBlueToothConnect(String addr) {

        if (connectingStatus == BluetoothConnectingStatus.STATUS_NOT_CONNECT) {
            Intent i = new Intent(MainActivity.this, MyService.class);
            i.putExtra("Mac", addr);
            startService(i);

            Message message = new Message();
            message.what = MSG_BLUETOOTH_CONNECTING;
            handler.sendMessage(message);

        } else {
            // TODO: 17-9-1
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == MSG_SYSTEM_BLUETOOTH) {
            Log.i(TAG, "resultCode: " + resultCode);
            if (resultCode == RESULT_OK) {
                if (bluetoothAdapter.isEnabled()) {

                    bluetoothSwitch.setChecked(true);
                } else {
                    bluetoothSwitch.setChecked(false);
                }

            } else if (resultCode == RESULT_CANCELED) {
                bluetoothSwitch.setChecked(false);
            }
        }
    }


    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.e("fond:", "mReceiver");

            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                // 已经配对的则跳过
                if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                    adapter.add(device.getAddress());
                }

            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {  //搜索结束
                Log.e("fond:", "ACTION_DISCOVERY_FINISHED");
                if (adapter.getCount() == 0) {
                    Toast.makeText(MainActivity.this, "没有搜索到设备", Toast.LENGTH_SHORT).show();
                }
            }

        }
    };

    /*********************************************************************************************/
    @Override
    protected void onStart() {
        super.onStart();
        Log.i("OnStart", "Start");
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        Log.i("onDestroy", "Destroy");
        if (receiver != null) {
            MainActivity.this.unregisterReceiver(receiver);
        }
    }

    @Override
    protected void onResume() {
        // TODO Auto-generated method stub
        super.onResume();
        Log.i("onResume", "Resume");
        receiver = new MyReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.bluetooth.admin.bluetooth");
        MainActivity.this.registerReceiver(receiver, filter);
    }

    public void showToast(String str) {//显示提示信息
        Toast.makeText(getApplicationContext(), str, Toast.LENGTH_SHORT).show();
    }

    public class MyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            // TODO Auto-generated method stub
            if (intent.getAction().equals("android.intent.action.bluetooth.admin.bluetooth")) {
                Bundle bundle = intent.getExtras();
                int cmd = bundle.getInt("cmd");

                if (cmd == CMD_SHOW_TOAST) {
                    String str = bundle.getString("str");
                    showToast(str);
                    if ("连接成功建立，可以开始操控了!".equals(str)) {
                        connectingStatus = BluetoothConnectingStatus.STATUS_CONNECTED;
                        bluetoothConnect.setEnabled(true);
                        bluetoothConnect.setBackgroundResource(R.drawable.bluetooth_connneted_selector);
                    } else if ("连接失败".equals(str)) {
                        connectingStatus = BluetoothConnectingStatus.STATUS_NOT_CONNECT;
                        bluetoothConnect.setEnabled(true);
                        bluetoothConnect.setBackgroundResource(R.drawable.bluetooth_not_connect_selector);
                    }
                } else if (cmd == CMD_SYSTEM_EXIT) {
                    System.exit(0);
                } else if (cmd == CMD_RECEIVE_DATA)  //此处是可以接收蓝牙发送过来的数据可以解析，此例程暂时不解析返回来的数据，需要解析的在我们的全功能版会有
                {
//                    String strtemp = bundle.getString("str");
//                    int start = strtemp.indexOf("$");
//                    int end = strtemp.indexOf("#");
//
//                    if (start >= 0 && end > 0 && end > start && strtemp.length() > 23 )
//                    {
//                        String str = strtemp.substring(23);
//                        String strCSB = str.substring(0, str.indexOf(","));
//                        String strVolume = str.substring(str.indexOf(",")+1, str.indexOf("#"));
//                        tvCSB.setText(strCSB);
//                        tvVolume.setText(strVolume);
//                    }
                }


            }
        }
    }

    public void SendBlueToothProtocol(String value) {
        Intent intent = new Intent();//创建Intent对象
        intent.setAction("android.intent.action.cmd");
        intent.putExtra("cmd", CMD_SEND_DATA);
        intent.putExtra("command", (byte) 0x00);
        intent.putExtra("value", value);
        sendBroadcast(intent);//发送广播
    }

    /*********************************************************************************************/


    class ButtonListener implements View.OnClickListener, View.OnTouchListener {

        public void onClick(View v) {
            if (v.getId() == R.id.button_run) {
                //Log.d("test", "cansal button ---> click");
            }
        }

        public boolean onTouch(View v, MotionEvent event) {
            switch (v.getId()) {
                case R.id.button_run: {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        mButtonRun.setBackgroundResource(R.drawable.button_run);
                        SendBlueToothProtocol("0");
                    }
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        SendBlueToothProtocol("1");

                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        v.playSoundEffect(SoundEffectConstants.CLICK);
                        mButtonRun.setBackgroundResource(R.drawable.button_run_clicked);
                    }
                }
                break;

                case R.id.button_back: {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        mButtonBack.setBackgroundResource(R.drawable.button_back);
                        SendBlueToothProtocol("0");

                    }
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        v.playSoundEffect(SoundEffectConstants.CLICK);
                        mButtonBack.setBackgroundResource(R.drawable.button_back_clicked);
                        SendBlueToothProtocol("2");


                    }
                }
                break;

                case R.id.button_left: {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        mButtonLeft.setBackgroundResource(R.drawable.button_left);

                        SendBlueToothProtocol("0");

                    }
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        v.playSoundEffect(SoundEffectConstants.CLICK);
                        mButtonLeft.setBackgroundResource(R.drawable.button_left_clicked);
                        SendBlueToothProtocol("3");
                    }
                }
                break;

                case R.id.button_right: {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        mButtonRight.setBackgroundResource(R.drawable.button_right);
                        SendBlueToothProtocol("0");

                    }
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        v.playSoundEffect(SoundEffectConstants.CLICK);
                        mButtonRight.setBackgroundResource(R.drawable.button_right_clicked);
                        SendBlueToothProtocol("4");
                    }
                }
                break;

                case R.id.button_stop: {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        mButtonStop.setBackgroundResource(R.drawable.accelerator);
                    }
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        mButtonStop.setBackgroundResource(R.drawable.accelerator_click);
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        v.playSoundEffect(SoundEffectConstants.CLICK);
                        SendBlueToothProtocol("0");

                    }
                }
                break;

                /*左旋*/
                case R.id.button_levo: {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        mButtonLevo.setBackgroundResource(R.drawable.turn_left);
                        SendBlueToothProtocol("0");
                    }
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        mButtonLevo.setBackgroundResource(R.drawable.turn_left_click);
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        v.playSoundEffect(SoundEffectConstants.CLICK);
                        SendBlueToothProtocol("5");

                    }
                }
                break;

                /*右旋*/
                case R.id.button_dextro: {
                    if (event.getAction() == MotionEvent.ACTION_UP) {
                        mButtonDextro.setBackgroundResource(R.drawable.turn_right);
                        SendBlueToothProtocol("0");

                    }
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        mButtonDextro.setBackgroundResource(R.drawable.turn_right_click);
                        v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                        v.playSoundEffect(SoundEffectConstants.CLICK);
                        SendBlueToothProtocol("6");


                    }
                }
                break;

            }
            return false;
        }

    }


}
