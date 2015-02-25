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

    Thread thread;
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

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                Log.d("prereturn", "thread");
                while(b){
                    try {

                        Process p = Runtime.getRuntime().exec("su");
                        DataOutputStream dos = new DataOutputStream(p.getOutputStream());
                        dos.writeBytes("screencap -p /sdcard/screenshot/picture.png\n"); // 押す
                        dos.flush();
                        SystemClock.sleep(2000);
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

                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    image.compress(Bitmap.CompressFormat.PNG, 100, bos);
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
                    SystemClock.sleep(2000);
                }

            }
        });
        thread.start();
        return START_STICKY;
    }


}
