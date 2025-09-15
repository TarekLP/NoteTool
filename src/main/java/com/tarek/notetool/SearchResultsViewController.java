package com.tarek.notetool;

import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import javafx.scene.input.KeyCode;
import javafx.util.Pair;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class SearchResultsViewController {

    @FXML
    private ListView<Pair<Note, Board>> resultsListView;

    private Stage dialogStage;
    private Pair<Note, Board> selectedResult = null;

    @FXML
    private void initialize() {
        resultsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Pair<Note, Board> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    Note note = item.getKey();
                    Board board = item.getValue();
                    setText("'" + note.getTitle() + "' in board [" + board.getName() + "]");
                }
            }
        });

        resultsListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) { // Double-click to select and close
                selectedResult = resultsListView.getSelectionModel().getSelectedItem();
                if (selectedResult != null) {
                    dialogStage.close();
                }
            }
        });
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
        // Add ESC key shortcut to close
        this.dialogStage.getScene().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                dialogStage.close();
            }
        });
    }

    public void setSearchResults(Map<Note, Board> results) {
        List<Pair<Note, Board>> resultList = results.entrySet().stream()
            .map(entry -> new Pair<>(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
        resultsListView.setItems(FXCollections.observableArrayList(resultList));
    }

    public Optional<Pair<Note, Board>> getSelectedResult() {
        return Optional.ofNullable(selectedResult);
    }
}