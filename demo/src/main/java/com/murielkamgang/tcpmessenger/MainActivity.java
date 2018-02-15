package com.murielkamgang.tcpmessenger;

import android.app.ProgressDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.Toast;

import com.murielkamgang.network.SocketConfig;
import com.murielkamgang.network.TCPMessenger;

public class MainActivity extends AppCompatActivity implements TCPMessenger.Callback<String> {

    private ProgressDialog progressDialog;
    private Button button;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        button = findViewById(R.id.button_test);

        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (progressDialog == null) {
                    progressDialog = new ProgressDialog(MainActivity.this);
                    progressDialog.setIndeterminate(true);
                    progressDialog.setMessage("Processing...");
                }

                progressDialog.show();
                TCPMessenger
                        .getDefaultInstance()
                        .sendCommand(new TCPMessenger.Request("192.168.1.1", "Hello"), String.class, MainActivity.this);
            }
        });


    }

    @Override
    public void onResponse(TCPMessenger.Request request, String s) {
        progressDialog.dismiss();
        Toast.makeText(this, "Response: " + s + " for request: " + request, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onError(TCPMessenger.Request request, Throwable throwable) {
        progressDialog.dismiss();
        Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
    }
}
