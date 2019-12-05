package com.example.smartbrush;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
//import android.support.v7.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private PaintView paintView;
    BluetoothAdapter mBluetoothAdapter;
    static Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        paintView = (PaintView) findViewById(R.id.paintView);
        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        paintView.init(metrics);
        mHandler = paintView.mHandler;
        /*
        paintView.touchStart(1120, 730);
        paintView.touchMove(1120, 450);
         */
        Bundle extras = getIntent().getExtras();
        if(extras != null){
            try{
                Bitmap target = (Bitmap) getIntent().getParcelableExtra("image");
                if (target == null) {
                    return;
                }
                target = Bitmap.createScaledBitmap(target, metrics.heightPixels - 500, metrics.heightPixels - 500, true);
                //paintView.target = target;
                paintView.setTarget(target);
            } catch(NullPointerException e){
                return;
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater menuInflater = getMenuInflater();
        menuInflater.inflate(R.menu.main, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch(item.getItemId()) {
            /*
            case R.id.normal:
                paintView.normal();
                return true;
            case R.id.emboss:
                paintView.emboss();
                return true;
            case R.id.blur:
                paintView.blur();
                return true;
             */
            case R.id.capture:
                Intent intent = new Intent(this, ShootAndCropActivity.class);
                startActivity(intent);
            case R.id.clear:
                paintView.clear();
                return true;
            case R.id.find:
                if(!paintView.Calibrated)
                    startBT();
                else
                    paintView.Calibrated = false;
                return true;
            case R.id.feedback:
                paintView.getFeedback();
        }

        return super.onOptionsItemSelected(item);
    }

    public void startBT(){
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if(!mBluetoothAdapter.isEnabled()) {
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent, 1);
        }

        BluetoothDevice selectedDevice = null;
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if(pairedDevices.size() > 0)
        {
            for(BluetoothDevice device : pairedDevices)
            {
                if(device.getName().equals("HC-06"))
                {
                    Log.d("DEBUG", "found");
                    selectedDevice = device;
                    break;
                }
            }
        }
        if(selectedDevice == null)
            return;

        BluetoothSocket mmSocket = null;
        try{
            UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
            mmSocket = selectedDevice.createRfcommSocketToServiceRecord(uuid);

            mBluetoothAdapter.cancelDiscovery();
            try{
                mmSocket.connect();
                Log.d("DEBUG", "connected");
            } catch (IOException connectException){
                try {
                    mmSocket.close();
                } catch (IOException closeException){}
            }
        } catch (IOException e) {}

        ConnectedThread cThread = new ConnectedThread(mmSocket);
        cThread.start();
    }

    static class ConnectedThread extends Thread{
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        public ConnectedThread(BluetoothSocket socket){
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e){}

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        StringBuffer sbb = new StringBuffer();

        public String readLine(){
            byte[] readBuffer = new byte[1024];
            int bytes;
            int readBufferPosition = 0;

            try{
                int bytesAvailable = mmInStream.available();
                if(bytesAvailable > 0)
                {
                    byte[] packetBytes = new byte[bytesAvailable];
                    mmInStream.read(packetBytes);
                    for(int i=0;i<bytesAvailable;i++)
                    {
                        byte b = packetBytes[i];
                        if(b == 10)
                        {
                            byte[] encodedBytes = new byte[readBufferPosition];
                            System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                            final String data = new String(encodedBytes, "US-ASCII");
                            readBufferPosition = 0;
                            return data;
                            //mHandler.obtainMessage(0, data).sendToTarget();
                        }
                        else
                        {
                            readBuffer[readBufferPosition++] = b;
                        }
                    }
                }
            } catch(IOException e) {
                return "fail";
            }
            return "fail";
        }

        public void run(){
            byte[] readBuffer = new byte[1024];
            int bytes;
            int readBufferPosition = 0;

            /*
            String data;
            while(true){
                data = readLine();
                if(data != "fail")
                    mHandler.obtainMessage(0, data).sendToTarget();
            }

             */


            while(true){
                try{
                    int bytesAvailable = mmInStream.available();
                    if(bytesAvailable > 0)
                    {
                        byte[] packetBytes = new byte[bytesAvailable];
                        mmInStream.read(packetBytes);
                        for(int i=0;i<bytesAvailable;i++)
                        {
                            byte b = packetBytes[i];
                            if(b == 10)
                            {
                                byte[] encodedBytes = new byte[readBufferPosition];
                                System.arraycopy(readBuffer, 0, encodedBytes, 0, encodedBytes.length);
                                final String data = new String(encodedBytes, "US-ASCII");
                                readBufferPosition = 0;
                                mHandler.obtainMessage(0, data).sendToTarget();
                            }
                            else
                            {
                                readBuffer[readBufferPosition++] = b;
                            }
                        }
                    }
                } catch(IOException e){
                    break;
                }
            }


        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device){
            BluetoothSocket tmp = null;
            mmDevice = device;

            try{
                UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
                tmp = device.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {}
            mmSocket = tmp;
        }

        public void run(){
            mBluetoothAdapter.cancelDiscovery();
            try{
                mmSocket.connect();
            } catch (IOException connectException){
                try {
                    mmSocket.close();
                } catch (IOException closeException){}
            }
            return;
        }
    }

}
