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
                "✨ File Attachments: You can now attach files directly to your notes! Keep all your resources in one place.",
                "✨ Note Dependencies: Link notes together with 'Blocks', 'Blocked By', or 'Related To' relationships. Dependencies are now two-way!",
                "✨ Visual Dependency Indicators: Note cards on the main board now show color-coded icons to indicate their dependency status at a glance (e.g., a red lock for blocked notes).",
                "✨ @Note Linking in Goals: Quickly link to another note from the goals section by simply typing '@' followed by the note's name.",
                "✨ Enhanced Note Model: The underlying data structure for notes has been improved to support these new features."
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