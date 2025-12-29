package com.example.lostfound;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

public class TcpClient {
    public interface MessageListener {
        void onMessageReceived(String message);
    }

    private final String serverIp;
    private final int serverPort;
    private final MessageListener messageListener;

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    private volatile boolean isRunning = false;
    private volatile boolean isConnected = false;

    private final ConcurrentLinkedQueue<String> pending = new ConcurrentLinkedQueue<>();

    public TcpClient(String serverIp, int serverPort, MessageListener listener) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.messageListener = listener;
    }

    public void connect() {
        new Thread(() -> {
            try {
                Log.d("TCP", "Connecting to " + serverIp + ":" + serverPort);
                socket = new Socket(serverIp, serverPort);

                out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                isRunning = true;
                isConnected = true;

                Log.d("TCP", "Connected!");

                // Flush anything that tried to send before connection completed
                flushPending();

                while (isRunning) {
                    String serverMessage = in.readLine(); // needs \n from server
                    if (serverMessage != null && messageListener != null) {
                        Log.d("TCP", "Received: " + serverMessage);
                        messageListener.onMessageReceived(serverMessage);
                    } else {
                        isRunning = false;
                    }
                }
            } catch (Exception e) {
                Log.e("TCP", "Connection Error", e);
                isRunning = false;
                isConnected = false;
            }
        }).start();
    }

    public void send(final String message) {
        // Queue if not connected yet
        if (!isConnected || out == null) {
            pending.add(message);
            Log.d("TCP", "Queued (not connected yet): " + message);
            return;
        }

        new Thread(() -> {
            try {
                Log.d("TCP", "Sending: " + message);
                out.println(message);
                out.flush();
            } catch (Exception e) {
                Log.e("TCP", "Send error", e);
            }
        }).start();
    }

    private void flushPending() {
        String msg;
        while ((msg = pending.poll()) != null) {
            try {
                Log.d("TCP", "Flushing queued: " + msg);
                out.println(msg);
                out.flush();
            } catch (Exception e) {
                Log.e("TCP", "Flush error", e);
                break;
            }
        }
    }

    public void close() {
        isRunning = false;
        isConnected = false;
        try {
            if (socket != null) socket.close();
            if (out == null) {
                String message = "";
                Log.d("TCP", "send() dropped because out==null (not connected yet): " + message);
            }
            if (out != null) out.close();
            if (in != null) in.close();
        } catch (Exception e) {
            Log.e("TCP", "Error closing", e);
        }
    }
}
