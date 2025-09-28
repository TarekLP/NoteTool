package com.tarek.notetool;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.util.Pair;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class WelcomeViewController {

    @FXML
    private ListView<String> boardListView;

    @FXML
    private ListView<NoteManager.NoteBoardPair> recentNotesListView;

    @FXML
    private VBox whatsNewPane;

    private NoteManager noteManager;
    private MainApp mainApp;

    public void setNoteManager(NoteManager noteManager, MainApp mainApp) {
        this.noteManager = noteManager;
        this.mainApp = mainApp;
        refreshBoardList();
        refreshRecentNotesList();
    }

    @FXML
    private void initialize() {
        // --- Board List Setup ---
        boardListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                handleOpenBoard();
            }
        });

        ContextMenu boardContextMenu = new ContextMenu();
        MenuItem openBoardItem = new MenuItem("Open Board");
        openBoardItem.setOnAction(e -> handleOpenBoard());

        MenuItem exportBoardItem = new MenuItem("Export Board...");
        exportBoardItem.setOnAction(e -> handleExportBoard());

        MenuItem deleteBoardItem = new MenuItem("Delete Board");
        deleteBoardItem.setGraphic(new FontIcon(MaterialDesignD.DELETE_OUTLINE));
        deleteBoardItem.setStyle("-fx-text-fill: -color-danger-fg;");
        deleteBoardItem.setOnAction(e -> handleDeleteBoard());

        boardContextMenu.getItems().addAll(
                openBoardItem,
                new SeparatorMenuItem(),
                exportBoardItem,
                new SeparatorMenuItem(),
                deleteBoardItem
        );
        boardListView.setContextMenu(boardContextMenu);

        // --- Recent Notes List Setup ---
        recentNotesListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(NoteManager.NoteBoardPair item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.note.getTitle() + " [" + item.board.getName() + "]");
            }
        });

        recentNotesListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                NoteManager.NoteBoardPair selected = recentNotesListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    mainApp.openBoardWindow(selected.board);
                }
            }
        });

        // --- What's New Pane ---
        if (VersionInfo.shouldShowWhatsNew()) {
            try {
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tarek/notetool/whats-new-view.fxml"));
                Node whatsNewContent = loader.load();
                whatsNewPane.getChildren().add(whatsNewContent);
                VersionInfo.updateLastSeenVersion();
            } catch (IOException e) {
                whatsNewPane.getChildren().add(new Label("Could not load What's New content."));
                e.printStackTrace();
            }
        } else {
            whatsNewPane.setVisible(false);
            whatsNewPane.setManaged(false);
        }
    }

    private void refreshBoardList() {
        boardListView.setItems(FXCollections.observableArrayList(noteManager.getBoardNames()));
    }

    private void refreshRecentNotesList() {
        recentNotesListView.setItems(FXCollections.observableArrayList(noteManager.getRecentNotes()));
    }

    @FXML
    private void handleOpenBoard() {
        String selectedBoardName = boardListView.getSelectionModel().getSelectedItem();
        if (selectedBoardName != null) {
            noteManager.getBoard(selectedBoardName).ifPresent(mainApp::openBoardWindow);
        }
    }

    @FXML
    private void handleNewBoard() {
        Dialog<String> dialog = new TextInputDialog();
        dialog.setTitle("Create New Board");
        dialog.setHeaderText("Enter a name for the new board.");
        dialog.setContentText("Board Name:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(boardName -> {
            if (!boardName.trim().isEmpty()) {
                try {
                    noteManager.createBoard(boardName, new ArrayList<>());
                    refreshBoardList();
                } catch (IllegalArgumentException e) {
                    showError("Creation Failed", e.getMessage());
                }
            }
        });
    }

    @FXML
    private void handleImportBoard() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Board");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("NoteTool Board File", "*.json"));
        File file = fileChooser.showOpenDialog(boardListView.getScene().getWindow());

        if (file != null) {
            try {
                noteManager.importBoard(file);
                refreshBoardList();
            } catch (IOException e) {
                showError("Import Failed", "Could not import the board from the selected file.\n\nError: " + e.getMessage());
            }
        }
    }

    private void handleExportBoard() {
        String selectedBoardName = boardListView.getSelectionModel().getSelectedItem();
        if (selectedBoardName == null) return;

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Board");
        fileChooser.setInitialFileName(selectedBoardName.replaceAll("[^a-zA-Z0-9.\\-]", "_") + ".json");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("NoteTool Board File", "*.json"));
        File file = fileChooser.showSaveDialog(boardListView.getScene().getWindow());

        if (file != null) {
            try {
                noteManager.exportBoard(selectedBoardName, file);
            } catch (IOException e) {
                showError("Export Failed", "Could not export the board.\n\nError: " + e.getMessage());
            }
        }
    }

    private void handleDeleteBoard() {
        String selectedBoardName = boardListView.getSelectionModel().getSelectedItem();
        if (selectedBoardName == null) return;

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Board");
        confirmation.setHeaderText("Are you sure you want to delete the board '" + selectedBoardName + "'?");
        confirmation.setContentText("This action is permanent and cannot be undone.");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                noteManager.removeBoard(selectedBoardName);
                refreshBoardList();
                refreshRecentNotesList();
            }
        });
    }

    @FXML
    private void handlePreferences() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tarek/notetool/preferences-view.fxml"));
            PreferencesViewController controller = new PreferencesViewController();
            loader.setController(controller);
            Parent page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Preferences");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            Scene scene = boardListView.getScene();
            dialogStage.initOwner(scene.getWindow());
            Scene dialogScene = new Scene(page);
            ThemeManager.loadAndApplyTheme(dialogScene);
            dialogStage.setScene(dialogScene);

            controller.setDialogStage(dialogStage);
            controller.setMainScene(scene);
            controller.setUsers(new ArrayList<>()); // User management might need to be re-thought
            controller.setTags(noteManager.getAllTags());
            controller.setCurrentUser(noteManager.getCurrentUser());

            dialogStage.showAndWait();

            // After closing preferences, update data and refresh UI
            noteManager.setCurrentUser(controller.getUpdatedCurrentUser());
            noteManager.setAllTags(controller.getUpdatedTags());
            // No need to refresh lists here as they don't depend on this data directly

        } catch (Exception e) {
            showError("Failed to open Preferences", "Could not load the preferences view. Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleQuit() {
        Platform.exit();
    }

    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }
}