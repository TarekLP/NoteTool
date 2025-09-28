package com.tarek.notetool;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextField;
import javafx.scene.control.Alert;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Tooltip;
import javafx.scene.control.Button;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseButton;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.TilePane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import javafx.embed.swing.SwingFXUtils;

public class ImageGalleryViewController {

    // A custom DataFormat to identify drags originating from our gallery.
    public static final DataFormat GALLERY_IMAGE_DATA_FORMAT = new DataFormat("com.tarek.notetool.galleryImage");

    @FXML
    private TilePane imageTilePane;
    @FXML
    private Button addImageButton;
    @FXML
    private Button pasteImageButton;
    @FXML
    private Button closeGalleryButton;
    @FXML
    private TextField searchField;

    private NoteManager noteManager;
    private Runnable onCloseRequestHandler;

    @FXML
    private void initialize() {
        addImageButton.setOnAction(e -> handleAddImageFromFile());
        pasteImageButton.setOnAction(e -> handlePasteImage());

        // Add listener to search field to filter the gallery
        searchField.textProperty().addListener((obs, oldVal, newVal) -> refreshGallery());

        closeGalleryButton.setGraphic(new FontIcon(MaterialDesignC.CLOSE));
        closeGalleryButton.getStyleClass().add("rich-text-editor-button"); // Use a flat style for the icon button
        Tooltip.install(closeGalleryButton, new Tooltip("Close Gallery"));
        closeGalleryButton.setOnAction(e -> {
            if (onCloseRequestHandler != null) {
                onCloseRequestHandler.run();
            }
        });

        // --- Responsive Tile Size ---
        // Bind the preferred tile width to the width of the TilePane itself.
        // This will create a 2-column layout that resizes with the panel.
        // The subtraction accounts for horizontal gap and padding.
        imageTilePane.prefTileWidthProperty().bind(imageTilePane.widthProperty().subtract(20));

        // --- Drag and Drop to Add Images ---
        imageTilePane.setOnDragOver(event -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()) {
                // Check if any of the files are images
                boolean hasImageFile = db.getFiles().stream()
                        .anyMatch(file -> file.getName().toLowerCase().matches(".*\\.(png|jpg|jpeg|gif|bmp)$"));
                if (hasImageFile) {
                    event.acceptTransferModes(TransferMode.COPY);
                }
            }
            event.consume();
        });

        imageTilePane.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                db.getFiles().forEach(file -> saveAndAddImage(file.toPath()));
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    public void setOnCloseRequestHandler(Runnable onCloseRequestHandler) {
        this.onCloseRequestHandler = onCloseRequestHandler;
    }

    public void setNoteManager(NoteManager noteManager) {
        this.noteManager = noteManager;
        refreshGallery();
    }

    private void refreshGallery() {
        imageTilePane.getChildren().clear();
        String query = searchField.getText().toLowerCase();

        if (noteManager != null) {
            noteManager.getGalleryImagePaths().stream()
                    .filter(imagePath -> query.isEmpty() || imagePath.toLowerCase().contains(query))
                    .forEach(this::createImageView);
            if (query.isEmpty()) {
                searchField.clear();
            }
        }
    }

    private void handleAddImageFromFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Add Image to Gallery");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Image Files", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp")
        );
        File selectedFile = fileChooser.showOpenDialog(imageTilePane.getScene().getWindow());

        if (selectedFile != null) {
            saveAndAddImage(selectedFile.toPath());
        }
    }

    private void handlePasteImage() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        if (clipboard.hasImage()) {
            Image fxImage = clipboard.getImage();
            Path tempFile = null;
            try {
                // Create a temporary file to save the clipboard image
                tempFile = Files.createTempFile("pasted-image-", ".png");

                // Convert JavaFX Image to AWT BufferedImage and write it to the file
                BufferedImage bufferedImage = SwingFXUtils.fromFXImage(fxImage, null);
                ImageIO.write(bufferedImage, "png", tempFile.toFile());

                // Use the existing logic to save and add the image to the gallery
                saveAndAddImage(tempFile);

            } catch (IOException e) {
                showError("Paste Failed", "Could not process the image from the clipboard. Error: " + e.getMessage());
            } finally {
                if (tempFile != null) {
                    try { Files.deleteIfExists(tempFile); } catch (IOException ignored) { /* Not critical */ }
                }
            }
        } else if (clipboard.hasFiles()) {
            clipboard.getFiles().stream()
                    .filter(file -> file.getName().toLowerCase().matches(".*\\.(png|jpg|jpeg|gif|bmp)$"))
                    .forEach(file -> saveAndAddImage(file.toPath())); // This already calls the correct method. No change needed, but good to confirm.
        }
    }

    private void saveAndAddImage(Path sourcePath) {
        try {
            Path galleryDir = MainApp.getGalleryDirectory();
            String uniqueFileName = UUID.randomUUID().toString().substring(0, 8) + "-" + sourcePath.getFileName().toString();
            Path targetPath = galleryDir.resolve(uniqueFileName);

            Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);

            // Add to the model and update UI
            noteManager.addGalleryImagePath(uniqueFileName);
            createImageView(uniqueFileName);

        } catch (IOException e) {
            showError("Save Failed", "Could not save the image to the gallery. Error: " + e.getMessage());
        }
    }

    private void createImageView(String imageFileName) {
        Path imagePath = MainApp.getGalleryDirectory().resolve(imageFileName);
        // Load at a higher resolution so images don't look blurry when the panel is resized larger.
        Image image = new Image(imagePath.toUri().toString(), 250, 250, true, true);
        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);

        // Bind the image view's size to the tile size for responsive scaling.
        imageView.fitWidthProperty().bind(imageTilePane.prefTileWidthProperty());
        // --- Container for image and overlays ---
        HBox buttonBar = new HBox(5);
        buttonBar.setAlignment(Pos.TOP_RIGHT);
        buttonBar.setPadding(new Insets(4));
        buttonBar.setVisible(false); // Initially hidden

        // --- UI for Copying ---
        FontIcon copyIcon = new FontIcon(MaterialDesignP.PAPERCLIP);
        copyIcon.getStyleClass().add("image-overlay-icon");
        Tooltip.install(copyIcon, new Tooltip("Copy Image to Clipboard"));
        copyIcon.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                ClipboardContent content = new ClipboardContent();
                content.putImage(image);
                Clipboard.getSystemClipboard().setContent(content);
                e.consume();
            }
        });

        // --- UI for Deleting ---
        FontIcon deleteIcon = new FontIcon(MaterialDesignD.DELETE);
        deleteIcon.getStyleClass().addAll("image-overlay-icon", "image-delete-icon");
        Tooltip.install(deleteIcon, new Tooltip("Delete Image"));
        deleteIcon.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                handleDeleteImage(imageFileName, imagePath);
                e.consume();
            }
        });

        StackPane imageContainer = new StackPane(imageView);
        imageContainer.setCursor(Cursor.OPEN_HAND);

        buttonBar.getChildren().addAll(copyIcon, deleteIcon);
        imageContainer.getChildren().add(buttonBar);

        // --- UI for showing usage ---
        List<Note> usingNotes = noteManager.getNotesUsingImage(imageFileName);
        if (!usingNotes.isEmpty()) {
            FontIcon linkIcon = new FontIcon(MaterialDesignL.LINK_VARIANT);
            linkIcon.getStyleClass().add("image-usage-icon"); // A new style class for color, size etc.
            linkIcon.setIconSize(16);

            String tooltipText = "Used in:\n" +
                    usingNotes.stream()
                            .map(Note::getTitle)
                            .map(title -> "• " + title)
                            .collect(Collectors.joining("\n"));
            Tooltip.install(linkIcon, new Tooltip(tooltipText));

            imageContainer.getChildren().add(linkIcon);
            StackPane.setAlignment(linkIcon, Pos.BOTTOM_LEFT);
            StackPane.setMargin(linkIcon, new Insets(0, 0, 4, 4));
        }

        imageContainer.setOnMouseEntered(e -> buttonBar.setVisible(true));
        imageContainer.setOnMouseExited(e -> buttonBar.setVisible(false));

        // --- Drag and Drop Handling ---
        imageContainer.setOnDragDetected(event -> {
            Dragboard db = imageContainer.startDragAndDrop(TransferMode.COPY);
            ClipboardContent content = new ClipboardContent();
            // Put the unique filename into the dragboard. This is what the note editor will receive.
            content.put(GALLERY_IMAGE_DATA_FORMAT, imageFileName);

            // Create a snapshot for the drag view
            SnapshotParameters params = new SnapshotParameters();
            params.setFill(Color.TRANSPARENT);
            WritableImage snapshot = imageContainer.snapshot(params, null);
            db.setDragView(snapshot, event.getX(), event.getY());

            db.setContent(content);
            imageContainer.setCursor(Cursor.CLOSED_HAND);
            event.consume();
        });

        imageContainer.setOnDragDone(event -> {
            imageContainer.setCursor(Cursor.OPEN_HAND);
            event.consume();
        });

        imageTilePane.getChildren().add(imageContainer);
    }

    private void handleDeleteImage(String imageFileName, Path imagePath) {
        List<Note> usingNotes = noteManager.getNotesUsingImage(imageFileName);
        boolean isInUse = !usingNotes.isEmpty();

        // If the image is in use, ask for confirmation.
        if (isInUse) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Delete Image");
            confirmation.setHeaderText("This image is currently used by " + usingNotes.size() + " note(s)."); // Corrected typo in my thought process, code is fine.
            String noteTitles = usingNotes.stream().map(Note::getTitle).limit(5).collect(Collectors.joining("\n- ", "\n- ", ""));
            confirmation.setContentText("Are you sure you want to delete it? This will create broken links in the notes that reference it." + noteTitles);

            Optional<ButtonType> result = confirmation.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return; // User cancelled the deletion.
            }
        }

        // Proceed with deletion.
        // Remove from the model first.
        if (noteManager.removeGalleryImagePath(imageFileName)) {
            // If successful, delete the physical file.
            try {
                Files.deleteIfExists(imagePath);
            } catch (IOException ioException) {
                showError("Deletion Failed", "Could not delete the image file from disk: " + ioException.getMessage());
            }
        }
        refreshGallery();
    }

    private void showError(String header, String content) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR);
        if (imageTilePane != null && imageTilePane.getScene() != null) {
            alert.initOwner(imageTilePane.getScene().getWindow());
        }
        alert.setTitle("Image Gallery Error");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}