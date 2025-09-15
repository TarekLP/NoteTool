package com.tarek.notetool;

import atlantafx.base.theme.PrimerDark;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.stage.Modality;
import javafx.scene.image.Image;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class MainApp extends Application {

    // Define the path for the data file within the user's Documents folder.
    private static final Path DATA_FILE_PATH = Paths.get(
            System.getProperty("user.home"),
            "Documents",
            "TarekPrograms",
            "NoteTool",
            "Saved",
            "notemanager.json"
    );

    private NoteManager noteManager;
    private Timeline autoSaveTimeline;

    @Override
    public void start(Stage stage) throws Exception {
        // Apply the modern theme
        Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());

        // Load the NoteManager data
        loadData();

        // Load the FXML file for the main view
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tarek/notetool/main-view.fxml"));

        // Manually create and set the controller. This bypasses the fx:controller attribute in the FXML,
        // which was causing a ClassNotFoundException because it likely uses a relative name ("MainViewController")
        // instead of the fully qualified name.
        MainViewController controller = new MainViewController();
        loader.setController(controller);

        Parent root = loader.load();

        // Pass the NoteManager to the now-initialized controller
        controller.setNoteManager(noteManager);

        // Set up the main window (Stage)
        stage.setTitle("Note Tool");
        // Add the application icon. The .ico format is used for the launch4j executable and works here too.
        stage.getIcons().add(new Image(getClass().getResourceAsStream("/com/tarek/notetool/assets/icon.ico")));
        Scene scene = new Scene(root, 1200, 800);

        // Set up global shortcuts now that the scene is available
        controller.setupShortcuts(scene);

        // Add our custom purple accent override
        scene.getStylesheets().add(getClass().getResource("/com/tarek/notetool/purple-theme.css").toExternalForm());
        scene.getStylesheets().add(getClass().getResource("/com/tarek/notetool/custom-styles.css").toExternalForm());

        // Load and apply user-defined or default colors, overriding the CSS.
        ThemeManager.loadAndApplyTheme(scene);
        stage.setScene(scene);

        // Show "What's New" dialog if this is a new version, before showing the main stage
        if (VersionInfo.shouldShowWhatsNew()) {
            showWhatsNewDialog(stage);
            VersionInfo.updateLastSeenVersion();
        }

        stage.show();

        // Set up auto-save to run periodically
        setupAutoSave();

        // Add a handler to save data when the application is closed
        stage.setOnCloseRequest(event -> {
            if (autoSaveTimeline != null) {
                autoSaveTimeline.stop();
            }
            // Perform one final save if there are pending changes.
            if (noteManager != null && noteManager.isDirty()) {
                System.out.println("Performing final save on exit...");
                saveData();
            }
        });
    }

    /**
     * Initializes and starts a timeline to periodically save the application data
     * if any changes have been made.
     */
    private void setupAutoSave() {
        autoSaveTimeline = new Timeline(new KeyFrame(Duration.seconds(30), e -> {
            if (noteManager != null && noteManager.isDirty()) {
                System.out.println("Auto-saving changes...");
                saveData();
            }
        }));
        autoSaveTimeline.setCycleCount(Timeline.INDEFINITE);
        autoSaveTimeline.play();
    }

    private void showWhatsNewDialog(Stage owner) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tarek/notetool/whats-new-view.fxml"));
            Parent page = loader.load();

            // The controller is now loaded via FXML, get it from the loader
            WhatsNewController controller = loader.getController();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("What's New");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(owner);
            Scene scene = new Scene(page);
            // Apply the user's custom theme to the new dialog's scene
            ThemeManager.loadAndApplyTheme(scene);
            dialogStage.setScene(scene);

            controller.setDialogStage(dialogStage);
            dialogStage.showAndWait();
        } catch (IOException e) {
            System.err.println("Failed to load What's New dialog: " + e.getMessage());
        }
    }

    private void loadData() {
        if (!Files.exists(DATA_FILE_PATH)) {
            System.out.println("Data file not found, starting with a new NoteManager.");
            noteManager = new NoteManager();
            return;
        }
        try {
            noteManager = NoteManager.loadFromFile(DATA_FILE_PATH.toString());
            System.out.println("Successfully loaded data from " + DATA_FILE_PATH);
        } catch (IOException e) {
            System.out.println("Could not load data, starting with a new NoteManager. Reason: " + e.getMessage());
            noteManager = new NoteManager();
        }
    }

    private void saveData() {
        try {
            // Ensure the directory exists before saving
            Path parentDir = DATA_FILE_PATH.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
                System.out.println("Created data directory: " + parentDir.toAbsolutePath());
            }

            noteManager.saveToFile(DATA_FILE_PATH.toString());
            System.out.println("Successfully saved data to " + DATA_FILE_PATH);
        } catch (IOException e) {
            System.err.println("Error saving data: " + e.getMessage());
        }
    }

}