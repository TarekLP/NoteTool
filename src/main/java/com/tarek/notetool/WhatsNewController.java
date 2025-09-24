package com.tarek.notetool;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

import java.util.List;

public class WhatsNewController {

    @FXML
    private Label headerLabel;

    @FXML
    private ListView<String> changesListView;

    @FXML
    private Button closeButton;

    private Stage dialogStage;

    @FXML
    private void initialize() {
        headerLabel.setText("What's New in v" + VersionInfo.CURRENT_VERSION);

        List<String> changes = List.of(
                "✨ Enhanced Markdown Preview: The live preview now supports syntax highlighting for code blocks and renders GitHub-style task lists (`- [x]`).",
                "✨ Live Markdown Editor: The note editor now features a real-time, side-by-side preview. No more switching tabs to see your formatted text and images!",
                "✨ Slide-out Image Gallery: A new global panel on the right to collect reference images. Toggle it with the new image icon in the top bar.",
                "✨ Customizable Columns: Right-click a column header to rename, add, delete, or reorder columns.",
                "✨ Embed Images in Notes: Type `@` in the note editor to search and embed images from your gallery directly into the note content.",
                "✨ Add images to the gallery from your computer, by pasting image files, or by pasting image data (like screenshots) from your clipboard.",
                "✨ Drag-and-Drop References: Drag images from the gallery and drop them onto a note to create a visual reference.",
                "✨ Note Model Update: The data model for notes and boards has been updated to support customizable columns and image references."
        );

        changesListView.setItems(FXCollections.observableArrayList(changes));
        // Make list view items wrap text
        changesListView.setCellFactory(param -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item);
                    setWrapText(true);
                }
            }
        });
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
    }

    @FXML
    private void handleClose() {
        dialogStage.close();
    }
}