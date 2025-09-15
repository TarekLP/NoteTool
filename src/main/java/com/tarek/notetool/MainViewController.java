package com.tarek.notetool;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.geometry.Insets;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.ListCell;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.input.KeyCombination;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Tooltip;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Alert;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.WritableImage;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.scene.Node;
import javafx.util.Pair;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;
import java.time.format.DateTimeFormatter;
import java.io.File;
import java.util.Arrays;
import javafx.beans.value.ChangeListener;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;

public class MainViewController {

    /**
     * A private record to hold the result from the quick-add dialog.
     */
    private record QuickAddResult(String title, String boardName, Note.Status status) {}

    @FXML
    private ListView<String> boardListView;

    @FXML
    private HBox columnsContainer;

    @FXML
    private ScrollPane boardScrollPane;

    @FXML
    private Label boardTitleLabel;

    @FXML
    private TextField searchField;

    @FXML
    private Button preferencesButton;

    @FXML
    private ToggleButton showArchivedToggle;

    @FXML
    private ListView<NoteManager.NoteBoardPair> recentNotesListView;

    private NoteManager noteManager;
    private Board currentBoard;
    private final Map<Note.Status, VBox> noteContainersMap = new EnumMap<>(Note.Status.class);
    private List<User> systemUsers;
    private ChangeListener<String> boardSelectionListener;

    public void setNoteManager(NoteManager noteManager) {
        this.noteManager = noteManager;

        // Remove the old listener to prevent duplicates when importing data
        if (boardSelectionListener != null) {
            boardListView.getSelectionModel().selectedItemProperty().removeListener(boardSelectionListener);
        }
        this.boardSelectionListener = (obs, oldVal, newVal) -> {
            if (newVal != null) {
                noteManager.getBoard(newVal).ifPresent(this::displayBoard);
            }
        };
        boardListView.getSelectionModel().selectedItemProperty().addListener(boardSelectionListener);

        refreshBoardList();
        refreshRecentNotesList();

        // Select the first board by default if it exists
        if (!noteManager.getBoardNames().isEmpty()) {
            boardListView.getSelectionModel().selectFirst();
        } else {
            // If no boards exist (e.g., after a fresh import), clear the view
            currentBoard = null;
            boardTitleLabel.setText("No Boards");
            columnsContainer.getChildren().clear();
        }
    }

