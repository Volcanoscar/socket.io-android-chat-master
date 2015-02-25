package com.github.nkzawa.socketio.androidchat;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.IO;
import com.github.nkzawa.socketio.client.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

//import org.apache.commons.codec.binary.Base64;
//import org.apache.commons.codec.binary.Hex;
import android.util.Base64;

/**
 * A chat fragment containing messages view and input form.
 */
public class MainFragment extends Fragment {

    private static final int REQUEST_LOGIN = 0;

    private static final int TYPING_TIMER_LENGTH = 600;

    private RecyclerView mMessagesView;
    private EditText mInputMessageView;
    private List<Message> mMessages = new ArrayList<Message>();
    private RecyclerView.Adapter mAdapter;
    private boolean mTyping = false;
    private Handler mTypingHandler = new Handler();
    private String mUsername = "hiroshi";
    private Socket mSocket;
    String path;
    FileInputStream fis;
    byte[] bytes;
    ByteBuffer buffer;
    String string_utf8;
    String[] array = new String[50];
    int ABS_MT_TRACKING_ID = 1;
    Intent intent;
    {
        try {
            mSocket = IO.socket("http://157.7.222.223:3000");
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    public MainFragment() {
        super();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mAdapter = new MessageAdapter(activity, mMessages);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);

        mSocket.on(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.on(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        //mSocket.on("new message", onNewMessage);
        mSocket.on("S_to_C_message", onNewMessage);
        mSocket.on("user joined", onUserJoined);
        mSocket.on("user left", onUserLeft);
        mSocket.on("typing", onTyping);
        mSocket.on("stop typing", onStopTyping);
        mSocket.connect();

        //startSignIn();


    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_main, container, false);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mSocket.disconnect();
        mSocket.off(Socket.EVENT_CONNECT_ERROR, onConnectError);
        mSocket.off(Socket.EVENT_CONNECT_TIMEOUT, onConnectError);
        mSocket.off("new message", onNewMessage);
        mSocket.off("user joined", onUserJoined);
        mSocket.off("user left", onUserLeft);
        mSocket.off("typing", onTyping);
        mSocket.off("stop typing", onStopTyping);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mMessagesView = (RecyclerView) view.findViewById(R.id.messages);
        mMessagesView.setLayoutManager(new LinearLayoutManager(getActivity()));
        mMessagesView.setAdapter(mAdapter);

        mInputMessageView = (EditText) view.findViewById(R.id.message_input);
        mInputMessageView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int id, KeyEvent event) {
                if (id == R.id.send || id == EditorInfo.IME_NULL) {
                    attemptSend();
                    return true;
                }
                return false;
            }
        });
        mInputMessageView.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (null == mUsername) return;
                if (!mSocket.connected()) return;

                if (!mTyping) {
                    mTyping = true;
                    mSocket.emit("typing");
                }

                mTypingHandler.removeCallbacks(onTypingTimeout);
                mTypingHandler.postDelayed(onTypingTimeout, TYPING_TIMER_LENGTH);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        ImageButton sendButton = (ImageButton) view.findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptSend();
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (Activity.RESULT_OK != resultCode) {
            getActivity().finish();
            return;
        }

        mUsername = data.getStringExtra("username");
        int numUsers = data.getIntExtra("numUsers", 1);

