package com.example.lostfound;

import android.content.Context;
import android.content.SharedPreferences;

public class UserIdentity {
    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_USER_ID = "UserID";

    public static void saveID(Context context, String userId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().putString(KEY_USER_ID, userId).apply();
    }

    public static String getID(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_ID, null);
    }

    public static boolean isLoggedIn(Context context) {
        return getID(context) != null;
    }

    public static void logout(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().remove(KEY_USER_ID).apply();
    }
}