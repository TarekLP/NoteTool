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
                "✨ Slide-out Image Gallery: A new global panel on the right to collect reference images. Toggle it with the new image icon in the top bar.",
                "✨ Drag-and-Drop References: Drag images from the gallery and drop them onto a note to create a visual reference.",
                "✨ Add images to the gallery from your computer or by pasting image files from your clipboard.",
                "✨ Note Model Update: The data model for notes has been updated to support image references."
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