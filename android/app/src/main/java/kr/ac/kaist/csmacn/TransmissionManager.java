package kr.ac.kaist.csmacn;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.net.Uri;
import android.os.Handler;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.GregorianCalendar;

/**
 * Created by jeffreychang on 2016. 5. 7..
 */
public class TransmissionManager {
    private static final String TAG = "TransmissionManager";

    private static final int maxCwnd = 500;
    static final int packetSize = 1024;
    private DatagramSocket socket = null;

    private final MainActivity activity;

    private final BLEManager bleManager;

    private FileManager fileManager = null;
    private long fileSize;

    private final ProgressBar progressBar;
    private final TextView progressStatusTextView;
    private final TextView speedTextView;
    private final TextView infoTextView;
    private final TextView timeTextView;

    private boolean hiddenTerminal;
    private boolean csmacn;

    public TransmissionManager(MainActivity activity) throws SocketException {
        this.activity = activity;
        cwnd = 5;
        isCongestionDetected = false;

        progressBar = (ProgressBar) activity.findViewById(R.id.activity_main_progressbar_progress);
        progressStatusTextView = (TextView) activity.findViewById(R.id.activity_main_textview_progress);
        speedTextView = (TextView) activity.findViewById(R.id.activity_main_textview_speed);
        infoTextView = (TextView) activity.findViewById(R.id.activity_main_textview_info);
        timeTextView = (TextView) activity.findViewById(R.id.activity_main_textview_time);

        bleManager = new BLEManager(activity, TransmissionManager.this);
    }

    private InetAddress inetAddress;
    private int port;

