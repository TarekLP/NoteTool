package com.tarek.notetool;

import javafx.scene.Scene;
import javafx.scene.paint.Color;
import java.util.Locale;

import java.util.prefs.Preferences;

public class ThemeManager {

    private static final Preferences prefs = Preferences.userNodeForPackage(MainApp.class);

    // Preference Keys
    private static final String ACCENT_FG_KEY = "colorAccentFg";
    private static final String ACCENT_EMPHASIS_KEY = "colorAccentEmphasis";
    private static final String ACCENT_MUTED_KEY = "colorAccentMuted";
    private static final String ACCENT_SUBTLE_KEY = "colorAccentSubtle";

    // Default Colors (from purple-theme.css)
    public static final Color DEFAULT_ACCENT_FG = Color.web("#8A2BE2");       // BlueViolet
    public static final Color DEFAULT_ACCENT_EMPHASIS = Color.web("#9370DB"); // MediumPurple
    public static final Color DEFAULT_ACCENT_MUTED = Color.web("rgba(147, 112, 219, 0.4)");
    public static final Color DEFAULT_ACCENT_SUBTLE = Color.web("rgba(147, 112, 219, 0.15)");

    /**
     * A simple data class for serializing/deserializing theme colors.
     */
    public record ThemeData(String accentFg, String accentEmphasis, String accentMuted, String accentSubtle) {}

    /**
     * Loads color preferences and applies them to the given scene.
     * If no preferences are found, default purple colors are used.
     * @param scene The scene to apply the theme to.
     */
    public static void loadAndApplyTheme(Scene scene) {
        String fg = prefs.get(ACCENT_FG_KEY, colorToWeb(DEFAULT_ACCENT_FG));
        String emphasis = prefs.get(ACCENT_EMPHASIS_KEY, colorToWeb(DEFAULT_ACCENT_EMPHASIS));
        String muted = prefs.get(ACCENT_MUTED_KEY, colorToWeb(DEFAULT_ACCENT_MUTED));
        String subtle = prefs.get(ACCENT_SUBTLE_KEY, colorToWeb(DEFAULT_ACCENT_SUBTLE));

        applyColors(scene, fg, emphasis, muted, subtle);
    }

    /**
     * Saves the given colors to the user's preferences.
     * @param fg The accent foreground color.
     * @param emphasis The accent emphasis color.
     * @param muted The accent muted color.
     * @param subtle The accent subtle color.
     */
    public static void saveColorPreferences(Color fg, Color emphasis, Color muted, Color subtle) {
        prefs.put(ACCENT_FG_KEY, colorToWeb(fg));
        prefs.put(ACCENT_EMPHASIS_KEY, colorToWeb(emphasis));
        prefs.put(ACCENT_MUTED_KEY, colorToWeb(muted));
        prefs.put(ACCENT_SUBTLE_KEY, colorToWeb(subtle));
    }

    /**
     * Applies a set of colors to a scene's root node as inline styles.
     * @param scene The scene to style.
     * @param fg The accent foreground color as a web string.
     * @param emphasis The accent emphasis color as a web string.
     * @param muted The accent muted color as a web string.
     * @param subtle The accent subtle color as a web string.
     */
    public static void applyColors(Scene scene, String fg, String emphasis, String muted, String subtle) {
        if (scene == null || scene.getRoot() == null) {
            return;
        }
        String style = String.format(
                "-fx-accent: %s; " +
                "-color-accent-fg: %s; " +
                "-color-accent-emphasis: %s; " +
                "-color-accent-muted: %s; " +
                "-color-accent-subtle: %s;",
                // Use the foreground color for the base -fx-accent as well
                fg, fg, emphasis, muted, subtle
        );
        scene.getRoot().setStyle(style);
    }

    /**
     * Converts a JavaFX Color object to its web string representation (e.g., "rgba(255,0,0,0.5)").
     * This format is necessary for inline styling.
     * @param color The color to convert.
     * @return The CSS string representation of the color.
     */
    public static String colorToWeb(Color color) {
        // Use Locale.US to ensure the decimal separator is always a period ('.')
        return String.format(Locale.US, "rgba(%d, %d, %d, %f)",
                (int) (color.getRed() * 255),
                (int) (color.getGreen() * 255),
                (int) (color.getBlue() * 255),
                color.getOpacity());
    }
}