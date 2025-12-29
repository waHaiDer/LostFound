package com.example.lostfound;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SearchPartyActivity extends AppCompatActivity {

    private static final String TAG = "SearchParty";

    // Views
    private TextView itemNameText;
    private TextView participantCount;
    private LinearLayout participantsList;
    private ProgressBar searchProgress;
    private TextView progressText;
    private RecyclerView zonesRecyclerView;
    private Button btnLeaveParty;
    private Button btnEndParty;
    private Button btnInvite;
    private LinearLayout foundOverlay;
    private TextView foundTitle;
    private TextView foundDetails;

    // Chat Views
    private LinearLayout chatHeader;
    private LinearLayout chatContent;
    private ImageView chatExpandIcon;
    private TextView chatUnreadBadge;
    private RecyclerView chatRecyclerView;
    private EditText chatInput;
    private ImageView btnSendChat;
    private boolean isChatExpanded = false;
    private int unreadCount = 0;

    // Data
    private TcpClient tcpClient;
    private String currentUser;
    private String sessionId;
    private String reportId;
    private String itemName;
    private boolean isCreator;
    private boolean hasLeftParty = false;  // Track if user explicitly left
    private Set<String> participants = new HashSet<>();
    private List<ZoneInfo> zones = new ArrayList<>();
    private ZoneAdapter adapter;
    private List<ChatMessage> chatMessages = new ArrayList<>();
    private ChatAdapter chatAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_search_party);

        if (!UserIdentity.isLoggedIn(this)) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        currentUser = UserIdentity.getID(this);
        sessionId = getIntent().getStringExtra("sessionId");
        reportId = getIntent().getStringExtra("reportId");
        itemName = getIntent().getStringExtra("itemName");
        isCreator = getIntent().getBooleanExtra("isCreator", false);

        // If creating a new session, generate session ID
        if (isCreator && sessionId == null) {
            sessionId = UUID.randomUUID().toString();
        }

        // If joining without a valid session ID, show error and exit
        if (!isCreator && sessionId == null) {
            Toast.makeText(this, "Invalid session - missing session ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupRecyclerView();
        connectAndStart();
    }

    private void initViews() {
        itemNameText = findViewById(R.id.itemNameText);
        participantCount = findViewById(R.id.participantCount);
        participantsList = findViewById(R.id.participantsList);
        searchProgress = findViewById(R.id.searchProgress);
        progressText = findViewById(R.id.progressText);
        zonesRecyclerView = findViewById(R.id.zonesRecyclerView);
        btnLeaveParty = findViewById(R.id.btnLeaveParty);
        btnEndParty = findViewById(R.id.btnEndParty);
        btnInvite = findViewById(R.id.btnInvite);
        foundOverlay = findViewById(R.id.foundOverlay);
        foundTitle = findViewById(R.id.foundTitle);
        foundDetails = findViewById(R.id.foundDetails);

        ImageView btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> confirmLeave());

        itemNameText.setText("Looking for: " + (itemName != null ? itemName : "Unknown Item"));

        btnLeaveParty.setOnClickListener(v -> confirmLeave());
        btnEndParty.setOnClickListener(v -> confirmEnd());
        btnInvite.setOnClickListener(v -> showInviteDialog());

        Button btnDismissFound = findViewById(R.id.btnDismissFound);
        btnDismissFound.setOnClickListener(v -> finish());

        // Show end button only for creator
        if (isCreator) {
            btnEndParty.setVisibility(View.VISIBLE);
        }

        // Chat views
        chatHeader = findViewById(R.id.chatHeader);
        chatContent = findViewById(R.id.chatContent);
        chatExpandIcon = findViewById(R.id.chatExpandIcon);
        chatUnreadBadge = findViewById(R.id.chatUnreadBadge);
        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        chatInput = findViewById(R.id.chatInput);
        btnSendChat = findViewById(R.id.btnSendChat);

        // Chat toggle
        chatHeader.setOnClickListener(v -> toggleChat());

        // Send chat message
        btnSendChat.setOnClickListener(v -> sendChatMessage());
        chatInput.setOnEditorActionListener((v, actionId, event) -> {
            sendChatMessage();
            return true;
        });
    }

    private void toggleChat() {
        isChatExpanded = !isChatExpanded;
        chatContent.setVisibility(isChatExpanded ? View.VISIBLE : View.GONE);
        chatExpandIcon.setRotation(isChatExpanded ? 180 : 0);

        if (isChatExpanded) {
            // Clear unread count when opened
            unreadCount = 0;
            chatUnreadBadge.setVisibility(View.GONE);
            // Scroll to bottom
            if (chatMessages.size() > 0) {
                chatRecyclerView.scrollToPosition(chatMessages.size() - 1);
            }
        }
    }

    private void sendChatMessage() {
        String message = chatInput.getText().toString().trim();
        if (message.isEmpty()) return;

        if (tcpClient == null || !tcpClient.isConnected()) {
            Toast.makeText(this, "Connecting... please wait", Toast.LENGTH_SHORT).show();
            return;
        }

        tcpClient.sendSearchPartyChat(sessionId, currentUser, message);
        chatInput.setText("");
    }

    private void setupRecyclerView() {
        adapter = new ZoneAdapter();
        zonesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        zonesRecyclerView.setAdapter(adapter);

        // Chat adapter
        chatAdapter = new ChatAdapter();
        chatRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatRecyclerView.setAdapter(chatAdapter);
    }

    private void connectAndStart() {
        tcpClient = new TcpClient(ServerConfig.SERVER_IP, ServerConfig.SERVER_PORT,
                msg -> runOnUiThread(() -> onServerMessage(msg)));
        tcpClient.connect();

        // Wait for connection to establish before sending commands
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            tcpClient.send("REGISTER_SOCKET::" + currentUser);

            if (isCreator) {
                tcpClient.createSearchParty(sessionId, currentUser, reportId, itemName);
            } else {
                tcpClient.joinSearchParty(sessionId, currentUser);
            }
        }, 500);
    }

    private void onServerMessage(String raw) {
        String msg = raw == null ? "" : raw.trim();

        // Session created
        if (msg.startsWith("SEARCH_PARTY::CREATED::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 4 && parts[2].equals(sessionId)) {
                String zonesStr = parts[3];
                parseZones(zonesStr, true);
                participants.add(currentUser);
                updateParticipantsUI();
                Toast.makeText(this, "Search party created!", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Joined session
        if (msg.startsWith("SEARCH_PARTY::JOINED::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 6 && parts[2].equals(sessionId)) {
                itemName = parts[3];
                itemNameText.setText("Looking for: " + itemName);

                String participantsStr = parts[4];
                for (String p : participantsStr.split(",")) {
                    if (!p.isEmpty()) participants.add(p);
                }

                String zonesStr = parts[5];
                parseZonesWithStatus(zonesStr);
                updateParticipantsUI();
                updateProgress();
                Toast.makeText(this, "Joined search party!", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Member joined
        if (msg.startsWith("SEARCH_PARTY::MEMBER_JOINED::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 4 && parts[2].equals(sessionId)) {
                String newMember = parts[3];
                participants.add(newMember);
                updateParticipantsUI();
                Toast.makeText(this, newMember + " joined the search!", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Member left
        if (msg.startsWith("SEARCH_PARTY::MEMBER_LEFT::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 4 && parts[2].equals(sessionId)) {
                String member = parts[3];
                participants.remove(member);
                updateParticipantsUI();
                Toast.makeText(this, member + " left the search", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Zone updated
        if (msg.startsWith("SEARCH_PARTY::ZONE_UPDATE::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 6 && parts[2].equals(sessionId)) {
                String zone = parts[3];
                String status = parts[4];
                String checker = parts[5];
                updateZoneStatus(zone, status, checker);
            }
            return;
        }

        // Item found!
        if (msg.startsWith("SEARCH_PARTY::ITEM_FOUND::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 5 && parts[2].equals(sessionId)) {
                hasLeftParty = true;  // Session completed, don't send leave on destroy
                String zone = parts[3];
                String finder = parts[4];
                showItemFound(zone, finder);
            }
            return;
        }

        // Session ended
        if (msg.startsWith("SEARCH_PARTY::ENDED::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 3 && parts[2].equals(sessionId)) {
                hasLeftParty = true;  // Session no longer exists, don't send leave on destroy
                String reason = parts.length > 3 ? parts[3] : "COMPLETED";
                Toast.makeText(this, "Search party ended: " + reason, Toast.LENGTH_LONG).show();
                finish();
            }
            return;
        }

        // Invite sent
        if (msg.startsWith("SEARCH_PARTY::INVITE_SENT::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 4) {
                String invitee = parts[3];
                Toast.makeText(this, "Invited " + invitee, Toast.LENGTH_SHORT).show();
            }
            return;
        }

        // Invite received
        if (msg.startsWith("SEARCH_PARTY::INVITE::")) {
            String[] parts = msg.split("::");
            if (parts.length >= 5) {
                String inviteSessionId = parts[2];
                String fromUser = parts[3];
                String inviteItemName = parts[4];
                showInviteReceived(inviteSessionId, fromUser, inviteItemName);
            }
            return;
        }

        // Chat message received
        if (msg.startsWith("SEARCH_PARTY::CHAT::")) {
            String[] parts = msg.split("::", 6);  // Limit splits to preserve message
            if (parts.length >= 6 && parts[2].equals(sessionId)) {
                String sender = parts[3];
                String timestamp = parts[4];
                String chatMsg = parts[5];
                addChatMessage(sender, timestamp, chatMsg);
            }
            return;
        }

        // Error handling
        if (msg.startsWith("SEARCH_PARTY::FAIL::")) {
            String[] parts = msg.split("::");
            String error = parts.length > 2 ? parts[2] : "Unknown error";
            Toast.makeText(this, "Error: " + error, Toast.LENGTH_SHORT).show();
        }
    }

    private void parseZones(String zonesStr, boolean fresh) {
        zones.clear();
        for (String zone : zonesStr.split(",")) {
            if (!zone.isEmpty()) {
                ZoneInfo info = new ZoneInfo();
                info.name = zone;
                info.status = "UNCHECKED";
                info.checkedBy = null;
                zones.add(info);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void parseZonesWithStatus(String zonesStr) {
        zones.clear();
        // Format: zone1:status1:checker1,zone2:status2:checker2
        for (String part : zonesStr.split(",")) {
            String[] zoneData = part.split(":");
            if (zoneData.length >= 2) {
                ZoneInfo info = new ZoneInfo();
                info.name = zoneData[0];
                info.status = zoneData[1];
                info.checkedBy = zoneData.length > 2 && !zoneData[2].isEmpty() ? zoneData[2] : null;
                zones.add(info);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void updateZoneStatus(String zoneName, String status, String checker) {
        for (int i = 0; i < zones.size(); i++) {
            if (zones.get(i).name.equals(zoneName)) {
                zones.get(i).status = status;
                zones.get(i).checkedBy = checker;
                adapter.notifyItemChanged(i);
                break;
            }
        }
        updateProgress();
    }

    private void updateParticipantsUI() {
        participantCount.setText(String.valueOf(participants.size()));
        participantsList.removeAllViews();

        for (String participant : participants) {
            TextView chip = new TextView(this);
            chip.setText(participant);
            chip.setTextColor(Color.WHITE);
            chip.setBackgroundResource(R.drawable.circle_badge);
            chip.setPadding(24, 8, 24, 8);

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(0, 0, 8, 0);
            chip.setLayoutParams(params);

            if (participant.equals(currentUser)) {
                chip.setBackgroundColor(ContextCompat.getColor(this, R.color.primary));
            } else {
                chip.setBackgroundColor(ContextCompat.getColor(this, R.color.text_secondary));
            }

            participantsList.addView(chip);
        }
    }

    private void updateProgress() {
        int checked = 0;
        for (ZoneInfo zone : zones) {
            if (!zone.status.equals("UNCHECKED")) {
                checked++;
            }
        }
        int total = zones.size();
        int progress = total > 0 ? (checked * 100 / total) : 0;
        searchProgress.setProgress(progress);
        progressText.setText(checked + "/" + total + " zones checked");
    }

    private void showItemFound(String zone, String finder) {
        foundDetails.setText("Found by " + finder + " at " + zone);
        foundOverlay.setVisibility(View.VISIBLE);
    }

    private void showInviteDialog() {
        EditText input = new EditText(this);
        input.setHint("Enter username");

        new AlertDialog.Builder(this)
                .setTitle("Invite to Search Party")
                .setMessage("Enter the username to invite:")
                .setView(input)
                .setPositiveButton("Invite", (dialog, which) -> {
                    String username = input.getText().toString().trim();
                    if (!username.isEmpty()) {
                        tcpClient.inviteToSearchParty(sessionId, currentUser, username);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showInviteReceived(String inviteSessionId, String fromUser, String inviteItemName) {
        new AlertDialog.Builder(this)
                .setTitle("Search Party Invite")
                .setMessage(fromUser + " invited you to help find: " + inviteItemName + "\n\nYou must leave your current search party first to join a new one.")
                .setPositiveButton("Leave & Join", (dialog, which) -> {
                    // Leave current session and join the new one
                    hasLeftParty = true;
                    if (tcpClient != null) {
                        tcpClient.leaveSearchParty(sessionId, currentUser);
                    }
                    // Start new SearchPartyActivity with the invite session
                    Intent intent = new Intent(this, SearchPartyActivity.class);
                    intent.putExtra("sessionId", inviteSessionId);
                    intent.putExtra("itemName", inviteItemName);
                    intent.putExtra("isCreator", false);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Stay Here", null)
                .show();
    }

    private void confirmLeave() {
        new AlertDialog.Builder(this)
                .setTitle("Leave Search Party?")
                .setMessage("Are you sure you want to leave the search?")
                .setPositiveButton("Leave", (dialog, which) -> {
                    hasLeftParty = true;
                    if (tcpClient != null) {
                        tcpClient.leaveSearchParty(sessionId, currentUser);
                    }
                    finish();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void confirmEnd() {
        new AlertDialog.Builder(this)
                .setTitle("End Search Party?")
                .setMessage("This will end the search for all participants.")
                .setPositiveButton("End", (dialog, which) -> {
                    tcpClient.endSearchParty(sessionId, currentUser);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addChatMessage(String sender, String timestamp, String message) {
        ChatMessage chatMessage = new ChatMessage();
        chatMessage.sender = sender;
        chatMessage.timestamp = timestamp;
        chatMessage.message = message;
        chatMessage.isMe = sender.equals(currentUser);

        chatMessages.add(chatMessage);
        chatAdapter.notifyItemInserted(chatMessages.size() - 1);
        chatRecyclerView.scrollToPosition(chatMessages.size() - 1);

        // Show unread badge if chat is collapsed and message is from others
        if (!isChatExpanded && !chatMessage.isMe) {
            unreadCount++;
            chatUnreadBadge.setText(String.valueOf(unreadCount));
            chatUnreadBadge.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public void onBackPressed() {
        confirmLeave();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Send leave message if not already left explicitly
        if (!hasLeftParty && tcpClient != null && sessionId != null && currentUser != null) {
            tcpClient.leaveSearchParty(sessionId, currentUser);
        }
        if (tcpClient != null) {
            tcpClient.close();
        }
    }

    // Zone data class
    static class ZoneInfo {
        String name;
        String status; // UNCHECKED, CHECKED, FOUND, NOT_FOUND
        String checkedBy;
    }

    // RecyclerView Adapter
    class ZoneAdapter extends RecyclerView.Adapter<ZoneAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_search_zone, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ZoneInfo zone = zones.get(position);
            holder.zoneName.setText(zone.name);

            // Update status text and icon
            switch (zone.status) {
                case "FOUND":
                    holder.zoneStatus.setText("FOUND by " + zone.checkedBy);
                    holder.zoneStatus.setTextColor(ContextCompat.getColor(SearchPartyActivity.this, R.color.found_text));
                    holder.zoneStatusIcon.setImageResource(android.R.drawable.ic_dialog_info);
                    holder.zoneStatusIcon.setColorFilter(ContextCompat.getColor(SearchPartyActivity.this, R.color.found_text));
                    holder.btnMarkFound.setVisibility(View.GONE);
                    holder.btnMarkNotFound.setVisibility(View.GONE);
                    break;
                case "NOT_FOUND":
                case "CHECKED":
                    holder.zoneStatus.setText("Checked by " + zone.checkedBy);
                    holder.zoneStatus.setTextColor(ContextCompat.getColor(SearchPartyActivity.this, R.color.text_secondary));
                    holder.zoneStatusIcon.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                    holder.zoneStatusIcon.setColorFilter(ContextCompat.getColor(SearchPartyActivity.this, R.color.text_secondary));
                    holder.btnMarkFound.setVisibility(View.VISIBLE);
                    holder.btnMarkNotFound.setVisibility(View.GONE);
                    break;
                default: // UNCHECKED
                    holder.zoneStatus.setText("Not checked yet");
                    holder.zoneStatus.setTextColor(ContextCompat.getColor(SearchPartyActivity.this, R.color.lost_text));
                    holder.zoneStatusIcon.setImageResource(android.R.drawable.ic_menu_help);
                    holder.zoneStatusIcon.setColorFilter(ContextCompat.getColor(SearchPartyActivity.this, R.color.lost_text));
                    holder.btnMarkFound.setVisibility(View.VISIBLE);
                    holder.btnMarkNotFound.setVisibility(View.VISIBLE);
                    break;
            }

            // Click handlers
            holder.btnMarkNotFound.setOnClickListener(v -> {
                tcpClient.updateSearchPartyZone(sessionId, currentUser, zone.name, "NOT_FOUND");
            });

            holder.btnMarkFound.setOnClickListener(v -> {
                new AlertDialog.Builder(SearchPartyActivity.this)
                        .setTitle("Item Found!")
                        .setMessage("Did you find the item at " + zone.name + "?")
                        .setPositiveButton("Yes, Found It!", (dialog, which) -> {
                            tcpClient.updateSearchPartyZone(sessionId, currentUser, zone.name, "FOUND");
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            });
        }

        @Override
        public int getItemCount() {
            return zones.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            ImageView zoneStatusIcon;
            TextView zoneName;
            TextView zoneStatus;
            ImageView btnMarkNotFound;
            ImageView btnMarkFound;

            ViewHolder(View itemView) {
                super(itemView);
                zoneStatusIcon = itemView.findViewById(R.id.zoneStatusIcon);
                zoneName = itemView.findViewById(R.id.zoneName);
                zoneStatus = itemView.findViewById(R.id.zoneStatus);
                btnMarkNotFound = itemView.findViewById(R.id.btnMarkNotFound);
                btnMarkFound = itemView.findViewById(R.id.btnMarkFound);
            }
        }
    }

    // Chat message data class
    static class ChatMessage {
        String sender;
        String timestamp;
        String message;
        boolean isMe;
    }

    // Chat RecyclerView Adapter
    class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_search_party_chat, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            ChatMessage msg = chatMessages.get(position);

            if (msg.isMe) {
                // My message - right aligned
                holder.myMessageLayout.setVisibility(View.VISIBLE);
                holder.otherMessageLayout.setVisibility(View.GONE);
                holder.myMessage.setText(msg.message);
                holder.myTimestamp.setText(msg.timestamp);
            } else {
                // Other's message - left aligned
                holder.myMessageLayout.setVisibility(View.GONE);
                holder.otherMessageLayout.setVisibility(View.VISIBLE);
                holder.otherUsername.setText(msg.sender);
                holder.otherMessage.setText(msg.message);
                holder.otherTimestamp.setText(msg.timestamp);
            }
        }

        @Override
        public int getItemCount() {
            return chatMessages.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            LinearLayout myMessageLayout;
            LinearLayout otherMessageLayout;
            TextView myMessage;
            TextView myTimestamp;
            TextView otherUsername;
            TextView otherMessage;
            TextView otherTimestamp;

            ViewHolder(View itemView) {
                super(itemView);
                myMessageLayout = itemView.findViewById(R.id.myMessageLayout);
                otherMessageLayout = itemView.findViewById(R.id.otherMessageLayout);
                myMessage = itemView.findViewById(R.id.myMessage);
                myTimestamp = itemView.findViewById(R.id.myTimestamp);
                otherUsername = itemView.findViewById(R.id.otherUsername);
                otherMessage = itemView.findViewById(R.id.otherMessage);
                otherTimestamp = itemView.findViewById(R.id.otherTimestamp);
            }
        }
    }
}
