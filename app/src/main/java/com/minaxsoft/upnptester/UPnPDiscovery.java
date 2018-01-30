package com.minaxsoft.upnptester;

import android.app.Activity;
import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.widget.TextView;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.PortUnreachableException;
import java.net.UnknownHostException;
import java.nio.channels.IllegalBlockingModeException;
import java.util.HashSet;

/**
 * @author Bernd Verst(@berndverst)
 */
public class UPnPDiscovery extends AsyncTask
{
    HashSet<String> addresses = new HashSet<>();
    Context ctx;
    TextView log;



    public UPnPDiscovery(Context context, TextView tv) {
        ctx = context;
        log = tv;
    }

    protected void logger(String _msg) {

        final String msg = _msg;
        log.post(new Runnable() {
            public void run() {

                log.append(msg);
            }
        });


    }

    @Override
    protected Object doInBackground(Object[] params) {

        WifiManager wifi = (WifiManager)ctx.getSystemService( ctx.getApplicationContext().WIFI_SERVICE );

        if(wifi != null) {
            logger("Got Wifi Manager \n");

            WifiManager.MulticastLock lock = wifi.createMulticastLock("The Lock");
            lock.acquire();
            logger("Multistate Lock acquired  \n");
            DatagramSocket socket = null;

            try {

                InetAddress group = InetAddress.getByName("239.255.255.250");
                int port = 1900;
                String query =
                        "M-SEARCH * HTTP/1.1\r\n" +
                                "HOST: 239.255.255.250:1900\r\n"+
                                "MAN: \"ssdp:discover\"\r\n"+
                                "MX: 1\r\n"+
                                //"ST: urn:schemas-upnp-org:service:AVTransport:1\r\n"+  // Use for Sonos
                                "ST: ssdp:all\r\n"+  // Use this for all UPnP Devices
                                "\r\n";

                logger("before sending discovery " + System.currentTimeMillis() + "\n");
                socket = new DatagramSocket(port);
                socket.setReuseAddress(true);

                DatagramPacket dgram = new DatagramPacket(query.getBytes(), query.length(),
                        group, port);
                Thread.sleep(500);
                socket.send(dgram);



                long time = System.currentTimeMillis();
                long curTime = System.currentTimeMillis();

                logger("after    sending discovery " + System.currentTimeMillis() + "\n");

                // Let's consider all the responses we can get in 1 second
                while (curTime - time < 1000) {
                    DatagramPacket p = new DatagramPacket(new byte[1024], 1024);
                    socket.receive(p);

                    String s = new String(p.getData(), 0, p.getLength());

                    logger("Answer received\n" + s +  "\n");
                    /*if (s.toUpperCase().equals("HTTP/1.1 200")) {
                        addresses.add(p.getAddress().getHostAddress());
                    }
                    */


                    curTime = System.currentTimeMillis();
                }


            } catch (IOException e) {
                logger("IOEx  \n" + e.toString());
            } catch (SecurityException e) {
                logger("SecEx  \n" + e.toString());
            } catch (IllegalBlockingModeException e) {
                logger("BlockEx  \n" + e.toString());
            } catch (IllegalArgumentException e) {
                logger("ArgEx  \n" + e.toString());
            } catch (InterruptedException e) {
                logger("IntEx  \n" + e.toString());
            }

            finally {

                socket.close();
                logger("Socket closed  \n");
            }
            lock.release();
            logger("Lock released  \n");
        }
        return null;
    }


}