    @FXML
    private void initialize() {
        // Create some sample users for assignment
        this.systemUsers = new ArrayList<>(List.of(
            new User("Alex"), new User("Jordan"), new User("Taylor")
        ));

        // Add listener for search field to re-filter the board
        searchField.setOnAction(event -> handleGlobalSearch());

        // Setup preferences button
        preferencesButton.setGraphic(new FontIcon(MaterialDesignC.COG_OUTLINE));
        Tooltip.install(preferencesButton, new Tooltip("Preferences"));

        // Setup archive toggle button
        showArchivedToggle.setGraphic(new FontIcon(MaterialDesignA.ARCHIVE_OUTLINE));
        showArchivedToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (currentBoard != null) {
                displayBoard(currentBoard);
            }
        });

        // Add context menu for deleting boards
        ContextMenu boardContextMenu = new ContextMenu();
        MenuItem deleteBoardItem = new MenuItem("Delete Board");
        deleteBoardItem.setGraphic(new FontIcon(MaterialDesignD.DELETE_OUTLINE));
        deleteBoardItem.setStyle("-fx-text-fill: -color-danger-fg;"); // Make text red for warning
        deleteBoardItem.setOnAction(event -> {
            String selectedBoard = boardListView.getSelectionModel().getSelectedItem();
            if (selectedBoard != null) {
                handleDeleteBoard(selectedBoard);
            }
        });

        MenuItem duplicateBoardItem = new MenuItem("Duplicate Board");
        duplicateBoardItem.setGraphic(new FontIcon(MaterialDesignC.CONTENT_COPY));
        duplicateBoardItem.setOnAction(event -> {
            String selectedBoard = boardListView.getSelectionModel().getSelectedItem();
            if (selectedBoard != null) {
                handleDuplicateBoard(selectedBoard);
            }
        });

        MenuItem importItem = new MenuItem("Import from JSON...");
        importItem.setGraphic(new FontIcon(MaterialDesignF.FILE_IMPORT_OUTLINE));
        importItem.setOnAction(e -> handleImportData());

        MenuItem exportItem = new MenuItem("Export to JSON...");
        exportItem.setGraphic(new FontIcon(MaterialDesignF.FILE_EXPORT_OUTLINE));
        exportItem.setOnAction(e -> handleExportData());

        MenuItem preferencesItem = new MenuItem("Preferences...");
        preferencesItem.setGraphic(new FontIcon(MaterialDesignC.COG_OUTLINE));
        preferencesItem.setOnAction(e -> handleManageUsers());

        boardContextMenu.getItems().addAll(duplicateBoardItem, new SeparatorMenuItem(), deleteBoardItem,
                new SeparatorMenuItem(), importItem, exportItem, new SeparatorMenuItem(), preferencesItem);
        boardListView.setContextMenu(boardContextMenu);

        // --- Recent Notes List Setup ---
        recentNotesListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(NoteManager.NoteBoardPair item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.note.getTitle() + " [" + item.board.getName() + "]");
                }
            }
        });

        recentNotesListView.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                NoteManager.NoteBoardPair selected = recentNotesListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    // Switch to the board if it's not the current one
                    if (currentBoard == null || !currentBoard.getName().equals(selected.board.getName())) {
                        boardListView.getSelectionModel().select(selected.board.getName());
                    }
                    // Open the detail view without a specific card to update
                    showNoteDetailView(selected.note, null);
                }
            }
        });
    }

    @FXML
    private void handleNewBoard() {
        // Create a custom dialog for creating a new board
        Dialog<Pair<String, List<User>>> dialog = new Dialog<>();
        dialog.setTitle("Create New Board");
        dialog.setHeaderText("Enter board details and select members.");

        // Set the button types
        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // Create the content for the dialog
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField boardNameField = new TextField();
        boardNameField.setPromptText("Board Name");

        ListView<User> userListView = new ListView<>();
        userListView.setItems(FXCollections.observableArrayList(systemUsers));
        userListView.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);

        grid.add(new Label("Name:"), 0, 0);
        grid.add(boardNameField, 1, 0);
        grid.add(new Label("Members (Ctrl+Click):"), 0, 1);
        grid.add(userListView, 1, 1);

        dialogPane.setContent(grid);

        // Request focus on the board name field
        Platform.runLater(boardNameField::requestFocus);

        // Disable OK button until a board name is entered
        final Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        boardNameField.textProperty().addListener((observable, oldValue, newValue) -> {
            okButton.setDisable(newValue.trim().isEmpty());
        });

        // Convert the result to a pair of board name and user list when OK is clicked.
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return new Pair<>(boardNameField.getText(), new ArrayList<>(userListView.getSelectionModel().getSelectedItems()));
            }
            return null;
        });

        Optional<Pair<String, List<User>>> result = dialog.showAndWait();

        result.ifPresent(name -> {
            String boardName = name.getKey();
            List<User> selectedUsers = name.getValue();
            if (!boardName.trim().isEmpty()) {
                // createBoard on the NoteManager will mark the state as dirty.
                try {
                    noteManager.createBoard(boardName, selectedUsers);
                    refreshBoardList();
                    boardListView.getSelectionModel().select(boardName);
                } catch (IllegalArgumentException e) {
                    showError("Creation Failed", e.getMessage());
                }
            }
        });
    }

    private void refreshBoardList() {
        boardListView.setItems(FXCollections.observableArrayList(noteManager.getBoardNames()));
    }

    private void refreshRecentNotesList() {
        recentNotesListView.setItems(FXCollections.observableArrayList(noteManager.getRecentNotes()));
    }

    private void displayBoard(Board board) {
        this.currentBoard = board;
        boardTitleLabel.setText(board.getName());
        columnsContainer.getChildren().clear(); // Clear previous board
        noteContainersMap.clear();

        // Determine which columns to show based on the toggle
        List<Note.Status> statusesToDisplay = Arrays.stream(Note.Status.values())
                .filter(s -> s != Note.Status.ARCHIVED || showArchivedToggle.isSelected())
                .collect(Collectors.toList());

        int i = 0;
        for (Note.Status status : statusesToDisplay) {
            VBox column = createColumn(status);
            columnsContainer.getChildren().add(column);

            // --- Add slide-in animation ---
            column.setOpacity(0);
            column.setTranslateY(50); // Start 50px below final position

            FadeTransition ft = new FadeTransition(Duration.millis(400), column);
            ft.setToValue(1);

            TranslateTransition tt = new TranslateTransition(Duration.millis(400), column);
            tt.setToY(0);

            // Stagger the animation
            ft.setDelay(Duration.millis(i * 70));
            tt.setDelay(Duration.millis(i * 70));

            ft.play();
            tt.play();
            i++;
        }
    }

    private VBox createColumn(Note.Status status) {
        // Column Header
        int noteCount = currentBoard.getNotesInColumn(status).size();
        Label titleLabel = new Label(status.toString() + " (" + noteCount + ")");
        titleLabel.setFont(new Font("System Bold", 16));
        titleLabel.setPadding(new Insets(5));

        // VBox for notes
        VBox notesContainer = new VBox(5);
        noteContainersMap.put(status, notesContainer);

        // Populate with all notes for the given status
        currentBoard.getNotesInColumn(status).forEach(note -> {
            VBox noteCard = createNoteCard(note);
            notesContainer.getChildren().add(noteCard);
        });

        // Assemble the column
        VBox columnVBox = new VBox(10, titleLabel, notesContainer);

        // Only add the "Add Note" button for non-archived columns
        if (status != Note.Status.ARCHIVED) {
            Button addNoteButton = new Button("+ Add Note");
            addNoteButton.setMaxWidth(Double.MAX_VALUE);
            addNoteButton.setOnAction(e -> handleNewNote(status));
            columnVBox.getChildren().add(addNoteButton);
        }

        columnVBox.setPadding(new Insets(10));
        columnVBox.setPrefWidth(250);

        // --- Drag and Drop Event Handlers for the Column ---
        columnVBox.setOnDragOver(event -> {
            if (event.getGestureSource() != columnVBox && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        columnVBox.setOnDragEntered(event -> {
            if (event.getGestureSource() != columnVBox && event.getDragboard().hasString()) {
                columnVBox.getStyleClass().add("column-vbox-drag-over");
            }
        });

        columnVBox.setOnDragExited(event -> {
            columnVBox.getStyleClass().remove("column-vbox-drag-over");
        });

        columnVBox.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                UUID noteId = UUID.fromString(db.getString());
                VBox newContainer = noteContainersMap.get(status);
                // Dropping on a column moves the note to the end of that column.
                handleReorderNote(noteId, status, newContainer.getChildren().size());
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        columnVBox.getStyleClass().add("column-vbox");

        return columnVBox;
    }

    /**
     * Updates the note counts in the header of each column.
     * This is more efficient than redrawing the entire board.
     */
    private void updateColumnCounts() {
        for (Note.Status status : Note.Status.values()) {
            VBox notesContainer = noteContainersMap.get(status);
            if (notesContainer != null && notesContainer.getParent() instanceof VBox) {
                VBox columnVBox = (VBox) notesContainer.getParent();
                // The title label is expected to be the first child of the column VBox.
                if (!columnVBox.getChildren().isEmpty() && columnVBox.getChildren().get(0) instanceof Label) {
                    Label titleLabel = (Label) columnVBox.getChildren().get(0);
                    int noteCount = currentBoard.getNotesInColumn(status).size();
                    titleLabel.setText(status.toString() + " (" + noteCount + ")");
                }
            }
        }
    }

    /**
     * Creates the UI representation of a single note (a "card").
     * This method orchestrates the creation of the card's components, styling, and event handlers.
     *
     * @param note The note to represent.
     * @return A VBox containing the note card UI.
     */
    private VBox createNoteCard(Note note) {
        Label title = new Label(note.getTitle());
        title.setWrapText(true);

        HBox detailsBox = buildCardDetails(note);
        FlowPane tagsPane = buildTagsFlowPane(note);

        VBox card = new VBox(5, title, detailsBox, tagsPane);
        card.setPadding(new Insets(10));
        card.setUserData(note); // Associate the note object with the UI card

        setCardStyleClass(card, note);

        setupCardContextMenu(card, note);
        setupCardClickHandling(card, note);
        setupCardDragAndDrop(card, note);

        return card;
    }

    /**
     * Builds the FlowPane containing labels for the note's tags.
     */
    private FlowPane buildTagsFlowPane(Note note) {
        FlowPane tagsPane = new FlowPane(5, 5); // hgap, vgap
        if (note.getTags() != null) {
            for (String tag : note.getTags()) {
                Label tagLabel = new Label(tag);
                tagLabel.getStyleClass().add("tag-label");
                tagsPane.getChildren().add(tagLabel);
            }
        }
        return tagsPane;
    }

    /**
     * Builds the HBox containing detail labels for the note card (e.g., priority, assignees).
     */
    private HBox buildCardDetails(Note note) {
        HBox detailsBox = new HBox(10); // Spacing between detail items

        // Priority
        Label priorityLabel = new Label(note.getPriority().toString());
        priorityLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-fg-muted;");
        Tooltip.install(priorityLabel, new Tooltip("Priority: " + note.getPriority().toString()));
        detailsBox.getChildren().add(priorityLabel);

        // Due Date
        if (note.getDueDate() != null) {
            Label dueDateLabel = new Label("🗓️ " + note.getDueDate().format(DateTimeFormatter.ofPattern("MMM dd")));
            String tooltipText = "Due: " + note.getDueDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a"));
            String style = "-fx-font-size: 10px; -fx-text-fill: -color-fg-muted;";

            if (note.isOverdue()) {
                style = "-fx-font-size: 10px; -fx-text-fill: -color-danger-fg;";
                tooltipText += " (Overdue)";
            }

            dueDateLabel.setStyle(style);
            Tooltip.install(dueDateLabel, new Tooltip(tooltipText));
            detailsBox.getChildren().add(dueDateLabel);
        }

        // Assignees
        if (note.getAssignees() != null && !note.getAssignees().isEmpty()) {
            String assigneeNames = note.getAssignees().stream()
                    .map(User::name)
                    .collect(Collectors.joining(", "));

            Label assigneeLabel = new Label("👤 " + assigneeNames);
            assigneeLabel.setMaxWidth(100); // Prevent the label from becoming too wide
            assigneeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-fg-muted;");
            Tooltip.install(assigneeLabel, new Tooltip("Assigned to: " + assigneeNames));
            detailsBox.getChildren().add(assigneeLabel);
        }
        return detailsBox;
    }

    private void setCardStyleClass(VBox card, Note note) {
        card.getStyleClass().clear();
        card.getStyleClass().add("note-card");

        if (note.isOverdue()) {
            card.getStyleClass().add("note-card-overdue");
        } else {
            switch (note.getPriority()) {
                case LOW -> card.getStyleClass().add("note-card-priority-low");
                case MEDIUM -> card.getStyleClass().add("note-card-priority-medium");
                case HIGH -> card.getStyleClass().add("note-card-priority-high");
                case URGENT -> card.getStyleClass().add("note-card-priority-urgent");
            }
        }
    }

    /**
     * Sets up the context menu (right-click) for a note card.
     */
    private void setupCardContextMenu(VBox card, Note note) {
        ContextMenu contextMenu = new ContextMenu();
        Menu moveMenu = new Menu("Move to");
        moveMenu.setGraphic(new FontIcon(MaterialDesignF.FOLDER_MOVE_OUTLINE));
        for (Note.Status newStatus : Note.Status.values()) {
            if (newStatus != note.getStatus()) {
                MenuItem moveItem = new MenuItem(newStatus.toString());
                moveItem.setOnAction(e -> {
                    VBox newContainer = noteContainersMap.get(newStatus);
                    // Move to the end of the target column
                    handleReorderNote(note.getId(), newStatus, newContainer.getChildren().size());
                });
                moveMenu.getItems().add(moveItem);
            }
        }
        MenuItem duplicateItem = new MenuItem("Duplicate Note");
        duplicateItem.setGraphic(new FontIcon(MaterialDesignC.CONTENT_COPY));
        duplicateItem.setOnAction(e -> handleDuplicateNote(note));
        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setGraphic(new FontIcon(MaterialDesignD.DELETE_OUTLINE));
        deleteItem.setOnAction(e -> handleDeleteNote(note));
        contextMenu.getItems().addAll(duplicateItem, moveMenu, new SeparatorMenuItem(), deleteItem);
        card.setOnContextMenuRequested(e -> contextMenu.show(card, e.getScreenX(), e.getScreenY()));
    }

    /**
     * Sets up the click handler to open the note detail view.
     */
    private void setupCardClickHandling(VBox card, Note note) {
        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                showNoteDetailView(note, card);
            }
        });
    }

    /**
     * Sets up the drag-and-drop functionality for a note card.
     */
    private void setupCardDragAndDrop(VBox card, Note note) {
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.web("#9370DB", 0.7)); // Use accent color
        shadow.setRadius(15);
        shadow.setSpread(0.1);

        card.setOnDragDetected(event -> {
            Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(note.getId().toString());
            db.setContent(content);

            WritableImage snapshot = card.snapshot(new SnapshotParameters(), null);
            db.setDragView(snapshot);

            card.setEffect(shadow);
            event.consume();
        });

        card.setOnDragDone(event -> {
            card.setEffect(null);
            // Also clear any lingering drop indicators
            card.getStyleClass().removeAll("note-card-drop-above", "note-card-drop-below");
            event.consume();
        });

        // --- Drop Target Logic for Reordering ---
        card.setOnDragOver(event -> {
            if (event.getGestureSource() != card && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);
            }
            event.consume();
        });

        card.setOnDragEntered(event -> {
            if (event.getGestureSource() != card && event.getDragboard().hasString()) {
                // Add a visual indicator based on where the cursor is.
                if (event.getY() < card.getHeight() / 2) {
                    card.getStyleClass().add("note-card-drop-above");
                } else {
                    card.getStyleClass().add("note-card-drop-below");
                }
            }
        });

        card.setOnDragExited(event -> {
            // Remove visual indicators when the drag exits the card.
            card.getStyleClass().removeAll("note-card-drop-above", "note-card-drop-below");
        });

        card.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                UUID draggedNoteId = UUID.fromString(db.getString());
                Note targetNote = (Note) card.getUserData();
                VBox container = (VBox) card.getParent();
                int targetIndex = container.getChildren().indexOf(card);

                // Determine the new index based on drop position.
                int newIndex = (event.getY() < card.getHeight() / 2) ? targetIndex : targetIndex + 1;

                handleReorderNote(draggedNoteId, targetNote.getStatus(), newIndex);
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void handleNewNote(Note.Status status) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("New Note");
        dialog.setHeaderText("Create a new note in the " + status + " column");

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField titleField = new TextField();
        titleField.setPromptText("Enter note title");
        dialogPane.setContent(titleField);

        // Disable the OK button until a title is entered
        final Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);
        okButton.setDisable(true);
        titleField.textProperty().addListener((observable, oldValue, newValue) -> {
            okButton.setDisable(newValue.trim().isEmpty());
        });

        // Request focus on the title field for immediate typing
        Platform.runLater(titleField::requestFocus);

        // When the OK button is clicked, return the title.
        dialog.setResultConverter(dialogButton -> dialogButton == ButtonType.OK ? titleField.getText() : null);

        Optional<String> result = dialog.showAndWait();

        result.ifPresent(title -> {
            Note newNote = new Note(title, ""); // Content is empty for new notes
            newNote.setStatus(status);
            currentBoard.addNote(newNote);
            noteManager.markAsDirty();

            // Incrementally update the UI
            noteContainersMap.get(status).getChildren().add(createNoteCard(newNote));
            updateColumnCounts();
        });
    }

    @FXML
    private void handleManageUsers() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tarek/notetool/preferences-view.fxml"));
            // Manually create and set the controller, following the project's established pattern.
            PreferencesViewController controller = new PreferencesViewController();
            loader.setController(controller);
            Parent page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Preferences");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            Scene scene = boardScrollPane.getScene();
            dialogStage.initOwner(scene.getWindow());
            Scene dialogScene = new Scene(page);
            // Apply the user's custom theme to the new dialog's scene
            ThemeManager.loadAndApplyTheme(dialogScene);
            dialogStage.setScene(dialogScene);

            // Now that the page is loaded and the controller is initialized, we can pass data to it.
            controller.setDialogStage(dialogStage);
            controller.setMainScene(scene);
            controller.setUsers(this.systemUsers);
            controller.setTags(noteManager.getAllTags());
            controller.setCurrentUser(noteManager.getCurrentUser());

            dialogStage.showAndWait();

            // Update the main user list with any changes made in the dialog
            this.systemUsers = controller.getUpdatedUsers();
            noteManager.setCurrentUser(controller.getUpdatedCurrentUser());
            noteManager.setAllTags(controller.getUpdatedTags());
            // The setters on NoteManager will mark the state as dirty.
            if (currentBoard != null) {
                displayBoard(currentBoard);
            }
        } catch (Exception e) {
            showError("Failed to open Preferences", "Could not load the preferences view. Error: " + e.getMessage());
            e.printStackTrace(); // For better debugging
        }
    }

    private void handleQuickAdd() {
        Dialog<QuickAddResult> dialog = new Dialog<>();
        dialog.setTitle("Quick Add Note");
        dialog.setHeaderText("Quickly create a new note on any board.");

        DialogPane dialogPane = dialog.getDialogPane();
        dialogPane.getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        // --- Content ---
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20, 150, 10, 10));

        TextField titleField = new TextField();
        titleField.setPromptText("Note Title");

        ComboBox<String> boardComboBox = new ComboBox<>();
        boardComboBox.setItems(FXCollections.observableArrayList(noteManager.getBoardNames()));
        if (currentBoard != null) {
            boardComboBox.setValue(currentBoard.getName());
        } else {
            boardComboBox.getSelectionModel().selectFirst();
        }

        ComboBox<Note.Status> statusComboBox = new ComboBox<>();
        // Filter out ARCHIVED from the list of statuses you can create a note in
        List<Note.Status> creatableStatuses = Arrays.stream(Note.Status.values())
                .filter(s -> s != Note.Status.ARCHIVED)
                .collect(Collectors.toList());
        statusComboBox.setItems(FXCollections.observableArrayList(creatableStatuses));
        statusComboBox.setValue(Note.Status.TODO); // Default to TODO

        grid.add(new Label("Title:"), 0, 0);
        grid.add(titleField, 1, 0);
        grid.add(new Label("Board:"), 0, 1);
        grid.add(boardComboBox, 1, 1);
        grid.add(new Label("Status:"), 0, 2);
        grid.add(statusComboBox, 1, 2);

        dialogPane.setContent(grid);

        // Request focus for immediate typing
        Platform.runLater(titleField::requestFocus);

        // --- Validation ---
        final Button okButton = (Button) dialogPane.lookupButton(ButtonType.OK);
        okButton.setDisable(titleField.getText().trim().isEmpty() || boardComboBox.getValue() == null);

        titleField.textProperty().addListener((obs, oldVal, newVal) -> {
            okButton.setDisable(newVal.trim().isEmpty() || boardComboBox.getValue() == null);
        });
        boardComboBox.valueProperty().addListener((obs, oldVal, newVal) -> {
            okButton.setDisable(newVal == null || titleField.getText().trim().isEmpty());
        });

        // --- Result Converter ---
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return new QuickAddResult(titleField.getText(), boardComboBox.getValue(), statusComboBox.getValue());
            }
            return null;
        });

        // --- Show and process result ---
        dialog.showAndWait().ifPresent(result -> {
            noteManager.getBoard(result.boardName()).ifPresent(board -> {
                Note newNote = new Note(result.title(), "");
                newNote.setStatus(result.status());
                board.addNote(newNote);
                noteManager.markAsDirty();

                // If the note was added to the currently displayed board, update the UI
                if (currentBoard != null && currentBoard.getName().equals(board.getName())) {
                    noteContainersMap.get(result.status()).getChildren().add(createNoteCard(newNote));
                    updateColumnCounts();
                }
            });
        });
    }

    private void handleGlobalSearch() {
        String query = searchField.getText();
        if (query == null || query.trim().isEmpty()) {
            return;
        }

        Map<Note, Board> searchResults = noteManager.searchAllNotes(query);

        if (searchResults.isEmpty()) {
            showInfo("No Results", "No notes found matching your search query '" + query + "'.");
            return;
        }

        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tarek/notetool/search-results-view.fxml"));
            // Manually create and set the controller for robustness in packaged applications.
            SearchResultsViewController controller = new SearchResultsViewController();
            loader.setController(controller);
            Parent page = loader.load();

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Search Results for '" + query + "'");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            Scene ownerScene = boardScrollPane.getScene().getWindow().getScene();
            dialogStage.initOwner(ownerScene.getWindow());
            Scene dialogScene = new Scene(page);
            ThemeManager.loadAndApplyTheme(dialogScene);
            dialogStage.setScene(dialogScene);
            controller.setDialogStage(dialogStage);
            controller.setSearchResults(searchResults);

            dialogStage.showAndWait();

            controller.getSelectedResult().ifPresent(pair -> {
                Board targetBoard = pair.getValue();
                // Navigate to the board. The listener on boardListView will handle the display.
                boardListView.getSelectionModel().select(targetBoard.getName());
            });

        } catch (IOException e) {
            showError("Search Error", "Could not open search results view. Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleDeleteBoard(String boardName) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Board");
        confirmation.setHeaderText("Are you sure you want to delete the board '" + boardName + "'?");
        confirmation.setContentText("This action is permanent and cannot be undone.");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                noteManager.removeBoard(boardName);
                // removeBoard on the NoteManager will mark the state as dirty.

                // Clear the view if the deleted board was the current one
                if (currentBoard != null && currentBoard.getName().equals(boardName)) {
                    currentBoard = null;
                    boardTitleLabel.setText("Select a board");
                    columnsContainer.getChildren().clear();
                    searchField.clear();
                }
                refreshRecentNotesList(); // Update recent notes in case one was on the deleted board
                refreshBoardList();
            }
        });
    }

    private void handleDuplicateBoard(String boardName) {
        try {
            // duplicateBoard on the NoteManager will mark the state as dirty.
            noteManager.duplicateBoard(boardName);
            refreshBoardList();
        } catch (IllegalArgumentException e) {
            showError("Duplication Failed", e.getMessage());
        }
    }

    private void handleDuplicateNote(Note originalNote) {
        Note newNote = originalNote.duplicate(); // Use the dedicated duplicate method
        currentBoard.addNote(newNote);
        noteManager.markAsDirty();

        // Incrementally add the new card to the UI instead of redrawing everything
        VBox container = noteContainersMap.get(newNote.getStatus());
        if (container != null) {
            container.getChildren().add(createNoteCard(newNote));
        }
        updateColumnCounts();
    }

    private void handleReorderNote(UUID draggedNoteId, Note.Status targetStatus, int newIndexInUI) {
        currentBoard.findNoteById(draggedNoteId).ifPresent(noteToMove -> {
            VBox oldContainer = noteContainersMap.get(noteToMove.getStatus());
            VBox newContainer = noteContainersMap.get(targetStatus);

            if (oldContainer != null && newContainer != null) {
                Optional<Node> cardToMoveOpt = oldContainer.getChildren().stream()
                        .filter(nodeUI -> noteToMove.equals(nodeUI.getUserData()))
                        .findFirst();

                cardToMoveOpt.ifPresent(cardNode -> {
                    // The index for the model needs to be adjusted if we move an item
                    // downwards in the same list, because we remove it first.
                    int modelIndex = newIndexInUI;
                    if (oldContainer == newContainer) {
                        int oldIndexInUI = oldContainer.getChildren().indexOf(cardNode);
                        if (oldIndexInUI < newIndexInUI) {
                            modelIndex--;
                        }
                    }

                    // Perform model update first
                    if (currentBoard.moveNote(draggedNoteId, targetStatus, modelIndex)) {
                        // If model update is successful, update the UI
                        oldContainer.getChildren().remove(cardNode);
                        newContainer.getChildren().add(modelIndex, cardNode);
                        updateColumnCounts();
                        noteManager.markAsDirty();
                    }
                });
            }
        });
    }

    private void handleDeleteNote(Note note) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Note");
        confirmation.setHeaderText("Are you sure you want to delete this note?");
        confirmation.setContentText("Note: " + note.getTitle());

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                VBox container = noteContainersMap.get(note.getStatus());
                if (container != null) {
                    Optional<Node> cardToRemove = container.getChildren().stream()
                            .filter(nodeUI -> note.equals(nodeUI.getUserData()))
                            .findFirst();

                    cardToRemove.ifPresent(cardNode -> {
                        if (currentBoard.removeNote(note.getId())) {
                            container.getChildren().remove(cardNode);
                            updateColumnCounts();
                            noteManager.markAsDirty();
                        }
                    });
                }
            }
        });
    }

    private void handleExportData() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Export Data to JSON");
        fileChooser.setInitialFileName("notetool_export.json");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));

        Stage stage = (Stage) boardScrollPane.getScene().getWindow();
        File file = fileChooser.showSaveDialog(stage);

        if (file != null) {
            try {
                noteManager.saveToFile(file.getAbsolutePath());
                showInfo("Export Successful", "All data has been exported to " + file.getName());
            } catch (IOException e) {
                showError("Export Failed", "Could not save data to file. " + e.getMessage());
            }
        }
    }

    private void handleImportData() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Import Data from JSON");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("JSON Files", "*.json"));

        Stage stage = (Stage) boardScrollPane.getScene().getWindow();
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Confirm Import");
            confirmation.setHeaderText("Overwrite all current data?");
            confirmation.setContentText("Importing a new file will replace all current boards, notes, and settings. This action cannot be undone.");

            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    try {
                        NoteManager newManager = NoteManager.loadFromFile(file.getAbsolutePath());
                        // Use the existing setNoteManager method to reload the entire UI
                        setNoteManager(newManager);
                        showInfo("Import Successful", "Data was imported from " + file.getName());
                    } catch (IOException e) {
                        showError("Import Failed", "Could not load data from file. It may be corrupted or in the wrong format.\n\n" + e.getMessage());
                    }
                }
            });
        }
    }

    /**
     * Sets up global keyboard shortcuts for the main scene.
     * This should be called after the scene has been created and shown.
     * @param scene The main application scene.
     */
    public void setupShortcuts(Scene scene) {
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                () -> searchField.requestFocus()
        );
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN),
                this::handleQuickAdd
        );
    }

    private void requestWindowAttention() {
        Stage stage = (Stage) boardScrollPane.getScene().getWindow();
        if (stage != null) {
            stage.requestFocus();
        }
    }

    private void showError(String header, String content) {
        requestWindowAttention();
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showInfo(String header, String content) {
        requestWindowAttention();
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void showNoteDetailView(Note note, VBox noteCard) {
        try {
            // Load the fxml file and create a new stage for the popup dialog.
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tarek/notetool/note-detail-view.fxml"));
            // Manually create and set the controller for robustness in packaged applications.
            NoteDetailViewController controller = new NoteDetailViewController();
            loader.setController(controller);
            Parent page = loader.load();

            // Create the dialog Stage.
            Stage dialogStage = new Stage();
            dialogStage.setTitle("Edit Note");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(boardScrollPane.getScene().getWindow());
            Scene scene = new Scene(page);
            // Apply the user's custom theme to the new dialog's scene
            ThemeManager.loadAndApplyTheme(scene);
            dialogStage.setScene(scene);

            // Set the note into the controller.
            controller.setDialogStage(dialogStage);
            controller.setNote(note);
            controller.setUsers(currentBoard.getMembers());
            controller.setAllTags(noteManager.getAllTags());
            if (noteManager.getCurrentUser() != null) {
                controller.setCurrentUser(noteManager.getCurrentUser()); // Set the current user for commenting
            }

            // Add fade-in animation
            page.setOpacity(0);
            FadeTransition ft = new FadeTransition(Duration.millis(300), page);
            ft.setToValue(1);
            ft.play();

            // Setup shortcuts after the scene is available
            controller.setupShortcuts();

            // Store the note's status before editing to detect if it changed
            Note.Status originalStatus = note.getStatus();

            // Record access before showing
            noteManager.recordNoteAccess(note.getId());

            // Show the dialog and wait until the user closes it
            dialogStage.showAndWait();

            // Refresh recent notes list after closing
            refreshRecentNotesList();

            // Check if the user saved the changes by getting the returned note copy
            controller.getResult().ifPresent(result -> {
                Note savedNoteCopy = result.savedNote();

                // If any new tags were created ad-hoc, add them to the global list
                if (!result.newTags().isEmpty()) {
                    noteManager.getAllTags().addAll(result.newTags());
                }

                // The user saved. Update the original note object with the new data.
                note.updateFrom(savedNoteCopy);
                noteManager.markAsDirty();

                // Now, update the UI to reflect the changes to the original note object.
                if (noteCard != null) {
                    // If the status (column) has changed, move the card. Otherwise, just update it.
                    if (originalStatus != note.getStatus()) {
                        VBox oldContainer = noteContainersMap.get(originalStatus);
                        oldContainer.getChildren().remove(noteCard);
                        VBox newContainer = noteContainersMap.get(note.getStatus());
                        newContainer.getChildren().add(createNoteCard(note)); // Re-create the card in the new column
                    } else {
                        // The card is in the same column, so just replace it in-place.
                        VBox container = (VBox) noteCard.getParent();
                        int cardIndex = container.getChildren().indexOf(noteCard);
                        container.getChildren().set(cardIndex, createNoteCard(note)); // Re-create the card to show updates
                    }
                    updateColumnCounts();
                } else {
                    // If the editor was opened from a context without a specific card (e.g., recent notes), refresh the whole board.
                    displayBoard(currentBoard);
                }
            });
        } catch (IOException e) {
            showError("Failed to open editor", "Could not load the note detail view. Error: " + e.getMessage());
        }
    }
}