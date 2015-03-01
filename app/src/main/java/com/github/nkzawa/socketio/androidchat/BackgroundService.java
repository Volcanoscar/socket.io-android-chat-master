package com.github.nkzawa.socketio.androidchat;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;

/**
 * Created by serious_hiropon on 2015/02/25.
 */
public class BackgroundService extends Service {

    Thread sendthread;
    Thread receivethread;
    boolean b = true;
    private Socket mSocket;


    {
        try {
            mSocket = IO.socket("http://157.7.222.223:3000");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        try {
            mSocket = IO.socket("http://157.7.222.223:3000");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        mSocket.on("S_to_C_message", onNewMessage);
        mSocket.connect();
        sendthread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d("prereturn", "thread");
                while(b){
                    try {

                        Process p = Runtime.getRuntime().exec("su");
                        DataOutputStream dos = new DataOutputStream(p.getOutputStream());
                        dos.writeBytes("screencap -p /sdcard/screenshot/picture.png\n"); // 押す
                        dos.flush();
                        SystemClock.sleep(1000);
                        p.destroy();
                        Log.d("prereturn", "aftersu");

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    String filepath = Environment.getExternalStorageDirectory().getPath() + "/screenshot/picture.png";

                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(filepath, options);

                    int scaleW = options.outWidth / 760 + 1;
                    int scaleH = options.outHeight / 840  + 1;

                    int scale = Math.max(scaleW, scaleH);

                    options.inJustDecodeBounds = false;

                    options.inSampleSize = scale;

                    Bitmap image = BitmapFactory.decodeFile(filepath, options);
                    Log.d("prereturn",filepath );
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    if(image != null){
                        image.compress(Bitmap.CompressFormat.PNG, 100, bos);
                    }
                    byte[] bArray = bos.toByteArray();
                    String image64 = Base64.encodeToString(bArray, Base64.DEFAULT);
                    //String imageBinary = "data:image/png;base64,"+image64;
                    String imageBinary = "data:image/png;base64,"+image64;
                    Log.d("prereturn", "afteredit");

                    JSONObject json = new JSONObject();
                    try {
                        json.put("value", imageBinary);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                    mSocket.emit("C_to_S_broadcast", json);
                    //SystemClock.sleep(1000);
                }

            }
        });
        sendthread.start();
        return START_REDELIVER_INTENT;
    }

    private Emitter.Listener onNewMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
             receivethread = new Thread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    String message;
                    String[] array = new String[30];
                    int ABS_MT_TRACKING_ID = 1;

                    try {
                        //username = data.getString("username");//送信側で未定義のためコメントアウト
                        message = data.getString("value");
                    } catch (JSONException e) {
                        return;
                    }
                    //StringBuilder sb = new StringBuilder();

                    /*String message1 = "sendevent /dev/input/event0 3 57 93\n" +
                            "sleep 0.000\n" +
                            "sendevent /dev/input/event0 3 58 29\n" +
                            "sleep 0.000\n" +
                            "sendevent /dev/input/event0 3 53 956\n" +
                            "sleep 0.000\n" +
                            "sendevent /dev/input/event0 3 54 2180\n" +
                            "sleep 0.000\n" +
                            "sendevent /dev/input/event0 0 0 0\n" +
                            "sleep 0.000\n" +
                            "sendevent /dev/input/event0 3 57 4294967295\n" +
                            "sleep 0.064\n" +
                            "sendevent /dev/input/event0 0 0 0\n" +
                            "sleep 0.000";*/

                    double ratio = 1.2;
                    String crlf = System.getProperty("line.separator");
                    //array = message.split("\n");
                    array = message.split(crlf);
                    String x = array[0];
                    int xx = Integer.parseInt(x,16);
                    //int xx = xx * 2;
                    String xxx = Integer.toString((int) (xx * ratio));
                    String y = array[1];
                    int yy = Integer.parseInt(y,16);
                    //int yyyy = yy * 2;
                    String yyy = Integer.toString((int) (yy * ratio));

                    String message1 = "sendevent /dev/input/event0 3 57 " + ABS_MT_TRACKING_ID + "\n" +
                            "sleep 0.000\n" +
                            "sendevent /dev/input/event0 3 48 10\n" +
                            "sleep 0.000\n" +
                            "sendevent /dev/input/event0 3 58 29\n" +
                            "sleep 0.000\n" +
                            "sendevent /dev/input/event0 3 53 " + xxx + "\n" +
                            "sleep 0.000\n" +
                            "sendevent /dev/input/event0 3 54 " + yyy + "\n" +
                            "sleep 0.000\n" +
                            "sendevent /dev/input/event0 0 0 0\n" +
                            "sleep 0.000\n" +
                            "sendevent /dev/input/event0 3 57 4294967295\n" +
                            "sleep 0.064\n" +
                            "sendevent /dev/input/event0 0 0 0\n" +
                            "sleep 0.000\n";



                    Process p = null;

                    try {
                        p = Runtime.getRuntime().exec("su");
                        DataOutputStream dos = new DataOutputStream(p.getOutputStream());
                        //for(int i=0;i<array.length;i++){



                        //dos.writeBytes(array[i]);
                        //dos.writeBytes(message);//messageだと実行される
                        dos.writeBytes(message1);
                        //dos.flush();
                        //Toast.makeText(getActivity().getApplicationContext(),array[i], Toast.LENGTH_LONG).show();



                        //}// 押す
                        dos.flush();
                        dos.close();
                    } catch (IOException e) {
                        //Toast.makeText(getActivity().getApplicationContext(), "er", Toast.LENGTH_LONG).show();
                        e.printStackTrace();
                    }



                    //removeTyping(username);//送信側で未定義のためコメントアウト
                    //addMessage("anonymous"/*username*/,message1);//messageだと実行される
                    ABS_MT_TRACKING_ID++;
                }
            });
        }
    };
}
