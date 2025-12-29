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

    public static ReportItem fromServerString(String raw) {
        try {
            if (raw == null || !raw.startsWith("REPORT::")) return null;

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

            return r;

        } catch (Exception e) {
            return null;
        }
    }
}
