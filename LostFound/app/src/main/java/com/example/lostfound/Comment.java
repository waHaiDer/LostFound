package com.example.lostfound;

public class Comment {
    public String id;
    public String reportId;
    public String username;
    public String text;
    public String createdAt;

    public Comment() {}

    public Comment(String id, String reportId, String username, String text, String createdAt) {
        this.id = id;
        this.reportId = reportId;
        this.username = username;
        this.text = text;
        this.createdAt = createdAt;
    }

    /**
     * Parse from server message: COMMENT::id::report_id::username::text::created_at
     */
    public static Comment fromServerString(String message) {
        if (message == null || !message.startsWith("COMMENT::")) return null;

        String[] parts = message.split("::");
        if (parts.length < 6) return null;

        Comment c = new Comment();
        c.id = parts[1];
        c.reportId = parts[2];
        c.username = parts[3];
        c.text = parts[4];
        c.createdAt = parts[5];
        return c;
    }

    public String getFormattedTime() {
        if (createdAt == null || createdAt.isEmpty()) return "";
        try {
            if (createdAt.contains(" ")) {
                return createdAt.split(" ")[1].substring(0, 5);
            }
        } catch (Exception e) {
            // Ignore
        }
        return createdAt;
    }
}
