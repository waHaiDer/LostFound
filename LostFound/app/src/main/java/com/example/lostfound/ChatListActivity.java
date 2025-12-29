package com.example.lostfound;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class ChatListActivity extends AppCompatActivity {

    private TcpClient tcpClient;
    private LinearLayout conversationsContainer;
    private LinearLayout emptyState;
    private ProgressBar loadingIndicator;

    private final List<Conversation> conversations = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!UserIdentity.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_chat_list);

        conversationsContainer = findViewById(R.id.conversationsContainer);
        emptyState = findViewById(R.id.emptyState);
        loadingIndicator = findViewById(R.id.loadingIndicator);

        setupBottomNavigation();

        // Connect and fetch conversations
        tcpClient = new TcpClient(ServerConfig.SERVER_IP, ServerConfig.SERVER_PORT, msg -> runOnUiThread(() -> onServerMessage(msg)));
        tcpClient.connect();

        showLoading(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (tcpClient != null) {
            refreshConversations();
        }
    }

    private void refreshConversations() {
        conversations.clear();
        conversationsContainer.removeAllViews();
        showLoading(true);
        String username = UserIdentity.getID(this);
        tcpClient.send("CHAT::CONVERSATIONS::" + username);
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setSelectedItemId(R.id.nav_chat);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_chat) return true;
            if (id == R.id.nav_home) {
                startActivity(new Intent(this, MainActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            if (id == R.id.nav_my_reports) {
                startActivity(new Intent(this, MyReportsActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            if (id == R.id.nav_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }
            return true;
        });
    }

    private void onServerMessage(String raw) {
        String msg = raw == null ? "" : raw.trim();

        if (msg.equals("CHAT::CONVERSATIONS_DONE")) {
            showLoading(false);
            displayConversations();
            return;
        }

        if (msg.startsWith("CHAT::CONV::")) {
            Conversation conv = Conversation.fromServerString(msg);
            if (conv != null) {
                conversations.add(conv);
            }
            return;
        }

        // Handle incoming message (refresh list)
        if (msg.startsWith("CHAT::RECEIVE::")) {
            refreshConversations();
        }
    }

    private void displayConversations() {
        if (conversations.isEmpty()) {
            emptyState.setVisibility(View.VISIBLE);
        } else {
            emptyState.setVisibility(View.GONE);
            for (Conversation conv : conversations) {
                addConversationCard(conv);
            }
        }
    }

    private void addConversationCard(Conversation conv) {
        LayoutInflater inflater = LayoutInflater.from(this);
        View cardView = inflater.inflate(R.layout.item_conversation, conversationsContainer, false);

        TextView username = cardView.findViewById(R.id.convUsername);
        TextView lastMessage = cardView.findViewById(R.id.convLastMessage);
        TextView time = cardView.findViewById(R.id.convTime);
        TextView unreadBadge = cardView.findViewById(R.id.unreadBadge);
        View onlineIndicator = cardView.findViewById(R.id.onlineIndicator);

        username.setText(conv.otherUser);
        lastMessage.setText(conv.lastMessage);
        time.setText(formatTime(conv.timestamp));

        if (conv.hasUnread()) {
            unreadBadge.setVisibility(View.VISIBLE);
            unreadBadge.setText(String.valueOf(conv.unreadCount));
        } else {
            unreadBadge.setVisibility(View.GONE);
        }

        // Check online status
        tcpClient.send("PRESENCE::CHECK::" + conv.otherUser);

        cardView.setOnClickListener(v -> {
            Intent intent = new Intent(this, ChatRoomActivity.class);
            intent.putExtra("otherUser", conv.otherUser);
            intent.putExtra("reportId", conv.reportId);
            startActivity(intent);
        });

        conversationsContainer.addView(cardView);
    }

    private String formatTime(String timestamp) {
        if (timestamp == null || timestamp.isEmpty()) return "";
        try {
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
            conversationsContainer.removeAllViews();
            emptyState.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tcpClient != null) tcpClient.close();
    }
}