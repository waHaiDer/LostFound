package com.example.lostfound;

public class MatchItem {
    public String id;
    public int score;
    public String confidence; // HIGH, MEDIUM, LOW

    // Lost report info
    public String lostReportId;
    public String lostOwnerId;
    public String lostItemName;
    public String lostPhotoUrl;

    // Found report info
    public String foundReportId;
    public String foundOwnerId;
    public String foundItemName;
    public String foundPhotoUrl;

    public static MatchItem fromServerString(String raw) {
        try {
            if (raw == null || !raw.startsWith("MATCH::")) return null;

            String[] parts = raw.split("::");
            if (parts.length < 12) return null;

            MatchItem m = new MatchItem();
            m.id = parts[1];
            m.score = Integer.parseInt(parts[2]);
            m.confidence = parts[3];

            // Lost report
            m.lostReportId = parts[4];
            m.lostOwnerId = parts[5];
            m.lostItemName = parts[6];
            m.lostPhotoUrl = parts.length > 7 ? parts[7] : "";

            // Found report
            m.foundReportId = parts[8];
            m.foundOwnerId = parts[9];
            m.foundItemName = parts[10];
            m.foundPhotoUrl = parts.length > 11 ? parts[11] : "";

            return m;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public boolean hasLostPhoto() {
        return lostPhotoUrl != null && !lostPhotoUrl.isEmpty() && lostPhotoUrl.startsWith("http");
    }

    public boolean hasFoundPhoto() {
        return foundPhotoUrl != null && !foundPhotoUrl.isEmpty() && foundPhotoUrl.startsWith("http");
    }

    public String getConfidenceColor() {
        switch (confidence) {
            case "HIGH":
                return "#16A34A"; // Green
            case "MEDIUM":
                return "#F59E0B"; // Orange
            case "LOW":
                return "#6B7280"; // Gray
            default:
                return "#6B7280";
        }
    }
}
