package com.example.lostfound;

/**
 * Model class representing a location sharing session between two users.
 * Sprint 5 - Location Sharing feature.
 */
public class LocationSession {
    public String sessionId;
    public String fromUser;
    public String toUser;
    public String status; // PENDING, ACTIVE, ENDED
    public long startTime;

    // Location data
    public double myLatitude;
    public double myLongitude;
    public float myAccuracy;

    public double otherLatitude;
    public double otherLongitude;
    public float otherAccuracy;

    public LocationSession() {
        this.status = "PENDING";
        this.startTime = System.currentTimeMillis();
    }

    public LocationSession(String sessionId, String fromUser, String toUser) {
        this.sessionId = sessionId;
        this.fromUser = fromUser;
        this.toUser = toUser;
        this.status = "PENDING";
        this.startTime = System.currentTimeMillis();
    }

    /**
     * Check if the session has expired (30 minute timeout)
     */
    public boolean isExpired() {
        long thirtyMinutes = 30 * 60 * 1000;
        return System.currentTimeMillis() - startTime > thirtyMinutes;
    }

    /**
     * Calculate distance between two users in meters
     */
    public float getDistanceMeters() {
        if (myLatitude == 0 || otherLatitude == 0) {
            return -1;
        }

        float[] results = new float[1];
        android.location.Location.distanceBetween(
            myLatitude, myLongitude,
            otherLatitude, otherLongitude,
            results
        );
        return results[0];
    }

    /**
     * Get formatted distance string
     */
    public String getDistanceString() {
        float distance = getDistanceMeters();
        if (distance < 0) {
            return "Calculating...";
        } else if (distance < 1000) {
            return String.format("%.0f m", distance);
        } else {
            return String.format("%.1f km", distance / 1000);
        }
    }

    /**
     * Estimate walking time in minutes (assuming 5 km/h walking speed)
     */
    public String getEstimatedTime() {
        float distance = getDistanceMeters();
        if (distance < 0) {
            return "";
        }

        // Walking speed ~5 km/h = ~83 m/min
        int minutes = (int) (distance / 83);

        if (minutes < 1) {
            return "< 1 min walk";
        } else if (minutes < 60) {
            return minutes + " min walk";
        } else {
            int hours = minutes / 60;
            int remainingMinutes = minutes % 60;
            return hours + "h " + remainingMinutes + "m walk";
        }
    }
}
