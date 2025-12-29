package com.example.lostfound;

public class ChatMessage {
    public String id;
    public String fromUser;
    public String toUser;
    public String reportId;
    public String messageText;
    public String timestamp;
    public boolean isRead;

    public static ChatMessage fromServerString(String raw) {
        try {
            if (raw == null || !raw.startsWith("CHAT::MSG::")) return null;

            String[] p = raw.split("::");
            if (p.length < 9) return null;

            ChatMessage m = new ChatMessage();
            m.fromUser = p[2];
            m.toUser = p[3];
            m.reportId = p[4];
            m.messageText = p[5];
            m.timestamp = p[6];
            m.id = p[7];
            m.isRead = "read".equalsIgnoreCase(p[8]);

            return m;
        } catch (Exception e) {
            return null;
        }
    }

    public static ChatMessage fromReceiveString(String raw) {
        try {
            if (raw == null || !raw.startsWith("CHAT::RECEIVE::")) return null;

            String[] p = raw.split("::");
            if (p.length < 8) return null;

            ChatMessage m = new ChatMessage();
            m.fromUser = p[2];
            m.toUser = p[3];
            m.reportId = p[4];
            m.messageText = p[5];
            m.timestamp = p[6];
            m.id = p[7];
            m.isRead = false;

            return m;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean isSentBy(String username) {
        return username != null && username.equals(fromUser);
    }
}