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
                "✨ Hierarchical Goals: Break down complex tasks with nested sub-goals in a new tree view.",
                "✨ Smart Goal Completion: Parent goals automatically complete when all sub-goals are done. Checking a parent also completes all its children.",
                "✨ Column Sorting: Instantly sort notes within a column by Priority, Due Date, or Title.",
                "✨ Automatic User Assignment: New and duplicated notes are now automatically assigned to the current user.",
                "✨ Quick Reordering: Use the new 'Move to Top/Bottom' context menu option to organize notes faster.",
                "✨ Clear Completed Goals: A new button in the note editor helps you clean up your task list with a single click.",
                "✨ Robust Editor: The note editor has been refactored to be more resilient against build and caching issues."
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