        addLog(getResources().getString(R.string.message_welcome));
        addParticipantsLog(numUsers);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        // Inflate the menu; this adds items to the action bar if it is present.
        inflater.inflate(R.menu.menu_main, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_leave) {
            leave();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void addLog(String message) {
        mMessages.add(new Message.Builder(Message.TYPE_LOG)
                .message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void addParticipantsLog(int numUsers) {
        addLog(getResources().getQuantityString(R.plurals.message_participants, numUsers, numUsers));
    }

    private void addMessage(String username, String message) {
        mMessages.add(new Message.Builder(Message.TYPE_MESSAGE)
                .username(username).message(message).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void addTyping(String username) {
        mMessages.add(new Message.Builder(Message.TYPE_ACTION)
                .username(username).build());
        mAdapter.notifyItemInserted(mMessages.size() - 1);
        scrollToBottom();
    }

    private void removeTyping(String username) {
        for (int i = mMessages.size() - 1; i >= 0; i--) {
            Message message = mMessages.get(i);
            if (message.getType() == Message.TYPE_ACTION && message.getUsername().equals(username)) {
                mMessages.remove(i);
                mAdapter.notifyItemRemoved(i);
            }
        }
    }

    private void attemptSend() {
        if (null == mUsername) return;
        if (!mSocket.connected()) return;

        mTyping = false;


        String message = mInputMessageView.getText().toString().trim();
        if (TextUtils.isEmpty(message)) {
            mInputMessageView.requestFocus();
            return;
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

        /*path = Environment.getExternalStorageDirectory().getPath()+"/screenshot/editedpicture.jpg";
        try {
            fis = new FileInputStream(path);
            FileChannel channel = fis.getChannel();
            buffer = ByteBuffer.allocate((int) channel.size());
            channel.read(buffer);
            buffer.clear();
            bytes = new byte[buffer.capacity()];
            buffer.get(bytes);
            channel.close();
            fis.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            string_utf8 = new String(bytes,"UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        byte[] encoded = Base64.encodeBase64(string_utf8.getBytes());//エンコード処理*/


        mInputMessageView.setText("");
        //addMessage(mUsername, message);
        //addMessage(mUsername, string_utf8);

        JSONObject json = new JSONObject();
        try {
            json.put("value", imageBinary);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        // perform the sending message attempt.
        //mSocket.emit("C_to_S_message", json);
        mSocket.emit("C_to_S_broadcast", json);
    }

    private void startSignIn() {
        mUsername = null;
        mSocket.connect();
        Intent intent = new Intent(getActivity(), LoginActivity.class);
        startActivityForResult(intent, REQUEST_LOGIN);
    }

    private void leave() {
        mUsername = null;
        mSocket.disconnect();
        startSignIn();
    }

    private void scrollToBottom() {
        mMessagesView.scrollToPosition(mAdapter.getItemCount() - 1);
    }

    private Emitter.Listener onConnectError = new Emitter.Listener() {
        @Override
        public void call(Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getActivity().getApplicationContext(),
                            R.string.error_connect, Toast.LENGTH_LONG).show();
                }
            });
        }
    };

    private Emitter.Listener onNewMessage = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    String message;


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
                    String xxx = Integer.toString((int) (xx * ratio));
                    String y = array[1];
                    int yy = Integer.parseInt(y,16);
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
                        Toast.makeText(getActivity().getApplicationContext(),"er", Toast.LENGTH_LONG).show();
                        e.printStackTrace();
                    }



                    //removeTyping(username);//送信側で未定義のためコメントアウト
                    addMessage("anonymous"/*username*/,message1);//messageだと実行される
                    ABS_MT_TRACKING_ID++;
                }
            });
        }
    };

    private Emitter.Listener onUserJoined = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    int numUsers;
                    try {
                        username = data.getString("username");
                        numUsers = data.getInt("numUsers");
                    } catch (JSONException e) {
                        return;
                    }

                    addLog(getResources().getString(R.string.message_user_joined, username));
                    addParticipantsLog(numUsers);
                }
            });
        }
    };

    private Emitter.Listener onUserLeft = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    int numUsers;
                    try {
                        username = data.getString("username");
                        numUsers = data.getInt("numUsers");
                    } catch (JSONException e) {
                        return;
                    }

                    addLog(getResources().getString(R.string.message_user_left, username));
                    addParticipantsLog(numUsers);
                    removeTyping(username);
                }
            });
        }
    };

    private Emitter.Listener onTyping = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    try {
                        username = data.getString("username");
                    } catch (JSONException e) {
                        return;
                    }
                    addTyping(username);
                }
            });
        }
    };

    private Emitter.Listener onStopTyping = new Emitter.Listener() {
        @Override
        public void call(final Object... args) {
            getActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    JSONObject data = (JSONObject) args[0];
                    String username;
                    try {
                        username = data.getString("username");
                    } catch (JSONException e) {
                        return;
                    }
                    removeTyping(username);
                }
            });
        }
    };

    private Runnable onTypingTimeout = new Runnable() {
        @Override
        public void run() {
            if (!mTyping) return;

            mTyping = false;
            mSocket.emit("stop typing");
        }
    };
}

