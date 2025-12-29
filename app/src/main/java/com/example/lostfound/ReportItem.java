package com.example.lostfound;

public class ReportItem {
    public String ownerId;
    public boolean isLost;
    public String name;
    public String category;
    public String color;
    public String location;
    public String description;

    public static ReportItem fromServerString(String raw) {
        if (raw == null) return null;
        String msg = raw.trim();

        // REPORT::owner::LOST/FOUND::name::category::color::location::description
        if (!msg.startsWith("REPORT::")) return null;

        String[] parts = msg.split("::");
        if (parts.length < 8) return null;

        ReportItem r = new ReportItem();
        r.ownerId = parts[1];
        r.isLost = "LOST".equalsIgnoreCase(parts[2]);
        r.name = parts[3];
        r.category = parts[4];
        r.color = parts[5];
        r.location = parts[6];
        r.description = parts[7];

        return r;
    }
}