    public void connect(final String address, final int port, final String fileName) throws IOException {

        progressBar.setIndeterminate(true);
        speedTextView.setText("");
        infoTextView.setText("");
        progressStatusTextView.setText("Preparring...");
        timeTextView.setText("00:00");

        socket = new DatagramSocket();
        socket.setSoTimeout(1000);
        this.inetAddress = InetAddress.getByName(address);
        this.port = port;
        if(!bleManager.isBluetoothOn()){
            handler.post(new Runnable() {
                @Override
                public void run() {
                    activity.onFailToConnect();
                    new AlertDialog.Builder(activity)
                            .setTitle("Bluetooth Error")
                            .setMessage("Bluetooth seems off. Please turn on Bluetooth.")
                            .setNeutralButton("Dismiss", null)
                            .show();
                }
            });
            return;
        }

        new Thread(){
            @Override
            public void run() {
                long startTime = 0, endTime = 0;
                int trialTime = 0;
                try{
                    JSONObject json = new JSONObject();
                    json.put("type", "connect");
                    json.put("filename", fileName);
                    byte[] data = json.toString().getBytes();
                    byte[] ackData = new byte[128];
                    final DatagramPacket packet = new DatagramPacket(data, data.length, inetAddress, port);
                    final DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length);
                    while(true){
                        try{
                            socket.receive(ackPacket);
                            endTime = System.currentTimeMillis();
                            break;
                        } catch (SocketTimeoutException e){
                            if(trialTime > 4) {
                                handler.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        activity.onFailToConnect();
                                    }
                                });
                                throw new IOException("Server does not respond");
                            }
                            Log.i(TAG, "Waiting for ACK from the server...");
                            startTime = System.currentTimeMillis();
                            socket.send(packet);
                            trialTime++;
                        }
                    }
                    // handshake established
                    rtt = endTime - startTime;
                    ertt = rtt;
                    drtt = ertt;
                    timeoutInterval = ertt + (drtt * 4);
                    // timeoutInterval = 150;
                    socket.setSoTimeout(0);
                    json = new JSONObject(new String(ackPacket.getData(), ackPacket.getOffset(), ackPacket.getLength()));
                    if(!json.getBoolean("ack")) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                activity.onFailToConnect();
                            }
                        });
                        throw new IOException("Server reject the request");
                    }
                    hiddenTerminal = json.getBoolean("hiddenterminal");
                    csmacn = json.getBoolean("csmacn");
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            activity.setSimulationOptionText(hiddenTerminal, csmacn);
                        }
                    });
                    Log.i(TAG, "Handshaking completed. RTT : " + rtt + " ms, timeout : " + timeoutInterval);
                    if(hiddenTerminal && csmacn){
                        // need BLE establishment
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                bleManager.startBleAdvertising();
                                activity.connectDialog.setMessage("Establishing BLE connection...");
                            }
                        });
                    }
                    // no hidden terminal set or csmacn off: not need BLE establishment
                    else{
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                activity.onSuccessToConnect();
                            }
                        });
                    }
                } catch (IOException e){
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private Thread updateProgressThread, receiveAckThread, dataTransferThread, timerThread;
    private final Handler handler = new Handler();

    private int maximumSequence;
    private int startIndex, endIndex;
    private float cwnd = 5;
    private boolean isCongestionDetected = false;
    private long rtt;

    private int rttMearsuingSeq = 0;
    private boolean isRttMeasuring = false;

    private float ertt, drtt, timeoutInterval;

    private long packetSendingStartTime, packetSendingEndTime;

    private int transferTime;

    public void collisionNotified() throws InterruptedException {
        Thread.sleep(5);
        packetSendingStartTime = System.currentTimeMillis() - 5;
        endIndex = startIndex;
    }

    public void sendFile(Uri filePath) throws IOException, URISyntaxException {
        fileManager = new FileManager(filePath);
        fileSize = fileManager.getFileSizeInBytes();
        maximumSequence = (int)(fileSize / packetSize) + 1;
        startIndex = 0;
        endIndex = 0;

        progressBar.setIndeterminate(false);
        progressBar.setMax(maximumSequence);
        progressBar.setProgress(0);

        transferTime = 0;

        Log.i(TAG, maximumSequence + " number of packets will be sent");
        final FileWriter fileWriter = fileManager.getLogFileStream();
        final SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss.SSS");
        fileWriter.write("time,swnd,rtt,timeout interval,speed,,");
        fileWriter.write("hiddenTerminal " + (hiddenTerminal? "on" : "off"));
        if(hiddenTerminal) fileWriter.write(",csmacn " + (csmacn? "on":"off"));
        fileWriter.write("\n");

        updateProgressThread = new Thread(){
            int previousAckedPacketSequence = 0;
            int i = 0;

            @Override
            public void run() {
                super.run();
                while(!isInterrupted()){
                    try {
                        Thread.sleep(100);
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                i++;
                                progressBar.setProgress(startIndex);
                                try {
                                    fileWriter.write(sdf.format(new GregorianCalendar().getTime()) + "," + cwnd + "," +
                                            rtt + ", " + timeoutInterval + "," + (startIndex - previousAckedPacketSequence) * packetSize + "\n");
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                if(i == 10){
                                    speedTextView.setText(getReadableByteCount((startIndex - previousAckedPacketSequence) * packetSize) + "/s");
                                    i = 0;
                                    previousAckedPacketSequence = startIndex;
                                }
                                progressStatusTextView.setText(getReadableByteCount(startIndex * packetSize) + " / " + getReadableByteCount(fileSize));
                                infoTextView.setText("swnd size: " + cwnd + "\n"
                                        + "swnd: [" + startIndex + " - " + endIndex + "]\n"
                                        + "RTT: " + rtt + " ms\n"
                                        + "timeout interval: " + timeoutInterval + " ms");

                            }
                        });
                    } catch (InterruptedException e) {
                        Log.i(TAG, "updateProgressThread is interrupted.");
                        fileManager.closeFileWriter(fileWriter);
                        return;
                    }
                }
            }
        };

        receiveAckThread = new Thread(){
            byte[] buffer = new byte[64];
            DatagramPacket packet = new DatagramPacket(buffer, 64);

            @Override
            public void run() {
                super.run();
                while(startIndex < maximumSequence){
                    try {
                        socket.setSoTimeout((int) timeoutInterval);
                        socket.receive(packet);
                        JSONObject json = new JSONObject(new String(packet.getData(), packet.getOffset(), packet.getLength()));
                        int ack = json.getInt("ack");

                        // cumulative ACK received in-order
                        if(ack > startIndex){
                            // increase cwnd
                            if(isCongestionDetected) cwnd += 1/cwnd;
                            else cwnd++;
                            if(cwnd > maxCwnd) cwnd = maxCwnd;
                            startIndex = ack;
                        }
                        if(isRttMeasuring && json.getInt("seq") >= rttMearsuingSeq){
                            packetSendingEndTime = System.currentTimeMillis();
                            rtt = packetSendingEndTime - packetSendingStartTime;
                            ertt = 0.875f * ertt + 0.125f * rtt;
                            drtt = 0.75f * drtt + 0.25f * ertt;
                            timeoutInterval = ertt + drtt * 4;
                            isRttMeasuring = false;
                        }
                        // else ignore.
                    } catch (SocketTimeoutException e) {
                        isCongestionDetected = true;
                        cwnd /= 2;
                        endIndex = startIndex;
//                        Log.i(TAG, "timeout");
                        packetSendingEndTime = System.currentTimeMillis();
                        rtt = packetSendingEndTime - packetSendingStartTime;
                        ertt = 0.875f * ertt + 0.125f * rtt;
                        drtt = 0.75f * drtt + 0.25f * ertt;
                        timeoutInterval = ertt + drtt * 4;
                        isRttMeasuring = false;
//                        Log.i(TAG, "RTT : " + rtt + ", timeout interval: " + timeoutInterval);
                    } catch (SocketException e) {
                        e.printStackTrace();
                    } catch (IOException e) {
                        e.printStackTrace();
                    } catch (JSONException e) {
                        e.printStackTrace();
                    } finally {
                        if(cwnd < 1) cwnd = 1;
                        //Log.i(TAG, "window [" + startIndex + " - " + endIndex + "], cwnd = " + cwnd);
                    }
                }

                // finish
                Log.i(TAG, "Transmission finished. interrupt the other threads");
                updateProgressThread.interrupt();
                dataTransferThread.interrupt();
                timerThread.interrupt();
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            finishTransfer();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                });
                return;
            }
        };

        dataTransferThread = new Thread(){
            @Override
            public void run() {
                super.run();
                byte[] data = new byte[1024];
                for(int i=0; i<1024; i++) data[i] = '0';
                while(!isInterrupted()){
                    while(endIndex - startIndex < cwnd){
                        JSONObject json = new JSONObject();
                        try {
                            json.put("seq", endIndex);
                            json.put("type", "data");
                            json.put("data", Base64.encodeToString(data, Base64.DEFAULT));
                            byte[] buffer = json.toString().getBytes();
                            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, inetAddress, port);
                            socket.send(packet);
                            if(!isRttMeasuring){
                                packetSendingStartTime = System.currentTimeMillis();
                                rttMearsuingSeq = endIndex;
                                isRttMeasuring = true;
                            }
                            endIndex++;
                        } catch (JSONException e) {
                            e.printStackTrace();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
                Log.i(TAG, "dataTransferThread is interrupted.");
                return;
            }
        };

        timeTextView.setText("00:00");
        timerThread = new Thread(){
            @Override
            public void run() {
                super.run();
                try{
                    while(!isInterrupted()){
                        Thread.sleep(1000);
                        transferTime++;
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                int minute = transferTime / 60;
                                int second = transferTime % 60;
                                timeTextView.setText(String.format("%02d:%02d", minute, second));
                            }
                        });
                    }
                } catch (InterruptedException e) {
                    Log.i(TAG, "timerThread is interrupted.");
                    return;
                }
            }
        };

        updateProgressThread.start();
        receiveAckThread.start();
        dataTransferThread.start();
        timerThread.start();
    }

    private void finishTransfer() throws IOException {
        activity.findViewById(R.id.activity_main_linearlayout_status).setVisibility(View.INVISIBLE);
        final ProgressDialog dialog = new ProgressDialog(activity);
        dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        dialog.setMessage("Disconnecting..");
        dialog.setCancelable(false);
        dialog.show();

        new Thread(){
            @Override
            public void run() {
                try{
                    JSONObject json = new JSONObject();
                    json.put("type", "disconnect");
                    byte[] data = json.toString().getBytes();
                    byte[] ackData = new byte[5];
                    final DatagramPacket packet = new DatagramPacket(data, data.length, inetAddress, port);
                    final DatagramPacket ackPacket = new DatagramPacket(ackData, ackData.length);
                    while(true){
                        try{
                            socket.receive(ackPacket);
                            String text = new String(ackPacket.getData(), ackPacket.getOffset(), ackPacket.getLength());
                            if(text.equals("ack")) break;
                        } catch (SocketTimeoutException e){
                            Log.i(TAG, "Waiting for disconnect ACK from the server...");
                            socket.send(packet);
                        }
                    }
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            dialog.dismiss();
                            activity.onSuccessToTransfer();
                        }
                    });
                } catch (IOException e){
                    e.printStackTrace();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }


    private String getReadableByteCount(long size){
        int unit = 1024;
        if (size < unit) return size + " B";
        int exp = (int) (Math.log(size) / Math.log(unit));
        String pre = ("KMGTPE").charAt(exp-1) + "";
        return String.format("%.2f %sB", size / Math.pow(unit, exp), pre);
    }

}
