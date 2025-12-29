package com.example.lostfound;

public class Claim {
    public String id;
    public String reportId;
    public String claimerUsername;
    public String proofDescription;
    public String status; // SUBMITTED, QUESTIONING, APPROVED, REJECTED, CANCELLED
    public String createdAt;
    public String reportName;
    public String reportOwner;

    public Claim() {}

    /**
     * Parse from server message:
     * CLAIM::id::report_id::claimer_username::proof::status::created_at::report_name::report_owner
     */
    public static Claim fromServerString(String message) {
        if (message == null || !message.startsWith("CLAIM::")) return null;

        String[] parts = message.split("::");
        if (parts.length < 7) return null;

        // Skip if it's a special CLAIM message like CLAIM::NEW, CLAIM::APPROVED, etc.
        try {
            Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }

        Claim c = new Claim();
        c.id = parts[1];
        c.reportId = parts[2];
        c.claimerUsername = parts[3];
        c.proofDescription = parts.length > 4 ? parts[4] : "";
        c.status = parts.length > 5 ? parts[5] : "SUBMITTED";
        c.createdAt = parts.length > 6 ? parts[6] : "";
        c.reportName = parts.length > 7 ? parts[7] : "";
        c.reportOwner = parts.length > 8 ? parts[8] : "";
        return c;
    }

    public String getStatusDisplayText() {
        switch (status) {
            case "SUBMITTED": return "Pending Review";
            case "QUESTIONING": return "Answering Questions";
            case "APPROVED": return "Approved";
            case "REJECTED": return "Rejected";
            case "CANCELLED": return "Cancelled";
            default: return status;
        }
    }

    public int getStatusColor() {
        switch (status) {
            case "SUBMITTED": return 0xFF2196F3; // Blue
            case "QUESTIONING": return 0xFFFF9800; // Orange
            case "APPROVED": return 0xFF4CAF50; // Green
            case "REJECTED": return 0xFFF44336; // Red
            case "CANCELLED": return 0xFF9E9E9E; // Gray
            default: return 0xFF9E9E9E;
        }
    }

    public boolean isPending() {
        return "SUBMITTED".equals(status) || "QUESTIONING".equals(status);
    }
}
