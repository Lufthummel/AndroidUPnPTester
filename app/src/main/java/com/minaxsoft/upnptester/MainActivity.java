package com.minaxsoft.upnptester;

import android.content.Context;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.StringTokenizer;


public class MainActivity extends AppCompatActivity implements AsyncResponse, View.OnClickListener {
    String TAG = "Main";

    TextView log;
    protected boolean isRunning = false;
    Button disc;
    static UPnPDiscovery discover;
    Context ctx;
    MainActivity act;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ctx = this.getApplicationContext();
        act = this;
        log = (TextView)findViewById(R.id.log);
        log.setMovementMethod(new ScrollingMovementMethod());
        log.setText("");

        forceWifi();

        disc = (Button) findViewById(R.id.discover) ;
        disc.setOnClickListener(this);


        Button clear = (Button) findViewById(R.id.clear) ;
        clear.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                log.setText("");
                if (discover != null) {
                    discover.cancelTask();
                }
            }
        });
        //discover = new UPnPDiscovery(this, ctx, log);
        //discover.execute();
    }

    public void processFinish(UPnPDiscovery.UPnPDevice output) {
        disc.setBackgroundColor(Color.RED);
    }


    public void onClick(View v) {
        if (discover != null) {
            discover.cancelTask();
        }
        startDiscovery();
    }
    protected void startDiscovery() {

        try {
            if (discover != null) {
                discover.cancelTask();
            }
            discover = new UPnPDiscovery(this, ctx, log);


            //ensure that we are running the discovery only once
            if (!isRunning) {
                logger("--> start discovery\n");
                disc.setEnabled(false);
                isRunning = true;

                discover.execute();
                try {
                    Thread.sleep(1500);
                    String[] devices = discover.addresses.toArray(new String[discover.addresses.size()]);

                    for (String device : devices) {
                        log.append(device);

                    }

                    discover.cancel(true);

                } catch (InterruptedException e) {
                    log.append("Exception during discovery \n");

                }

                isRunning = false;
                logger("--> finished discovery\n");
                disc.setEnabled(true);
            }
        } catch (Exception e) {
            Log.d(TAG, "error starting discovery");
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
                logger("Connection Manager: Binding Network  " + network.toString() + "\n");
                logger("Bound network " + connection_manager.getActiveNetworkInfo().toString() + "\n");

                connection_manager.bindProcessToNetwork(network);
                //ConnectivityManager.setProcessDefaultNetwork(network);
            }
        });

    }

    protected void logger(String _msg) {

        final String msg = _msg;
        log.post(new Runnable() {
            public void run() {

                log.append(msg);
            }
        });


    }







    public class UPnPDiscovery extends AsyncTask<Void, Void, UPnPDiscovery.UPnPDevice>  {


        public AsyncResponse delegate = null;

        final String TAG = "UPnPDiscovery";

        // XML tag name returned from each device.
        private final  String DEVICE_TYPE = "deviceType";
        private final  String FRIENDLY_NAME = "friendlyName";
        private final  String MANUFACTURER = "manufacturer";
        private final  String MANUFACTURER_URL = "manufacturerURL";
        private final  String MODEL_DESC = "modelDescription";
        private final  String MODEL_NAME = "modelName";
        private final  String MODEL_NUMBER = "modelNumber";
        private final  String UDN = "UDN";
        private final  String URL_BASE = "URLBase";

        HashSet<String> addresses = new HashSet<>();
        Context ctx;
        TextView log;

        Map<String, String> fields;
        UPnPDevice[] devices;

        public UPnPDiscovery(AsyncResponse delegate, Context context, TextView tv) {
            ctx = context;
            log = tv;
            this.delegate = delegate;
        }


        protected void logger(String _msg) {
        /*
        final String msg = _msg;
        log.post(new Runnable() {
            public void run() {

                log.append(msg);
            }
        });

*/
        }

        protected void setDelegate(AsyncResponse delegate) {
            this.delegate = delegate;
        }

        protected void setContext(Context context) {
            this.ctx = context;
        }

        private boolean running = true;

        @Override
        protected void onCancelled() {
            running = false;
        }

        public void cancelTask() {
            running = false;
        }

        @Override
        protected UPnPDevice doInBackground(Void... params) {

            UPnPHttpResponse msg;
            UPnPDevice device = null;

            logger("in doInBackground \n");
            WifiManager wifi = (WifiManager)ctx.getSystemService( ctx.getApplicationContext().WIFI_SERVICE );

            if(wifi != null) {
                logger("Got Wifi Manager \n");

                WifiManager.MulticastLock lock = wifi.createMulticastLock("The Lock");
                lock.acquire();
                logger("Multistate Lock acquired  \n");
                DatagramSocket socket = null;
                for (int i=0; i < 5; i++) {
                    try {

                        InetAddress group = InetAddress.getByName("239.255.255.250");
                        int port = 1900;
                        String query =
                                "M-SEARCH * HTTP/1.1\r\n" +
                                        "HOST: 239.255.255.250:1900\r\n" +
                                        "MAN: \"ssdp:discover\"\r\n" +
                                        "MX: 1\r\n" +
                                        //"ST: urn:schemas-upnp-org:service:AVTransport:1\r\n"+  // Use for Sonos
                                        "ST: ssdp:all\r\n" +  // Use this for all UPnP Devices
                                        "\r\n";

                        logger("before sending discovery " + System.currentTimeMillis() + "\n");
                        socket = new DatagramSocket(port);
                        socket.setReuseAddress(true);
                        //2 seconds timeout
                        socket.setSoTimeout(2000);

                        DatagramPacket dgram = new DatagramPacket(query.getBytes(), query.length(),
                                group, port);
                        Thread.sleep(500);
                        socket.send(dgram);


                        long time = System.currentTimeMillis();
                        long curTime = System.currentTimeMillis();

                        logger("after    sending discovery " + System.currentTimeMillis() + "\n");

                        // Let's consider all the responses we can get in 1 second
                        while (curTime - time < 5000) {
                            DatagramPacket p = new DatagramPacket(new byte[4096], 4096);
                            try {

                                socket.receive(p);
                                String raw = new String(p.getData(), 0, p.getLength());

                                //parse response
                                msg = new UPnPHttpResponse(raw);
                                //check if we have a location & USN

                                String location = msg.getHTTPHeaderField("location");
                                if (location != null || location.trim().length() != 0) {
                                    //get the device info from given location
                                    URL url = new URL(location);
                                    //Log.d(TAG,"location = " + location );
                                    //Log.d(TAG, "NEW device " + url.toString() + "@Host " + url.getHost());

                                    String host = url.getHost();
                                    device = getDeviceInfo(url);
                                    device.setIP(host);


                                    Log.d(TAG, "Model = " + device.getModelName());
                                    Log.d(TAG, "Modelnumber = " + device.getModelNumber());
                                    Log.d(TAG, "Modeldescription = " + device.getModelDescription());
                                    Log.d(TAG, "IP = " + device.getIP());
                                    Log.d(TAG, "------------------------");
                                }


                                logger("Answer received\n" + msg.getHeader() + "\n");
                                curTime = System.currentTimeMillis();
                                if (!running) {
                                    break;
                                }

                            } catch (SocketTimeoutException e) {
                                // resend
                                logger("TimeoutEx  \n" + e.toString());
                                curTime = System.currentTimeMillis();
                            } catch (IllegalArgumentException e) {
                                Log.d(TAG, "ArgEx  \n" + e.toString());
                                curTime = System.currentTimeMillis();
                            } catch (XmlPullParserException e) {
                                Writer writer = new StringWriter();
                                e.printStackTrace(new PrintWriter(writer));
                                String s = writer.toString();
                                Log.d(TAG, "xml parser exception " + s);
                            } catch (IOException e) {
                                Writer writer = new StringWriter();
                                e.printStackTrace(new PrintWriter(writer));
                                String s = writer.toString();
                                Log.d(TAG, "IO exception " + s);
                            } //end catch blocks
                        }
                        logger("finished listening \n" + "\n");
                        socket.close();

                        if (!running) {
                            break;
                        }


                    }  catch (IOException e) {
                        logger("IOEx  \n" + e.toString());
                        Log.d(TAG, "IOEx  \n" + e.toString());
                    } catch (SecurityException e) {
                        logger("SecEx  \n" + e.toString());
                        Log.d(TAG, "SecEx  \n" + e.toString());
                    } catch (IllegalBlockingModeException e) {
                        logger("BlockEx  \n" + e.toString());
                        Log.d(TAG, "BlockEx  \n" + e.toString());
                    } catch (InterruptedException e) {
                        logger("IntEx  \n" + e.toString());
                        Log.d(TAG, "IntEx  \n" + e.toString());
                    } finally {
                        if (socket != null) { socket.close();}
                        Log.d(TAG, "Socket closed in finally");
                    }
                    Log.d(TAG, "#############################executed loop # " + i);
                }
                //end for
                lock.release();
                logger("Lock released  \n");
            } //if wifi manager
            Log.d(TAG, "!!!!!!!!!!!!!!!discovery finished!!");
            return device;
        }


        public String getUrlData(URL url) throws IOException {

            InputStream in;
            BufferedReader reader = null;
            String d;

            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            try {
                if (urlConnection.getResponseCode() == 200) {
                    // Log.d(TAG, "HTTP ok " + urlConnection.getResponseCode());
                    in = urlConnection.getInputStream();
                    d = urlConnection.getResponseMessage();
                    //Log.d(TAG, "response = " + d);
                    StringBuffer buffer = new StringBuffer();
                    if (in == null) {
                        // Nothing to do.
                        return null;
                    }
                    reader = new BufferedReader(new InputStreamReader(in));

                    String line;
                    while ((line = reader.readLine()) != null) {
                        buffer.append(line + "\n");
                    }

                    if (buffer.length() == 0) {
                        // Stream was empty.  No point in parsing.
                        return null;
                    }
                    //Log.d(TAG, "url response = " + buffer.toString());
                    return buffer.toString();
                } else {
                    Log.d(TAG, "HTTP error " + urlConnection.getResponseCode());
                    in = urlConnection.getErrorStream();
                }
            } finally {
                urlConnection.disconnect();
            }
            return null;
        }

        //call the URL from UPnP response to get the device info.
        protected UPnPDevice getDeviceInfo(URL url) throws XmlPullParserException, IOException {

            UPnPDevice device = new UPnPDevice();

            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            factory.setNamespaceAware(true);
            XmlPullParser xpp = factory.newPullParser();

            String response = getUrlData(url);
            InputStream stream = new ByteArrayInputStream(response.getBytes(StandardCharsets.UTF_8.name()));

            xpp.setInput(new InputStreamReader(stream));

            int eventType = xpp.getEventType();
            String currentTagName = null;
            while (eventType != XmlPullParser.END_DOCUMENT) {
                //Log.d(TAG, "Start parsing " + currentTagName);
                if (eventType == XmlPullParser.START_TAG) {
                    currentTagName = xpp.getName();


                    //Log.d(TAG, "TAG NAME " + currentTagName);


                } else if (eventType == XmlPullParser.TEXT) {

                    if (currentTagName != null) {

                        if (currentTagName.equalsIgnoreCase(DEVICE_TYPE)) {
                            device.setDeviceType(xpp.getText());
                        } else if (currentTagName.equalsIgnoreCase(FRIENDLY_NAME)) {
                            device.setFriendlyName(xpp.getText());
                        } else if (currentTagName.equalsIgnoreCase(MANUFACTURER)) {
                            device.setManufacturer(xpp.getText());
                        } else if (currentTagName
                                .equalsIgnoreCase(MANUFACTURER_URL)) {
                            device.setManufacturerURL(xpp.getText());
                        } else if (currentTagName.equalsIgnoreCase(MODEL_DESC)) {
                            device.setModelDescription(xpp.getText());
                        } else if (currentTagName.equalsIgnoreCase(MODEL_NAME)) {
                            device.setModelName(xpp.getText());
                        } else if (currentTagName.equalsIgnoreCase(MODEL_NUMBER)) {
                            device.setModelNumber(xpp.getText());
                        } else if (currentTagName.equalsIgnoreCase(UDN)) {
                            device.setUDN(xpp.getText());
                        } else if (currentTagName.equalsIgnoreCase(URL_BASE)) {
                            device.setUrlBase(xpp.getText());
                        }


                        //Log.d(TAG, "TAG VALUE:  " + xpp.getText());

                        currentTagName = null;
                    }

                }
                eventType = xpp.next();
            }

            return device;

        }

        @Override
        protected void onPreExecute() {

            Log.d(TAG, "----------> Pre Execute  \n");
        }




        @Override
        protected void onPostExecute(UPnPDiscovery.UPnPDevice result) {
            //super.onPostExecute(result);
            Log.d(TAG, "---> Discovery finished, Post Execute  \n");
            delegate.processFinish(result);
        }




        //----------------- Inner classes ------------------------------

        private class UPnPHttpResponse {

            final String TAG = "UPnPHttpResponse";

            private String header;
            private Map<String, String> fields;
            private String body;


            /**
             * Constructor of the response, will try to parse the raw response data
             *
             * @param rawHttpResponse the raw response data
             * @throws IllegalArgumentException if some error occurs during parsing
             */
            protected UPnPHttpResponse(String rawHttpResponse) throws IllegalArgumentException {
                if (rawHttpResponse == null || rawHttpResponse.trim().length() == 0) {
                    throw new IllegalArgumentException("Empty HTTP response message");
                }
                boolean bodyParsing = false;
                StringBuffer bodyParsed = new StringBuffer();
                fields = new HashMap<String, String>();
                String[] lines = rawHttpResponse.split("\\r\\n");
                this.header = lines[0].trim();
                //Log.d(TAG, "Start parsing lines " + lines.length);
                for (int i = 1; i < lines.length; i++) {

                    String line = lines[i];

                    if (line.length() == 0) {
                        // line break before body
                        bodyParsing = true;
                    } else if (bodyParsing) {
                        // we parse the message body
                        //Log.d(TAG, "Processing body line " + line);
                        bodyParsed.append(line).append("\r\n");
                    } else {
                        //Log.d(TAG, "Processing header line " + line);
                        // we parse the header
                        if (line.length() > 0) {
                            int delim = line.indexOf(':');
                            if (delim != -1) {
                                String key = line.substring(0, delim).toUpperCase();
                                String value = line.substring(delim + 1).trim();
                                fields.put(key, value);
                            } else {
                                throw new IllegalArgumentException("Invalid HTTP message header :" + line);
                            }
                        }
                    }
                }
                if (bodyParsing) {
                    Log.d(TAG, "Body " + bodyParsed);
                    body = bodyParsed.toString();
                }
            }

            public String getHeader() {
                return header;
            }

            public String getBody() {
                return body;
            }

            public String getHTTPFieldElement(String fieldName, String elementName)
                    throws IllegalArgumentException {
                String fieldNameValue = getHTTPHeaderField(fieldName);
                if (fieldName != null) {

                    StringTokenizer tokenizer = new StringTokenizer(
                            fieldNameValue.trim(), ",");
                    while (tokenizer.countTokens() > 0) {
                        String nextToken = tokenizer.nextToken().trim();
                        if (nextToken.startsWith(elementName)) {
                            int index = nextToken.indexOf("=");
                            if (index != -1) {
                                return nextToken.substring(index + 1).trim();
                            }
                        }
                    }
                }
                throw new IllegalArgumentException("HTTP element field " + elementName + " is not present");
            }

            public String getHTTPHeaderField(String fieldName)
                    throws IllegalArgumentException {
                String field = (String) fields.get(fieldName.toUpperCase());
                if (field == null) {
                    throw new IllegalArgumentException("HTTP field " + fieldName + " is not present");
                }
                return field;
            }

        }


        //storing the device information

        public class UPnPDevice {

            private static final String TAG = "UPnPDevice";

            private String deviceType;
            private String friendlyName;
            private String manufacturer;
            private String manufacturerURL;

            private String modelDescription;
            private String modelName;
            private String modelNumber;
            private String urlBase;
            private String UDN;

            private String ip;

            public String getDeviceType() {
                return deviceType;
            }

            public void setDeviceType(String deviceType) {
                this.deviceType = deviceType;
            }

            public String getFriendlyName() {
                return friendlyName;
            }

            public void setFriendlyName(String friendlyName) {
                this.friendlyName = friendlyName;
            }

            public String getManufacturer() {
                return manufacturer;
            }

            public void setManufacturer(String manufacturer) {
                this.manufacturer = manufacturer;
            }

            public String getManufacturerURL() {
                return manufacturerURL;
            }

            public void setManufacturerURL(String manufacturerURL) {
                this.manufacturerURL = manufacturerURL;
            }

            public String getModelDescription() {
                return modelDescription;
            }

            public void setModelDescription(String modelDescription) {
                this.modelDescription = modelDescription;
            }

            public String getModelName() {
                return modelName;
            }

            public void setModelName(String modelName) {
                this.modelName = modelName;
            }

            public String getModelNumber() {
                return modelNumber;
            }

            public void setModelNumber(String modelNumber) {
                this.modelNumber = modelNumber;
            }

            public String getUrlBase() {
                return urlBase;
            }

            public void setUrlBase(String urlBase) {
                this.urlBase = urlBase;
            }

            public String getUDN() {
                return UDN;
            }

            public void setUDN(String uDN) {
                UDN = uDN;
            }

            public String getIP() {
                return ip;
            }

            public void setIP(String iP) {
                ip = iP;
                //Log.d(TAG, "IP set to " + ip);
            }

        }


    }
}
