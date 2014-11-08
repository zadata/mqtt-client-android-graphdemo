package com.zadata.graphdemo;

import java.io.IOException;
import java.net.Socket;
import java.util.Date;

import org.achartengine.chart.TimeChart;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;

public class ZADataGraphDemoActivity extends Activity {
    
    static final string MQTT_USER = "your_mqtt_username";
    static final string MQTT_PWD  = "your_mqtt_password";
    static final int  MQTT_KEEP_ALIVE_IN_SECS = 25;
    static final long TIME_SLICE_IN_SECS = 120;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final GraphView gv = new GraphView(this);
        Handler handler = new Handler() {
            public void handleMessage(Message msg) {
                Bundle bundle = msg.getData();
                gv.addPoint(Double.parseDouble(bundle.getString("MSG")));
            }
        };
        //Log.i("GRAPH", "onCreate");
        //tv.setText("Hello, Android");
        //setContentView(tv);
        new Thread(new MqttThread(handler)).start();
        setContentView(gv);
    }
}

class GraphView extends View {
    TimeChart tc;
    TimeSeries series;
    XYMultipleSeriesRenderer renderers;
    XYSeriesRenderer renderer;

    public GraphView(Context context) {
        super(context);
        XYMultipleSeriesDataset ds = new XYMultipleSeriesDataset();
        series = new TimeSeries("quote");
        ds.addSeries(series);
        renderers = new XYMultipleSeriesRenderer();
        renderer = new XYSeriesRenderer();
        renderer.setColor(Color.GREEN);
        renderer.setLineWidth(5.0f);
        renderer.setFillBelowLine(true);
        renderer.setFillBelowLineColor(Color.green(64));
        renderers.addSeriesRenderer(renderer);
        tc = new TimeChart(ds, renderers);
    }

    void addPoint(double y) {
        long curTime = System.currentTimeMillis();
        //Log.i("GRAPH", "invalidate");
        series.add(new Date(curTime), y);
        while (series.getItemCount() > 0 && series.getX(0) < curTime - ZADataGraphDemoActivity.TIME_SLICE_IN_SECS * 1000) {
            series.remove(0);
        }
        this.invalidate();
    }

    protected void onDraw(Canvas canvas) {
        Paint paint = new Paint();
        paint.setColor(0xFFFF0000);
        tc.draw(canvas, 0, 0, canvas.getWidth(), canvas.getHeight(), paint);
        Double []locs = renderers.getXTextLabelLocations();
        Log.i("GRAPH", "locs.length is " + locs.length);
        Log.i("GRAPH", "xlabels is " + renderers.getXLabels());
        for (Double l : locs)
            Log.i("GRAPH", l.toString());
        //Log.i("GRAPH", "redraw");
    }
}


class NetworkTestThread implements Runnable {
    Handler handler;
    private Bundle bundle = new Bundle();

    public NetworkTestThread(Handler handler) {
        this.handler = handler;
    }

    public void run() {
        try {
            new Socket("mqtt.zadata.com", 1883);
            updateText("Connected via plain socket");
        }
        catch (IOException ee) {
            updateText("Failed3 via plain socket: " + ee.getMessage());             
        }
    }

    void updateText(String str) {
        Message m = Message.obtain();
        bundle.clear();
        bundle.putString("MSG", str);
        m.setData(bundle);
        handler.sendMessage(m);
    }
}

class MqttThread implements MqttCallback, Runnable {
    Handler handler;
    MqttClient client;
    
    static final int RECONNECT_INTERVAL_MS = 1000;

    public MqttThread(Handler handler) {
        this.handler = handler;
    }
    
    void connectClient() {
        try {
            /* check which mqtt client line fails */
            /*SocketFactory factory = SocketFactory.getDefault();
            try {
                Socket so = factory.createSocket("mqtt.zadata.com", 1883);
                so.setTcpNoDelay(true);
            } catch (Exception e) {
                Log.i("GRAPH", e.toString());
                for (StackTraceElement elem : e.getStackTrace()) {
                    Log.i("GRAPH", elem.toString());
                }               
            }*/
            MqttClient client = new MqttClient("tcp://mqtt.zadata.com:1883", "paho000", null);
            client.setCallback(this);
            MqttConnectOptions opts = new MqttConnectOptions();
            opts.setKeepAliveInterval(ZADataGraphDemoActivity.MQTT_KEEP_ALIVE_IN_SECS);
            opts.setUserName(MQTT_USER);
            opts.setPassword(MQTT_PWD.toCharArray());
            client.connect(opts);
            client.subscribe("quotes-sim/data/exchange/NYSE/ticker/SPY/price", 0);      
        } catch (MqttException e) {
            //updateText("Failed to connect mqtt: " + e.getReasonCode() + " " + e.getCause());
            for (StackTraceElement elem : e.getStackTrace()) {
                Log.i("GRAPH", elem.toString());
            }
            try { Thread.sleep(RECONNECT_INTERVAL_MS); } catch (InterruptedException ee) {};
            //updateText("Retrying...");
            connectClient();
        }       
    }
    
    public void run(){
        connectClient();
    }
    
    @Override
    public void connectionLost(Throwable arg0) {
        //updateText("Connection lost");
        try { Thread.sleep(RECONNECT_INTERVAL_MS); } catch (InterruptedException e) {};
        //updateText("Reconnecting...");
        connectClient();
    }

    @Override
    public void deliveryComplete(MqttDeliveryToken arg0) {
    }

    @Override
    public void messageArrived(MqttTopic arg0, MqttMessage arg1)
            throws Exception {
        //Log.i("GRAPH", "Got value " + arg1.toString());
        updateText(arg1.toString());
    }

    void updateText(String str) {
        Message m = Message.obtain();
        Bundle bundle = new Bundle();
        bundle.clear();
        bundle.putString("MSG", str);
        m.setData(bundle);
        handler.sendMessage(m);
    }
}
