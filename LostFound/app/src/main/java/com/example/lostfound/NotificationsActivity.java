package com.example.lostfound;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class NotificationsActivity extends AppCompatActivity {

    private TcpClient tcpClient;
    private LinearLayout notificationsContainer;
    private LinearLayout emptyState;
    private ProgressBar loadingIndicator;

    private final List<NotificationItem> notifications = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!UserIdentity.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_notifications);

        notificationsContainer = findViewById(R.id.notificationsContainer);
        emptyState = findViewById(R.id.emptyState);
        loadingIndicator = findViewById(R.id.loadingIndicator);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        TextView btnMarkAllRead = findViewById(R.id.btnMarkAllRead);
        btnMarkAllRead.setOnClickListener(v -> markAllAsRead());

        // Connect and fetch notifications
        tcpClient = new TcpClient(ServerConfig.SERVER_IP, ServerConfig.SERVER_PORT, msg -> runOnUiThread(() -> onServerMessage(msg)));
        tcpClient.connect();

        showLoading(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tcpClient != null) {
            notifications.clear();
            notificationsContainer.removeAllViews();
            String username = UserIdentity.getID(this);
            tcpClient.send("NOTIFICATIONS::GET::" + username);
        }
    }

    private void onServerMessage(String raw) {
        String msg = raw == null ? "" : raw.trim();

        if (msg.equals("NOTIFICATIONS_DONE")) {
            showLoading(false);
            displayNotifications();
            return;
        }

        if (msg.startsWith("NOTIFICATION::")) {
            NotificationItem notif = NotificationItem.fromServerString(msg);
            if (notif != null) {
                notifications.add(notif);
            }
            return;
        }

        if (msg.equals("NOTIFICATIONS_ALL_READ_OK")) {
            // Refresh the list
            for (NotificationItem n : notifications) {
                n.isRead = true;
            }
            notificationsContainer.removeAllViews();
            displayNotifications();
        }
    }

    private void displayNotifications() {
        if (notifications.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
        } else {
            emptyState.setVisibility(View.GONE);
            for (NotificationItem notif : notifications) {
                addNotificationCard(notif);
            }
        }
    }

    private void addNotificationCard(NotificationItem notif) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View cardView = inflater.inflate(R.layout.item_notification, notificationsContainer, false);

        ImageView icon = cardView.findViewById(R.id.notifIcon);
        TextView title = cardView.findViewById(R.id.notifTitle);
        TextView message = cardView.findViewById(R.id.notifMessage);
        TextView time = cardView.findViewById(R.id.notifTime);
        View unreadDot = cardView.findViewById(R.id.unreadDot);

        icon.setImageResource(notif.getIconResource());
        title.setText(notif.title);
        message.setText(notif.message);
        time.setText(formatTime(notif.timestamp));

        if (!notif.isRead) {
            unreadDot.setVisibility(View.VISIBLE);
            cardView.setAlpha(1.0f);
        } else {
            unreadDot.setVisibility(View.GONE);
            cardView.setAlpha(0.7f);
        }

        cardView.setOnClickListener(v -> {
            // Mark as read
            if (!notif.isRead) {
                tcpClient.send("NOTIFICATION::READ::" + notif.id);
                notif.isRead = true;
                unreadDot.setVisibility(View.GONE);
                cardView.setAlpha(0.7f);
            }

            // Navigate based on type
            if ("MESSAGE".equals(notif.type)) {
                startActivity(new Intent(this, ChatListActivity.class));
            }
        });

        notificationsContainer.addView(cardView);
    }

    private void markAllAsRead() {
        String username = UserIdentity.getID(this);
        tcpClient.send("NOTIFICATIONS::READ_ALL::" + username);
    }

    private String formatTime(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return "";
        try {
            // Simple formatting - just show time or date
            if (timestamp.contains(" ")) {
                return timestamp.split(" ")[1].substring(0, 5);
            }
        } catch (Exception e) {
            // Ignore
        }
        return timestamp;
    }

    private void showLoading(boolean show) {
        loadingIndicator.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            notificationsContainer.removeAllViews();
            emptyState.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tcpClient != null) tcpClient.close();
    }
}