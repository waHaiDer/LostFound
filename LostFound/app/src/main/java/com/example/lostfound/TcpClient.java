package com.example.lostfound;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
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

    // Heartbeat
    private String currentUsername;
    private Handler heartbeatHandler;
    private static final long HEARTBEAT_INTERVAL = 30000; // 30 seconds
    private static final int CONNECTION_TIMEOUT = 3000; // 3 seconds connection timeout

    public TcpClient(String serverIp, int serverPort, MessageListener listener) {
        this.serverIp = serverIp;
        this.serverPort = serverPort;
        this.messageListener = listener;
    }

    public void connect() {
        new Thread(() -> {
            try {
                Log.d("TCP", "Connecting to " + serverIp + ":" + serverPort);
                socket = new Socket();
                socket.connect(new InetSocketAddress(serverIp, serverPort), CONNECTION_TIMEOUT);

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
        stopHeartbeat();
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

    /**
     * Set the current username and start heartbeat
     */
    public void setUsername(String username) {
        this.currentUsername = username;
        if (username != null && !username.isEmpty() && isConnected) {
            startHeartbeat();
        }
    }

    /**
     * Start sending heartbeat messages periodically
     */
    public void startHeartbeat() {
        if (heartbeatHandler == null) {
            heartbeatHandler = new Handler(Looper.getMainLooper());
        }

        Runnable heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (isConnected && currentUsername != null && !currentUsername.isEmpty()) {
                    send("HEARTBEAT::" + currentUsername);
                    Log.d("TCP", "Heartbeat sent for: " + currentUsername);
                    heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL);
                }
            }
        };

        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL);
        Log.d("TCP", "Heartbeat started for: " + currentUsername);
    }

    /**
     * Stop sending heartbeat messages
     */
    public void stopHeartbeat() {
        if (heartbeatHandler != null) {
            heartbeatHandler.removeCallbacksAndMessages(null);
            Log.d("TCP", "Heartbeat stopped");
        }
    }

    /**
     * Check if client is connected
     */
    public boolean isConnected() {
        return isConnected;
    }

    // ---- Convenience methods for Sprint 4 features ----

    /**
     * Subscribe to report updates (comments, etc.)
     */
    public void subscribeToReport(String reportId, String username) {
        send("SUBSCRIBE::REPORT::" + reportId + "::" + username);
    }

    /**
     * Unsubscribe from report updates
     */
    public void unsubscribeFromReport(String reportId, String username) {
        send("UNSUBSCRIBE::REPORT::" + reportId + "::" + username);
    }

    /**
     * Get comments for a report
     */
    public void getComments(String reportId) {
        send("COMMENT::GET::" + reportId);
    }

    /**
     * Post a comment on a report
     */
    public void postComment(String reportId, String username, String text) {
        send("COMMENT::POST::" + reportId + "::" + username + "::" + text);
    }

    /**
     * Delete a comment
     */
    public void deleteComment(String commentId, String username) {
        send("COMMENT::DELETE::" + commentId + "::" + username);
    }

    /**
     * Submit a claim for a found item
     */
    public void submitClaim(String reportId, String claimer, String proofDescription) {
        send("CLAIM::SUBMIT::" + reportId + "::" + claimer + "::" + proofDescription);
    }

    /**
     * Get claims for a report (finder view)
     */
    public void getClaimsForReport(String reportId) {
        send("CLAIMS::GET::REPORT::" + reportId);
    }

    /**
     * Get claims by a user (claimer view)
     */
    public void getClaimsByUser(String username) {
        send("CLAIMS::GET::USER::" + username);
    }

    /**
     * Ask a verification question
     */
    public void askClaimQuestion(String claimId, String finder, String question) {
        send("CLAIM::QUESTION::" + claimId + "::" + finder + "::" + question);
    }

    /**
     * Answer a verification question
     */
    public void answerClaimQuestion(String claimId, String claimer, String answer) {
        send("CLAIM::ANSWER::" + claimId + "::" + claimer + "::" + answer);
    }

    /**
     * Get Q&A history for a claim
     */
    public void getClaimQA(String claimId) {
        send("CLAIM::QA::" + claimId);
    }

    /**
     * Approve a claim
     */
    public void approveClaim(String claimId, String finder) {
        send("CLAIM::APPROVE::" + claimId + "::" + finder);
    }

    /**
     * Reject a claim
     */
    public void rejectClaim(String claimId, String finder, String reason) {
        send("CLAIM::REJECT::" + claimId + "::" + finder + "::" + reason);
    }

    /**
     * Cancel a claim
     */
    public void cancelClaim(String claimId, String claimer) {
        send("CLAIM::CANCEL::" + claimId + "::" + claimer);
    }

    /**
     * Check user presence
     */
    public void checkPresence(String username) {
        send("PRESENCE::CHECK::" + username);
    }

    // ---- Sprint 5: Location Sharing Methods ----

    /**
     * Start a location sharing session with another user
     */
    public void startLocationSession(String sessionId, String fromUser, String toUser) {
        send("LOCATION::START::" + sessionId + "::" + fromUser + "::" + toUser);
    }

    /**
     * Accept a location session invitation
     */
    public void acceptLocationSession(String sessionId, String username) {
        send("LOCATION::ACCEPT::" + sessionId + "::" + username);
    }

    /**
     * Decline a location session invitation
     */
    public void declineLocationSession(String sessionId, String username) {
        send("LOCATION::DECLINE::" + sessionId + "::" + username);
    }

    /**
     * Send location update during active session
     */
    public void sendLocationUpdate(String sessionId, String username, double latitude, double longitude, float accuracy) {
        send("LOCATION::UPDATE::" + sessionId + "::" + username + "::" + latitude + "::" + longitude + "::" + accuracy);
    }

    /**
     * End a location sharing session
     */
    public void endLocationSession(String sessionId, String username) {
        send("LOCATION::END::" + sessionId + "::" + username);
    }
}
