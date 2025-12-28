package com.example.lostfound;

public class ReportItem {
    private boolean isLost;
    private String name;
    private String category;
    private String color;
    private String location;
    private String description;
    private String date;
    private boolean hasImage;

    public ReportItem(boolean isLost, String name, String category, String color, String location, String description, String date, boolean hasImage) {
        this.isLost = isLost;
        this.name = name;
        this.category = category;
        this.color = color;
        this.location = location;
        this.description = description;
        this.date = date;
        this.hasImage = hasImage;
    }

    // Getters
    public boolean isLost() { return isLost; }
    public String getName() { return name; }
    public String getCategory() { return category; }
    public String getColor() { return color; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }
    public String getDate() { return date; }
    public boolean hasImage() { return hasImage; }

    // Convert to Server String
    public String toServerString() {
        String tag = isLost ? "[LOST]" : "[FOUND]";
        StringBuilder sb = new StringBuilder();
        sb.append(tag).append(" ").append(name);

        if (!category.isEmpty()) sb.append(" (").append(category).append(")");
        if (!color.isEmpty()) sb.append(" - ").append(color);
        if (!location.isEmpty()) sb.append(" at ").append(location);
        if (date != null && !date.isEmpty()) sb.append(" on ").append(date);
        if (!description.isEmpty()) sb.append(" | ").append(description);
        if (hasImage) sb.append(" | [IMAGE]");

        return sb.toString();
    }

    // Parse from Server String
    public static ReportItem fromServerString(String message) {
        boolean isLost = message.contains("[LOST]");
        boolean hasImage = message.contains("[IMAGE]");

        String content = message.replace("[IMAGE]", "")
                .replace("[LOST]", "")
                .replace("[FOUND]", "")
                .trim();

        String name = content;
        String category = "Item";
        String color = "";
        String location = "Campus";
        String description = "";
        String date = "";

        if (content.contains("|")) {
            String[] parts = content.split("\\|");
            content = parts[0].trim();
            if (parts.length > 1) description = parts[1].trim();
        }

        int onIndex = content.lastIndexOf(" on ");
        if (onIndex != -1) {
            date = content.substring(onIndex + 4).trim();
            content = content.substring(0, onIndex).trim();
        }

        int atIndex = content.lastIndexOf(" at ");
        if (atIndex != -1) {
            location = content.substring(atIndex + 4).trim();
            content = content.substring(0, atIndex).trim();
        }

        if (content.contains("(") && content.contains(")")) {
            int start = content.indexOf("(");
            int end = content.indexOf(")");
            if (start < end) {
                category = content.substring(start + 1, end);
                content = content.replace("(" + category + ")", "").trim();
            }
        }

        if (content.contains("-")) {
            String[] parts = content.split("-");
            name = parts[0].trim();
            if (parts.length > 1) color = parts[1].trim();
        } else {
            name = content.trim();
        }

        return new ReportItem(isLost, name, category, color, location, description, date, hasImage);
    }
}