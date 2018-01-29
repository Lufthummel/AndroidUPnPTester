package com.minaxsoft.upnptester;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


public class MainActivity extends AppCompatActivity {

    TextView log;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        log = (TextView)findViewById(R.id.log);
        log.setMovementMethod(new ScrollingMovementMethod());
        log.setText("");

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

    }
}
