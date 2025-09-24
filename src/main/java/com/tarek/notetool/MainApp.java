package com.tarek.notetool;

import atlantafx.base.theme.PrimerDark;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;
import javafx.scene.image.Image;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

public class MainApp extends Application {

    // Define the path for the data directory. It checks for OneDrive and uses it if available.
    private static final Path DATA_DIRECTORY_PATH = determineDataPath();

    private NoteManager noteManager;
    private Timeline autoSaveTimeline;

    /**
     * Gets the path to the directory where attachments are stored.
     * @return The Path for the application's attachments directory.
     */
    public static Path getAttachmentsDirectory() {
        return DATA_DIRECTORY_PATH.resolve("attachments");
    }

    /**
     * Gets the path to the directory where gallery images are stored.
     * @return The Path for the application's gallery directory.
     */
    public static Path getGalleryDirectory() {
        return DATA_DIRECTORY_PATH.resolve("gallery");
    }
    /**
     * Determines the appropriate data storage path.
     * If a OneDrive folder is detected via environment variables, it's used as the base.
     * Otherwise, it defaults to the standard user home folder.
     * @return The Path for the application's data directory.
     */
    private static Path determineDataPath() {
        String oneDrive = System.getenv("OneDrive");
        Path baseDir;

        if (oneDrive != null && !oneDrive.trim().isEmpty()) {
            System.out.println("OneDrive detected. Using OneDrive for data storage.");
            // The OneDrive env var typically points to the root of the OneDrive folder, e.g., C:\\Users\\user\\OneDrive
            // The user's Documents folder is often inside this directory. Note the escaped backslash to prevent a unicode parsing error.
            baseDir = Paths.get(oneDrive);
        } else {
            System.out.println("OneDrive not detected. Using default user folder for data storage.");
            baseDir = Paths.get(System.getProperty("user.home"));
        }

        return baseDir.resolve(Paths.get(
                "Documents",
                "TarekPrograms",
                "NoteTool"
        ));
    }

    @Override
    public void start(Stage stage) throws Exception {
        // Check for data in the old format and offer to migrate it before loading anything.
        checkForAndOfferMigration();

        // Ensure the data subdirectories exist
        try {
            Files.createDirectories(getAttachmentsDirectory());
            Files.createDirectories(getGalleryDirectory());
        } catch (IOException e) {
            System.err.println("Could not create data subdirectories: " + e.getMessage());
            // This is not fatal, so we just log it and continue.
        }


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
        Scene scene = new Scene(root, 1450, 900);

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

    /**
     * Checks for data in the old single-file format and offers to migrate it.
     * Migration is offered if the old file exists and the new directory structure does not.
     */
    private void checkForAndOfferMigration() {
        // The old data path was a specific file inside a 'Saved' subdirectory.
        Path oldDataFile = DATA_DIRECTORY_PATH.resolve(Paths.get("Saved", "notemanager.json"));

        // A good indicator that new data exists (or migration has occurred) is the presence of the 'boards' directory.
        Path newBoardsDir = DATA_DIRECTORY_PATH.resolve("boards");

        if (Files.exists(oldDataFile) && !Files.exists(newBoardsDir)) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Data Migration");
            confirmation.setHeaderText("Legacy Data Format Detected");
            confirmation.setContentText("An older data file was found. Would you like to migrate it to the new format?\n\n" +
                    "The old file will be renamed to 'notemanager.json.migrated' after a successful migration.");
            confirmation.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

            Optional<ButtonType> result = confirmation.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.YES) {
                System.out.println("User approved migration. Starting process...");
                try {
                    // 1. Load from the old single-file format
                    NoteManager managerToMigrate = NoteManager.loadFromFile(oldDataFile.toString());

                    // 2. Save to the new directory format
                    if (!Files.exists(DATA_DIRECTORY_PATH)) {
                        Files.createDirectories(DATA_DIRECTORY_PATH);
                    }
                    managerToMigrate.saveToDirectory(DATA_DIRECTORY_PATH);

                    // 3. Rename the old file to prevent this from running again
                    Path migratedFile = oldDataFile.resolveSibling("notemanager.json.migrated");
                    Files.move(oldDataFile, migratedFile);

                    System.out.println("Migration successful. Old file renamed to: " + migratedFile);
                    Alert info = new Alert(Alert.AlertType.INFORMATION);
                    info.setTitle("Migration Complete");
                    info.setHeaderText("Your data has been successfully migrated.");
                    info.setContentText("The application will now load your migrated data.");
                    info.showAndWait();

                } catch (IOException e) {
                    System.err.println("Data migration failed: " + e.getMessage());
                    Alert error = new Alert(Alert.AlertType.ERROR);
                    error.setTitle("Migration Failed");
                    error.setHeaderText("An error occurred during data migration.");
                    error.setContentText("The application will start with fresh data. Your old data file has not been changed.\n\nError: " + e.getMessage());
                    error.showAndWait();
                }
            } else {
                System.out.println("User declined migration. The old file will be ignored.");
            }
        }
    }

    private void loadData() {
        try {
            // loadFromDirectory will create a new manager if the directory or files don't exist.
            noteManager = NoteManager.loadFromDirectory(DATA_DIRECTORY_PATH);
            System.out.println("Data loaded from " + DATA_DIRECTORY_PATH);
        } catch (IOException e) {
            // This would be for a more serious I/O error, like permissions.
            System.err.println("Critical error loading data, starting with a new NoteManager. Reason: " + e.getMessage());
            noteManager = new NoteManager();
        }
    }

    private void saveData() {
        try {
            // Ensure the directory exists before saving
            if (!Files.exists(DATA_DIRECTORY_PATH)) {
                Files.createDirectories(DATA_DIRECTORY_PATH);
                System.out.println("Created data directory: " + DATA_DIRECTORY_PATH.toAbsolutePath());
            }
            noteManager.saveToDirectory(DATA_DIRECTORY_PATH);
            System.out.println("Successfully saved data to " + DATA_DIRECTORY_PATH);
        } catch (IOException e) {
            System.err.println("Error saving data: " + e.getMessage());
        }
    }

}