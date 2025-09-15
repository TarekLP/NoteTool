package com.tarek.notetool;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.control.DatePicker;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.scene.layout.FlowPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.stream.IntStream;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class NoteDetailViewController {

    /**
     * A record to hold the result of the editor, including the saved note
     * and any newly created tags.
     */
    public record EditorResult(Note savedNote, Set<String> newTags) {
    }

    @FXML
    private TextField titleField;
    @FXML
    private ComboBox<Note.Priority> priorityComboBox;
    @FXML
    private DatePicker dueDatePicker;
    @FXML
    private TextArea contentArea;
    @FXML
    private ProgressBar goalsProgressBar;
    @FXML
    private ListView<Note.Goal> goalsListView;
    @FXML
    private ListView<User> assigneeListView;
    @FXML
    private TextField newGoalField;

    @FXML
    private ListView<Note.Comment> commentsListView;
    @FXML
    private TextField newCommentField;

    @FXML
    private ComboBox<Integer> dueHourComboBox;
    @FXML
    private ComboBox<Integer> dueMinuteComboBox;
    @FXML
    private Label headerLabel;

    @FXML
    private FlowPane tagsFlowPane;

    @FXML
    private ComboBox<String> tagComboBox;

    private Stage dialogStage;
    private Note noteCopy; // The editable copy of the note
    private Note initialNoteState; // A snapshot of the note's state when the editor was opened
    private boolean saved = false;
    private User currentUser;
    private Set<String> tempTags; // A temporary set to stage tag changes

    @FXML
    private void initialize() {
        priorityComboBox.getItems().setAll(Note.Priority.values());

        // Allow adding goals by pressing Enter in the text field
        newGoalField.setOnAction(e -> handleAddGoal());

        // Allow adding comments by pressing Enter in the text field
        newCommentField.setOnAction(e -> handleAddComment());

        // Allow multiple assignees to be selected
        assigneeListView.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);

        // Populate time selectors
        dueHourComboBox.setItems(FXCollections.observableArrayList(IntStream.range(0, 24).boxed().toList()));
        dueMinuteComboBox.setItems(FXCollections.observableArrayList(List.of(0, 15, 30, 45)));

        // --- Goals ListView setup ---
        goalsListView.setCellFactory(lv -> new ListCell<>() {
            private final HBox hbox = new HBox(10);
            private final CheckBox checkBox = new CheckBox();
            private final Button deleteButton = new Button("X");

            {
                // When the checkbox is toggled, update the goal's completed status
                // and refresh the progress bar.
                checkBox.setOnAction(event -> {
                    if (getItem() != null) {
                        getItem().setCompleted(checkBox.isSelected());
                        updateGoalsProgress();
                    }
                });

                deleteButton.setOnAction(event -> {
                    if (getItem() != null) {
                        getListView().getItems().remove(getItem());
                        updateGoalsProgress();
                    }
                });

                HBox.setHgrow(checkBox, Priority.ALWAYS);
                hbox.getChildren().addAll(checkBox, deleteButton);
            }

            @Override
            protected void updateItem(Note.Goal item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    checkBox.setText(item.getDescription());
                    checkBox.setSelected(item.isCompleted());
                    setGraphic(hbox);
                }
            }
        });

        // --- Comments ListView setup ---
        commentsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Note.Comment item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    VBox commentBox = new VBox(3);
                    HBox header = new HBox(10);
                    Label authorLabel = new Label(item.getAuthor().name());
                    authorLabel.setStyle("-fx-font-weight: bold;");

                    java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a");
                    Label timestampLabel = new Label(item.getTimestamp().format(formatter));
                    timestampLabel.setStyle("-fx-font-size: 9px; -fx-text-fill: -color-fg-muted;");

                    header.getChildren().addAll(authorLabel, timestampLabel);
                    Label textLabel = new Label(item.getText());
                    textLabel.setWrapText(true);
                    commentBox.getChildren().addAll(header, textLabel);
                    setGraphic(commentBox);
                }
            }
        });

        // --- Tag ComboBox setup ---
        if (tagComboBox != null) {
            tagComboBox.setOnAction(e -> handleAddTagFromComboBox());
        }
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;

        // Handle window close request (clicking the 'X' button)
        this.dialogStage.setOnCloseRequest(event -> {
            if (saved) {
                return; // Allow closing if saved
            }
            if (isDirty()) {
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("Unsaved Changes");
                alert.setHeaderText("You have unsaved changes.");
                alert.setContentText("Do you want to discard your changes and close the editor?");
                // Using ButtonType.YES and ButtonType.NO for clarity
                alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isEmpty() || result.get() == ButtonType.NO) {
                    event.consume(); // Prevent window from closing
                }
            }
        });
    }

    public void setupShortcuts() {
        if (dialogStage == null) return;
        // Add keyboard shortcuts
        dialogStage.getScene().getAccelerators().put(
            new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
            this::handleSave
        );
        dialogStage.getScene().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                handleCancel();
            }
        });
    }

    public void setUsers(List<User> users) {
        assigneeListView.setItems(FXCollections.observableArrayList(users));
    }

    public void setAllTags(Set<String> allTags) {
        if (tagComboBox != null) {
            tagComboBox.setItems(FXCollections.observableArrayList(allTags));
        }
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
    }

    public void setNote(Note note) {
        // Work on a copy to prevent modifying the original object unless "Save" is clicked.
        this.noteCopy = new Note(note);
        this.initialNoteState = new Note(noteCopy); // Create a snapshot for change detection

        headerLabel.setText("Edit: " + noteCopy.getTitle());
        titleField.setText(noteCopy.getTitle());
        contentArea.setText(noteCopy.getContent());
        priorityComboBox.setValue(noteCopy.getPriority());
        if (noteCopy.getDueDate() != null) {
            dueDatePicker.setValue(noteCopy.getDueDate().toLocalDate());
            dueHourComboBox.setValue(noteCopy.getDueDate().getHour());
            dueMinuteComboBox.setValue(noteCopy.getDueDate().getMinute());
        } else {
            dueHourComboBox.setValue(null);
            dueMinuteComboBox.setValue(null);
        }
        // Select the current assignees in the list
        assigneeListView.getSelectionModel().clearSelection();
        for (User assignee : noteCopy.getAssignees()) {
            assigneeListView.getSelectionModel().select(assignee);
        }

        // Populate goals
        goalsListView.setItems(FXCollections.observableArrayList(noteCopy.getGoals()));
        commentsListView.setItems(FXCollections.observableArrayList(noteCopy.getComments()));
        updateGoalsProgress();

        // Populate tags
        this.tempTags = new HashSet<>(noteCopy.getTags());
        refreshTagsPane(); // This will now safely do nothing if the pane is null

        // Auto-focus the title field
        Platform.runLater(titleField::requestFocus);
    }

    /**
     * Returns the edited note copy if the user saved, otherwise returns an empty Optional.
     * @return An Optional containing the saved note.
     */
    public Optional<EditorResult> getResult() {
        return saved ? Optional.of(new EditorResult(noteCopy, findNewTags())) : Optional.empty();
    }

    @FXML
    private void handleSave() {
        // Update the note copy with data from the form
        noteCopy.setTitle(titleField.getText());
        noteCopy.setContent(contentArea.getText());
        noteCopy.setPriority(priorityComboBox.getValue());
        if (dueDatePicker.getValue() != null) {
            int hour = dueHourComboBox.getValue() != null ? dueHourComboBox.getValue() : 0;
            int minute = dueMinuteComboBox.getValue() != null ? dueMinuteComboBox.getValue() : 0;
            noteCopy.setDueDate(dueDatePicker.getValue().atTime(hour, minute));
        } else {
            noteCopy.setDueDate(null);
        }
        noteCopy.setAssignees(assigneeListView.getSelectionModel().getSelectedItems());

        // Persist the goals list back to the note object
        noteCopy.setGoals(goalsListView.getItems());
        noteCopy.setComments(commentsListView.getItems());
        noteCopy.setTags(this.tempTags);

        saved = true;
        dialogStage.close();
    }

    @FXML
    private void handleCancel() {
        // The OnCloseRequest handler will catch this and ask for confirmation if needed.
        dialogStage.close();
    }

    @FXML
    private void handleAddGoal() {
        String description = newGoalField.getText();
        if (description != null && !description.trim().isEmpty()) {
            Note.Goal newGoal = new Note.Goal(description);
            goalsListView.getItems().add(newGoal);
            newGoalField.clear();
            updateGoalsProgress();
        }
    }

    @FXML
    private void handleClearDueDate() {
        dueDatePicker.setValue(null);
        dueHourComboBox.setValue(null);
        dueMinuteComboBox.setValue(null);
    }

    @FXML
    private void handleAddComment() {
        String text = newCommentField.getText();
        if (text != null && !text.trim().isEmpty() && currentUser != null) {
            Note.Comment newComment = new Note.Comment(text, currentUser);
            commentsListView.getItems().add(newComment);
            newCommentField.clear();
        }
    }

    @FXML
    private void handleAddTagFromComboBox() {
        String newTag = tagComboBox.getEditor().getText();
        if (newTag != null && !newTag.trim().isEmpty()) {
            String lowerCaseTag = newTag.toLowerCase().trim();

            // Add to the temporary set for this editing session
            tempTags.add(lowerCaseTag);

            // Refresh the UI to show the new tag
            refreshTagsPane();

            tagComboBox.getEditor().clear();
        }
    }

    private void refreshTagsPane() {
        if (tagsFlowPane == null) {
            return; // Do nothing if the FXML component doesn't exist.
        }
        tagsFlowPane.getChildren().clear();
        for (String tag : tempTags) { // tempTags is guaranteed to be initialized.
            tagsFlowPane.getChildren().add(createTagLabel(tag)); // tempTags is guaranteed to be initialized.
        }
    }

    private Node createTagLabel(String tag) {
        Label label = new Label(tag);
        Button removeButton = new Button("x");
        removeButton.getStyleClass().add("tag-remove-button");

        HBox tagView = new HBox(5, label, removeButton);
        tagView.getStyleClass().add("tag-view");
        removeButton.setOnAction(e -> {
            tempTags.remove(tag);
            refreshTagsPane();
        });
        return tagView;
    }

    private Set<String> findNewTags() {
        Set<String> newTags = new HashSet<>();
        if (tagComboBox != null) {
            Set<String> existingTags = new HashSet<>(tagComboBox.getItems());
            for (String tag : this.tempTags) {
                if (!existingTags.contains(tag)) {
                    newTags.add(tag);
                }
            }
        }
        return newTags;
    }

    /**
     * Checks if the note has been modified by comparing the current UI state
     * to the initial state when the editor was opened.
     * @return true if there are unsaved changes, false otherwise.
     */
    private boolean isDirty() {
        // Compare current UI state with the initial state of the note.
        if (!Objects.equals(titleField.getText(), initialNoteState.getTitle())) return true;
        if (!Objects.equals(contentArea.getText(), initialNoteState.getContent())) return true;
        if (!Objects.equals(priorityComboBox.getValue(), initialNoteState.getPriority())) return true;

        // Compare due date
        java.time.LocalDateTime currentDueDate = null;
        if (dueDatePicker.getValue() != null) {
            int hour = dueHourComboBox.getValue() != null ? dueHourComboBox.getValue() : 0;
            int minute = dueMinuteComboBox.getValue() != null ? dueMinuteComboBox.getValue() : 0;
            currentDueDate = dueDatePicker.getValue().atTime(hour, minute);
        }
        if (!Objects.equals(currentDueDate, initialNoteState.getDueDate())) return true;

        // Compare assignees (order doesn't matter)
        Set<User> currentAssignees = new HashSet<>(assigneeListView.getSelectionModel().getSelectedItems());
        Set<User> initialAssignees = new HashSet<>(initialNoteState.getAssignees());
        if (!currentAssignees.equals(initialAssignees)) return true;

        // Compare goals (order and content matter)
        List<Note.Goal> currentGoals = goalsListView.getItems();
        List<Note.Goal> initialGoals = initialNoteState.getGoals();
        if (currentGoals.size() != initialGoals.size()) return true;
        for (int i = 0; i < currentGoals.size(); i++) {
            Note.Goal currentGoal = currentGoals.get(i);
            Note.Goal initialGoal = initialGoals.get(i);
            if (!Objects.equals(currentGoal.getDescription(), initialGoal.getDescription()) ||
                currentGoal.isCompleted() != initialGoal.isCompleted()) {
                return true;
            }
        }

        return !tempTags.equals(initialNoteState.getTags());
    }

    private void updateGoalsProgress() {
        long totalGoals = goalsListView.getItems().size();
        if (totalGoals == 0) {
            goalsProgressBar.setProgress(0.0);
            return;
        }

        long completedGoals = goalsListView.getItems().stream()
                .filter(Note.Goal::isCompleted)
                .count();

        double progress = (double) completedGoals / totalGoals;
        goalsProgressBar.setProgress(progress);
    }
}