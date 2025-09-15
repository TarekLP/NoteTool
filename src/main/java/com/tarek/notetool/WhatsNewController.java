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
                "✨ Auto-Focus for Dialogs: Input fields are now automatically focused when dialogs open, so you can start typing immediately.",
                "✨ Unsaved Changes Confirmation: The app will now warn you if you try to close an editor with unsaved changes, preventing accidental data loss.",
                "✨ Improved Purple Theme: The purple accent color is now applied more consistently across all buttons and UI elements.",
                "✨ 'What's New' Dialog: You're looking at it! This dialog will now appear on startup after an update to highlight new features."
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