package com.tarek.notetool;

import java.util.prefs.Preferences;

public class VersionInfo {
    // The current version of the application. Increment this for new releases.
    public static final String CURRENT_VERSION = "5";

    private static final String LAST_SEEN_VERSION_KEY = "lastSeenWhatsNewVersion";
    private static final Preferences prefs = Preferences.userNodeForPackage(MainApp.class);

    /**
     * Checks if the "What's New" dialog should be shown.
     * This is true if the current version is different from the last version
     * for which the dialog was shown.
     * @return true if the dialog should be shown, false otherwise.
     */
    public static boolean shouldShowWhatsNew() {
        String lastSeenVersion = prefs.get(LAST_SEEN_VERSION_KEY, "0.0.0");
        return !CURRENT_VERSION.equals(lastSeenVersion);
    }

    /**
     * Updates the stored preference to the current version, so the dialog
     * won't be shown again until the next version.
     */
    public static void updateLastSeenVersion() {
        prefs.put(LAST_SEEN_VERSION_KEY, CURRENT_VERSION);
    }
}