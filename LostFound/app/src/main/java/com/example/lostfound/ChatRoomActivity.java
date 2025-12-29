package com.example.lostfound;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class ChatRoomActivity extends AppCompatActivity {

    private TcpClient tcpClient;
    private RecyclerView messagesRecyclerView;
    private EditText messageInput;
    private ImageView btnSend;
    private TextView chatUsername;
    private TextView chatStatus;
    private TextView typingIndicator;

    private String otherUser;
    private String reportId;
    private String currentUser;

    private final List<ChatMessage> messages = new ArrayList<>();
    private MessageAdapter adapter;

    private final Handler typingHandler = new Handler(Looper.getMainLooper());
    private boolean isTyping = false;
    private static final long TYPING_TIMEOUT = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!UserIdentity.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_chat_room);

        currentUser = UserIdentity.getID(this);
        otherUser = getIntent().getStringExtra("otherUser");
        reportId = getIntent().getStringExtra("reportId");

        if (otherUser == null || otherUser.isEmpty()) {
            finish();
            return;
        }

        initViews();
        setupRecyclerView();
        setupListeners();
        connectAndLoad();
    }

    private void initViews() {
        messagesRecyclerView = findViewById(R.id.messagesRecyclerView);
        messageInput = findViewById(R.id.messageInput);
        btnSend = findViewById(R.id.btnSend);
        chatUsername = findViewById(R.id.chatUsername);
        chatStatus = findViewById(R.id.chatStatus);
        typingIndicator = findViewById(R.id.typingIndicator);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        chatUsername.setText(otherUser);
        chatStatus.setText("Connecting...");
    }

    private void setupRecyclerView() {
        adapter = new MessageAdapter();
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        messagesRecyclerView.setLayoutManager(layoutManager);
        messagesRecyclerView.setAdapter(adapter);
    }

    private void setupListeners() {
        btnSend.setOnClickListener(v -> sendMessage());

        messageInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (s.length() > 0 && !isTyping) {
                    isTyping = true;
                    sendTypingIndicator(true);
                }
                typingHandler.removeCallbacksAndMessages(null);
                typingHandler.postDelayed(() -> {
                    isTyping = false;
                    sendTypingIndicator(false);
                }, TYPING_TIMEOUT);
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void connectAndLoad() {
        tcpClient = new TcpClient(ServerConfig.SERVER_IP, ServerConfig.SERVER_PORT, msg -> runOnUiThread(() -> onServerMessage(msg)));
        tcpClient.connect();

        // Register user and check presence
        tcpClient.send("REGISTER_SOCKET::" + currentUser);
        tcpClient.send("PRESENCE::CHECK::" + otherUser);

        // Load chat history
        String rptId = (reportId != null && !reportId.isEmpty()) ? reportId : "0";
        tcpClient.send("CHAT::HISTORY::" + currentUser + "::" + otherUser + "::" + rptId);
    }

    private void onServerMessage(String raw) {
        String msg = raw == null ? "" : raw.trim();

        // Presence response - format: PRESENCE::username::ONLINE/OFFLINE::last_seen
        if (msg.startsWith("PRESENCE::") && !msg.startsWith("PRESENCE::CHECK::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 3 && parts[1].equals(otherUser)) {
                boolean online = "ONLINE".equals(parts[2]);
                chatStatus.setText(online ? "Online" : "Offline");
                chatStatus.setTextColor(getResources().getColor(
                        online ? R.color.found_text : R.color.text_secondary, null));
            }
            return;
        }

        // Chat history done
        if (msg.equals("CHAT::HISTORY_DONE")) {
            adapter.notifyDataSetChanged();
            scrollToBottom();
            // Mark messages as read
            tcpClient.send("CHAT::READ::" + currentUser + "::" + otherUser);
            return;
        }

        // Chat message (history or new)
        if (msg.startsWith("CHAT::MSG::")) {
            ChatMessage chatMsg = ChatMessage.fromServerString(msg);
            if (chatMsg != null) {
                // Check if it's a new message or part of history
                boolean exists = false;
                for (ChatMessage m : messages) {
                    if (m.id != null && m.id.equals(chatMsg.id)) {
                        exists = true;
                        break;
                    }
                }
                if (!exists) {
                    messages.add(chatMsg);
                    adapter.notifyItemInserted(messages.size() - 1);
                    scrollToBottom();

                    // Mark as read if from other user
                    if (chatMsg.fromUser.equals(otherUser)) {
                        tcpClient.send("CHAT::READ::" + currentUser + "::" + otherUser);
                    }
                }
            }
            return;
        }

        // Receive new message
        if (msg.startsWith("CHAT::RECEIVE::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 7) {
                ChatMessage chatMsg = new ChatMessage();
                chatMsg.fromUser = parts[2];
                chatMsg.toUser = parts[3];
                chatMsg.reportId = parts[4];
                chatMsg.messageText = parts[5];
                chatMsg.timestamp = parts[6];
                chatMsg.id = parts.length > 7 ? parts[7] : null;
                chatMsg.isRead = false;

                if (chatMsg.fromUser.equals(otherUser)) {
                    messages.add(chatMsg);
                    adapter.notifyItemInserted(messages.size() - 1);
                    scrollToBottom();
                    tcpClient.send("CHAT::READ::" + currentUser + "::" + otherUser);
                }
            }
            return;
        }

        // Message sent confirmation
        if (msg.startsWith("CHAT::SENT::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 4) {
                String msgId = parts[2];
                String timestamp = parts[3];

                // Update last sent message with server-assigned ID and timestamp
                for (int i = messages.size() - 1; i >= 0; i--) {
                    ChatMessage m = messages.get(i);
                    if (m.fromUser.equals(currentUser) && (m.id == null || m.id.isEmpty())) {
                        m.id = msgId;
                        m.timestamp = timestamp;
                        adapter.notifyItemChanged(i);
                        break;
                    }
                }
            }
            return;
        }

        // Typing indicator
        if (msg.startsWith("CHAT::TYPING::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 4) {
                String typingUser = parts[2];
                boolean typing = "true".equals(parts[3]);
                if (typingUser.equals(otherUser)) {
                    typingIndicator.setVisibility(typing ? View.VISIBLE : View.GONE);
                    typingIndicator.setText(otherUser + " is typing...");
                }
            }
            return;
        }

        // Read receipt
        if (msg.startsWith("CHAT::READ_RECEIPT::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 3 && parts[2].equals(otherUser)) {
                // Mark all messages to this user as read
                for (int i = 0; i < messages.size(); i++) {
                    ChatMessage m = messages.get(i);
                    if (m.fromUser.equals(currentUser) && !m.isRead) {
                        m.isRead = true;
                        adapter.notifyItemChanged(i);
                    }
                }
            }
            return;
        }
    }

    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;

        // Add message to UI immediately
        ChatMessage msg = new ChatMessage();
        msg.fromUser = currentUser;
        msg.toUser = otherUser;
        msg.reportId = reportId != null ? reportId : "0";
        msg.messageText = text;
        msg.timestamp = "";
        msg.isRead = false;

        messages.add(msg);
        adapter.notifyItemInserted(messages.size() - 1);
        scrollToBottom();

        // Send to server
        tcpClient.send("CHAT::SEND::" + currentUser + "::" + otherUser + "::" + msg.reportId + "::" + text);

        messageInput.setText("");
        isTyping = false;
    }

    private void sendTypingIndicator(boolean typing) {
        if (tcpClient != null) {
            tcpClient.send("CHAT::TYPING::" + currentUser + "::" + otherUser + "::" + typing);
        }
    }

    private void scrollToBottom() {
        if (!messages.isEmpty()) {
            messagesRecyclerView.smoothScrollToPosition(messages.size() - 1);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        typingHandler.removeCallbacksAndMessages(null);
        if (tcpClient != null) tcpClient.close();
    }

    // RecyclerView Adapter
    private class MessageAdapter extends RecyclerView.Adapter<MessageAdapter.MessageViewHolder> {

        private static final int TYPE_SENT = 1;
        private static final int TYPE_RECEIVED = 2;

        @Override
        public int getItemViewType(int position) {
            ChatMessage msg = messages.get(position);
            return msg.fromUser.equals(currentUser) ? TYPE_SENT : TYPE_RECEIVED;
        }

        @NonNull
        @Override
        public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layoutId = viewType == TYPE_SENT ? R.layout.item_message_sent : R.layout.item_message_received;
            View view = LayoutInflater.from(parent.getContext()).inflate(layoutId, parent, false);
            return new MessageViewHolder(view, viewType);
        }

        @Override
        public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
            ChatMessage msg = messages.get(position);
            holder.bind(msg);
        }

        @Override
        public int getItemCount() {
            return messages.size();
        }

        class MessageViewHolder extends RecyclerView.ViewHolder {
            TextView messageText;
            TextView messageTime;
            ImageView readStatus;
            int viewType;

            MessageViewHolder(@NonNull View itemView, int viewType) {
                super(itemView);
                this.viewType = viewType;
                messageText = itemView.findViewById(R.id.messageText);
                messageTime = itemView.findViewById(R.id.messageTime);
                if (viewType == TYPE_SENT) {
                    readStatus = itemView.findViewById(R.id.readStatus);
                }
            }

            void bind(ChatMessage msg) {
                messageText.setText(msg.messageText);
                messageTime.setText(formatTime(msg.timestamp));

                if (viewType == TYPE_SENT && readStatus != null) {
                    readStatus.setAlpha(msg.isRead ? 1.0f : 0.5f);
                }
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
        }
    }
}
