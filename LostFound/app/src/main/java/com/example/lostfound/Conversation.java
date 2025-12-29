package com.example.lostfound;

public class Conversation {
    public String otherUser;
    public String reportId;
    public String lastMessage;
    public String timestamp;
    public int unreadCount;

    public static Conversation fromServerString(String raw) {
        try {
            if (raw == null || !raw.startsWith("CHAT::CONV::")) return null;

            String[] p = raw.split("::");
            if (p.length < 7) return null;

            Conversation c = new Conversation();
            c.otherUser = p[2];
            c.reportId = p[3];
            c.lastMessage = p[4];
            c.timestamp = p[5];
            c.unreadCount = Integer.parseInt(p[6]);

            return c;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean hasUnread() {
        return unreadCount > 0;
    }
}