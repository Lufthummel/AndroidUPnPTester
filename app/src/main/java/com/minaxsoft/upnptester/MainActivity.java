package com.minaxsoft.upnptester;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity {

    TextView log;
    protected boolean isRunning = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);


        log = (TextView)findViewById(R.id.log);
        log.setMovementMethod(new ScrollingMovementMethod());
        log.setText("");

        forceWifi();

        Button discover = (Button) findViewById(R.id.discover) ;
        discover.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        startDiscovery();
                    }
                });

            }
        });

        Button clear = (Button) findViewById(R.id.clear) ;
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                log.setText("");
            }
        });
    }

    protected void startDiscovery() {

        //ensure that we are running the discovery only once
        if (!isRunning) {
            isRunning = true;
            UPnPDiscovery discover = new UPnPDiscovery(this.getApplicationContext(), log);
            discover.execute();
            try {
                Thread.sleep(1500);
                String[] devices = discover.addresses.toArray(new String[discover.addresses.size()]);

                for (String device : devices) {
                    log.append(device);
                }

            } catch (InterruptedException e) {
                log.append("Exception during discovery \n");

            }
            isRunning = false;
            log.append("finished discovery");
        }



    }

    public void forceWifi() {
        final ConnectivityManager connection_manager =
                (ConnectivityManager) this.getApplication().getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkRequest.Builder request = new NetworkRequest.Builder();
        request.addTransportType(NetworkCapabilities.TRANSPORT_WIFI);
        request.removeTransportType(NetworkCapabilities.TRANSPORT_CELLULAR);
        request.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED);

        log.append("Current Active network " + connection_manager.getActiveNetworkInfo().toString() + "\n");

        connection_manager.registerNetworkCallback(request.build(), new ConnectivityManager.NetworkCallback() {

            @Override
            public void onAvailable(Network network) {
                log.append("Connection Manager: Network Available " + network.toString() + "\n");

                connection_manager.bindProcessToNetwork(network);
                //ConnectivityManager.setProcessDefaultNetwork(network);
            }
        });

    }
}
