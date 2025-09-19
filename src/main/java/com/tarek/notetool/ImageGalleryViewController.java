package com.tarek.notetool;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
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
import javafx.scene.input.TransferMode;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.TilePane;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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

    private NoteManager noteManager;
    private Runnable onCloseRequestHandler;

    @FXML
    private void initialize() {
        addImageButton.setOnAction(e -> handleAddImageFromFile());
        pasteImageButton.setOnAction(e -> handlePasteImage());

        closeGalleryButton.setGraphic(new FontIcon(MaterialDesignC.CLOSE));
        closeGalleryButton.getStyleClass().add("rich-text-editor-button"); // Use a flat style for the icon button
        Tooltip.install(closeGalleryButton, new Tooltip("Close Gallery"));
        closeGalleryButton.setOnAction(e -> {
            if (onCloseRequestHandler != null) {
                onCloseRequestHandler.run();
            }
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
        if (noteManager != null) {
            for (String imagePath : noteManager.getGalleryImagePaths()) {
                createImageView(imagePath);
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
            showError("Paste Not Implemented", "Pasting images from the clipboard is a great next step to implement!");
            // To implement this, you would:
            // 1. Get the Image object: clipboard.getImage()
            // 2. Use a Swing/AWT utility (ImageIO) to write this Image object to a temporary file.
            // 3. Call saveAndAddImage() with the path to that temporary file.
        } else if (clipboard.hasFiles()) {
            clipboard.getFiles().stream()
                    .filter(file -> file.getName().matches(".*\\.(png|jpg|jpeg|gif|bmp)$"))
                    .forEach(file -> saveAndAddImage(file.toPath()));
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
        Image image = new Image(imagePath.toUri().toString(), 120, 120, true, true); // Load resized
        ImageView imageView = new ImageView(image);
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(120);
        imageView.setFitHeight(120);

        // --- Container for image and overlays ---
        StackPane imageContainer = new StackPane(imageView);
        imageContainer.setCursor(Cursor.OPEN_HAND);

        // --- UI for Deleting ---
        FontIcon deleteIcon = new FontIcon(MaterialDesignC.CLOSE_CIRCLE);
        deleteIcon.getStyleClass().add("image-delete-icon");
        deleteIcon.setVisible(false); // Initially hidden
        deleteIcon.setOnMouseClicked(e -> {
            noteManager.removeGalleryImagePath(imageFileName);
            // The physical file is left behind, but could be deleted here too.
            refreshGallery();
            e.consume();
        });
        imageContainer.getChildren().add(deleteIcon);
        StackPane.setAlignment(deleteIcon, Pos.TOP_RIGHT);
        StackPane.setMargin(deleteIcon, new Insets(4, 4, 0, 0));

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

        imageContainer.setOnMouseEntered(e -> deleteIcon.setVisible(true));
        imageContainer.setOnMouseExited(e -> deleteIcon.setVisible(false));

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

    private void showError(String header, String content) {
        // In a real implementation, you'd use a proper Alert dialog.
        System.err.println(header + ": " + content);
    }
}