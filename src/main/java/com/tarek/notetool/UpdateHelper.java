package com.tarek.notetool;

import javafx.application.Platform;

import java.io.File;
import java.io.IOException;
import java.util.function.BiConsumer;

/**
 * A helper class containing shared logic for version comparison and running the updater.
 */
public class UpdateHelper {

    /**
     * Compares two version strings (e.g., "3.5", "v4").
     * This method can handle decimal points within version parts.
     * @param v1 The first version string (e.g., from GitHub).
     * @param v2 The second version string (e.g., current app version).
     * @return true if v1 is newer than v2, false otherwise.
     */
    public static boolean isNewer(String v1, String v2) {
        // Remove leading 'v' (case-insensitive) and split by dots
        String[] parts1 = v1.toLowerCase().replace("v", "").split("\\.");
        String[] parts2 = v2.toLowerCase().replace("v", "").split("\\.");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            // Use Double.parseDouble to handle versions like "3.5"
            double part1 = i < parts1.length ? Double.parseDouble(parts1[i]) : 0;
            double part2 = i < parts2.length ? Double.parseDouble(parts2[i]) : 0;

            if (part1 > part2) return true;
            if (part1 < part2) return false;
        }
        // Versions are identical
        return false;
    }

    /**
     * Launches the external AutoUpdater.jar process and exits the main application.
     * @param errorHandler A consumer to show an error message if the updater fails to start.
     */
    public static void runUpdater(BiConsumer<String, String> errorHandler) {
        try {
            // Path to the updater JAR. Assumes it's in the same directory.
            String updaterJarPath = "AutoUpdater.jar";
            File updaterJar = new File(updaterJarPath);

            if (!updaterJar.exists()) {
                errorHandler.accept("Updater Not Found", "The updater application (AutoUpdater.jar) was not found in the application directory.");
                return;
            }

            // Launch the updater as a separate process
            new ProcessBuilder("java", "-jar", updaterJarPath, VersionInfo.CURRENT_VERSION).start();

            // Exit the main application
            Platform.exit();
        } catch (IOException e) {
            errorHandler.accept("Update Failed", "Could not start the updater process. Please try updating manually.\n\nError: " + e.getMessage());
        }
    }
}