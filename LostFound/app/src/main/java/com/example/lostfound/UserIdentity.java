package com.example.lostfound;

import android.content.Context;
import android.content.SharedPreferences;

public class UserIdentity {
    private static final String PREF_NAME = "UserPrefs";
    private static final String KEY_USER_ID = "UserID";
    private static final String KEY_PHONE = "PHONE";
    private static final String KEY_LINE = "LINE";

    public static void saveLoginInfo(Context context, String username, String phone, String lineId) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit()
                .putString(KEY_USER_ID, username)
                .putString(KEY_PHONE, phone == null ? "" : phone)
                .putString(KEY_LINE, lineId == null ? "" : lineId)
                .apply();
    }

    public static String getID(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_USER_ID, null);
    }

    public static String getPhone(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_PHONE, "");
    }

    public static String getLineId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_LINE, "");
    }

    public static boolean isLoggedIn(Context context) {
        return getID(context) != null;
    }

    public static void logout(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        prefs.edit().clear().apply();
    }
}
