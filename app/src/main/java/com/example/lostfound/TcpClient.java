package com.example.lostfound;

import android.util.Log;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

public class TcpClient {
    public interface MessageListener {
        void onMessageReceived(String message);
    }

    private String serverIp;
    private int serverPort;
    private MessageListener messageListener;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean isRunning = false;

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
                isRunning = true;

                out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())), true);
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

                Log.d("TCP", "Connected!");

                // Listen for incoming messages
                while (isRunning) {
                    String serverMessage = in.readLine();
                    if (serverMessage != null && messageListener != null) {
                        Log.d("TCP", "Received: " + serverMessage);
                        messageListener.onMessageReceived(serverMessage);
                    } else {
                        isRunning = false;
                    }
                }
            } catch (Exception e) {
                Log.e("TCP", "Connection Error", e);
            }
        }).start();
    }

    public void send(final String message) {
        new Thread(() -> {
            if (out != null && !out.checkError()) {
                Log.d("TCP", "Sending: " + message);
                out.println(message);
                out.flush();
            }
        }).start();
    }

    public void close() {
        isRunning = false;
        try {
            if (socket != null) socket.close();
            if (out != null) out.close();
            if (in != null) in.close();
        } catch (Exception e) {
            Log.e("TCP", "Error closing", e);
        }
    }
}