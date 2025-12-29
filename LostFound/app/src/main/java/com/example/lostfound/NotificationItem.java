package com.example.lostfound;

public class NotificationItem {
    public String id;
    public String type;      // MESSAGE, MATCH, CLAIM, COMMENT, SYSTEM
    public String title;
    public String message;
    public String timestamp;
    public boolean isRead;

    public static NotificationItem fromServerString(String raw) {
        try {
            if (raw == null || !raw.startsWith("NOTIFICATION::")) return null;

            String[] p = raw.split("::");
            if (p.length < 7) return null;

            NotificationItem n = new NotificationItem();
            n.id = p[1];
            n.type = p[2];
            n.title = p[3];
            n.message = p[4];
            n.timestamp = p[5];
            n.isRead = "read".equalsIgnoreCase(p[6]);

            return n;
        } catch (Exception e) {
            return null;
        }
    }

    public int getIconResource() {
        switch (type) {
            case "MESSAGE":
                return android.R.drawable.ic_dialog_email;
            case "MATCH":
                return android.R.drawable.ic_menu_search;
            case "CLAIM":
                return android.R.drawable.ic_menu_myplaces;
            default:
                return android.R.drawable.ic_dialog_info;
        }
    }
}