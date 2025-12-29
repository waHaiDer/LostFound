package com.example.lostfound;

public class ClaimQA {
    public String id;
    public String claimId;
    public String question;
    public String answer;
    public String askedBy;
    public String createdAt;
    public String answeredAt;

    public ClaimQA() {}

    /**
     * Parse from server message:
     * CLAIM_QA::id::claim_id::question::answer::asked_by::created_at::answered_at
     */
    public static ClaimQA fromServerString(String message) {
        if (message == null || !message.startsWith("CLAIM_QA::")) return null;

        String[] parts = message.split("::");
        if (parts.length < 7) return null;

        ClaimQA qa = new ClaimQA();
        qa.id = parts[1];
        qa.claimId = parts[2];
        qa.question = parts[3];
        qa.answer = parts.length > 4 ? parts[4] : "";
        qa.askedBy = parts.length > 5 ? parts[5] : "";
        qa.createdAt = parts.length > 6 ? parts[6] : "";
        qa.answeredAt = parts.length > 7 ? parts[7] : "";
        return qa;
    }

    public boolean isAnswered() {
        return answer != null && !answer.isEmpty();
    }
}
