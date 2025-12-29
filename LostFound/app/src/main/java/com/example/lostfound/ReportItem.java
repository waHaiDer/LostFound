package com.example.lostfound;

public class ReportItem {

    public String id;
    public String ownerId;
    public boolean isLost;

    public String name;
    public String category;
    public String color;
    public String location;
    public String description;
    public String reportDate;
    public String status; // ACTIVE, RESOLVED
    public String photoUrl; // Photo URL from Supabase Storage

    public static ReportItem fromServerString(String raw) {
        try {
            if (raw == null) return null;

            // Support multiple message prefixes
            if (!raw.startsWith("REPORT::") && !raw.startsWith("MY_REPORT::") && !raw.startsWith("SEARCH_RESULT::")) {
                return null;
            }

            String[] p = raw.split("::");
            if (p.length < 10) return null;

            ReportItem r = new ReportItem();
            r.id = p[1];
            r.ownerId = p[2];
            r.isLost = p[3].equalsIgnoreCase("LOST");

            r.name = p[4];
            r.category = p[5];
            r.color = p[6];
            r.location = p[7];
            r.description = p[8];
            r.reportDate = p[9];

            // Status field (optional, defaults to ACTIVE)
            r.status = p.length > 10 ? p[10] : "ACTIVE";

            // Photo URL (optional)
            r.photoUrl = p.length > 11 ? p[11] : "";

            return r;

        } catch (Exception e) {
            return null;
        }
    }

    public boolean isResolved() {
        return "RESOLVED".equalsIgnoreCase(status);
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status) || status == null || status.isEmpty();
    }

    public boolean hasPhoto() {
        return photoUrl != null && !photoUrl.isEmpty() && photoUrl.startsWith("http");
    }
}
