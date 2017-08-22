package kr.ac.kaist.csmacn;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.Handler;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.nononsenseapps.filepicker.FilePickerActivity;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

public class MainActivity extends AppCompatActivity {

    private Uri targetFilePath = null;
    private TransmissionManager transmissionManager;

    ProgressDialog connectDialog;

    private Button videoSelectButton;
    private Button sendButton;
    private TextView videoTextView;
    private TextView optionTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        videoSelectButton = (Button) findViewById(R.id.activity_main_button_video);
        videoTextView = (TextView) findViewById(R.id.activity_main_textview_video);
        sendButton = (Button) findViewById(R.id.activity_main_button_send);
        optionTextView = (TextView) findViewById(R.id.activity_main_textview_option);

        videoSelectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(MainActivity.this, FilePickerActivity.class);
                intent.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
                intent.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, false);
                intent.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);
                intent.putExtra(FilePickerActivity.EXTRA_START_PATH,
                        Environment.getExternalStorageDirectory().getPath());
                startActivityForResult(intent, 0);
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                videoSelectButton.setEnabled(false);
                sendButton.setEnabled(false);
                findViewById(R.id.activity_main_linearlayout_status).setVisibility(View.VISIBLE);

                optionTextView.setText("Simulation Settings\n\n");

                connectDialog = new ProgressDialog(MainActivity.this);
                connectDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                connectDialog.setMessage("Conecting to the server...");
                connectDialog.setCancelable(false);
                connectDialog.show();
                try {
                    transmissionManager = new TransmissionManager(MainActivity.this);
                    transmissionManager.connect("192.168.43.1", 5000, targetFilePath.getLastPathSegment());
                } catch (IOException e) {}
            }
        });
    }

    void setSimulationOptionText(boolean hiddenTerminal, boolean csmacn){
        TextView textView = (TextView) findViewById(R.id.activity_main_textview_option);
        String string = "Simulation Settings\n";
        if(hiddenTerminal){
            string += "Hidden terminal [on]\n";
            if(csmacn) string += "CSMA/CN [on]";
            else string += "CSMA/CN [off]";
        }
        else string += "Hidden terminal [off]\n";
        textView.setText(string);
    }

    void onSuccessToConnect() {
        connectDialog.dismiss();
        Snackbar.make(findViewById(android.R.id.content),
                "Connected to the server. Start transferring.",
                Snackbar.LENGTH_LONG)
                .show();
        try {
            transmissionManager.sendFile(targetFilePath);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void onFailToConnect() {
        connectDialog.dismiss();
        findViewById(R.id.activity_main_linearlayout_status).setVisibility(View.INVISIBLE);
        new AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("Cannot connect to the server.\nAbort sending the file.")
                .setNeutralButton("Dismiss", null)
                .show();
        videoSelectButton.setEnabled(true);
        sendButton.setEnabled(true);
        transmissionManager = null;
        System.gc();
    }

    void onSuccessToTransfer() {
        Snackbar.make(findViewById(android.R.id.content),
                "Transfer completed.",
                Snackbar.LENGTH_LONG)
                .show();
        videoSelectButton.setEnabled(true);
        sendButton.setEnabled(true);
        transmissionManager = null;
        System.gc();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode != 0 || resultCode != RESULT_OK) return;

        targetFilePath = data.getData();
        videoTextView.setText(targetFilePath.toString());
        sendButton.setEnabled(true);
    }
}
