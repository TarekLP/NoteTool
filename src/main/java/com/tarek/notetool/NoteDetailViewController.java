package com.tarek.notetool;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
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
import java.util.stream.Collectors;
import java.util.ArrayList;
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
    private VBox goalsContainer; // Placeholder for the TreeView

    // The TreeView is now created programmatically to avoid FXML injection issues.
    private TreeView<Note.Goal> goalsTreeView;
    @FXML
    private ListView<User> assigneeListView;
    @FXML
    private TextField newGoalField;
    @FXML
    private Button clearCompletedGoalsButton;

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

        // Wire up the button to clear completed goals.
        if (clearCompletedGoalsButton != null) {
            clearCompletedGoalsButton.setOnAction(e -> handleClearCompletedGoals());
            Tooltip.install(clearCompletedGoalsButton, new Tooltip("Remove all completed goals from the list"));
        }

        // Allow adding comments by pressing Enter in the text field
        newCommentField.setOnAction(e -> handleAddComment());

        // Allow multiple assignees to be selected
        assigneeListView.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);

        // Populate time selectors
        dueHourComboBox.setItems(FXCollections.observableArrayList(IntStream.range(0, 24).boxed().toList()));
        dueMinuteComboBox.setItems(FXCollections.observableArrayList(
                IntStream.iterate(0, i -> i < 60, i -> i + 5).boxed().toList()
        ));

        // --- Goals TreeView setup ---
        // The TreeView is now created programmatically to ensure it's never null,
        // bypassing potential FXML loading or build caching issues.
        goalsTreeView = new TreeView<>();
        goalsTreeView.setEditable(true);
        goalsTreeView.setCellFactory(tv -> new GoalTreeCell());
        goalsTreeView.setShowRoot(false);
        VBox.setVgrow(goalsTreeView, Priority.ALWAYS);
        goalsContainer.getChildren().add(goalsTreeView);

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
        TreeItem<Note.Goal> root = new TreeItem<>(); // Dummy root
        noteCopy.getGoals().forEach(goal -> root.getChildren().add(createGoalTreeItem(goal)));
        goalsTreeView.setRoot(root);
        goalsTreeView.getRoot().setExpanded(true);

        commentsListView.setItems(FXCollections.observableArrayList(noteCopy.getComments()));
        updateGoalsProgress();

        // Populate tags
        this.tempTags = new HashSet<>(noteCopy.getTags());
        refreshTagsPane(); // This will now safely do nothing if the pane is null

        // Auto-focus the title field
        Platform.runLater(titleField::requestFocus);
    }

    /**
     * Recursively builds a TreeItem structure from a given Goal object and its sub-goals.
     */
    private TreeItem<Note.Goal> createGoalTreeItem(Note.Goal goal) {
        TreeItem<Note.Goal> item = new TreeItem<>(goal);
        if (goal.getSubGoals() != null) {
            goal.getSubGoals().forEach(subGoal -> item.getChildren().add(createGoalTreeItem(subGoal)));
        }
        return item;
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
        List<Note.Goal> topLevelGoals = goalsTreeView.getRoot().getChildren().stream()
                .map(TreeItem::getValue)
                .collect(Collectors.toList());
        noteCopy.setGoals(topLevelGoals);

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
            goalsTreeView.getRoot().getChildren().add(new TreeItem<>(newGoal));
            newGoalField.clear();
            updateGoalsProgress();
        }
    }

    /**
     * Handles the action of clearing all completed goals from the list.
     * This is triggered by the "Clear Completed" button, which is assumed to be
     * added in the FXML layout near the goals list.
     */
    @FXML
    private void handleClearCompletedGoals() {
        if (goalsTreeView.getRoot() != null) {
            boolean wasChanged = removeCompletedGoals(goalsTreeView.getRoot());
            if (wasChanged) {
                updateGoalsProgress();
            }
        }
    }

    /**
     * Recursively traverses a TreeItem and removes any children that are marked as completed.
     * @param parent The parent TreeItem to clean.
     * @return true if any goals were removed, false otherwise.
     */
    private boolean removeCompletedGoals(TreeItem<Note.Goal> parent) {
        if (parent == null || parent.getChildren().isEmpty()) {
            return false;
        }

        // A list of children to remove from the parent
        List<TreeItem<Note.Goal>> childrenToRemove = new ArrayList<>();
        boolean wasChanged = false;

        for (TreeItem<Note.Goal> child : parent.getChildren()) {
            // Recursively clean the children of the child first.
            if (removeCompletedGoals(child)) {
                wasChanged = true;
            }
            // Now, check if the child itself is completed.
            if (child.getValue().isCompleted()) {
                childrenToRemove.add(child);
            }
        }
        if (!childrenToRemove.isEmpty()) {
            parent.getChildren().removeAll(childrenToRemove);
            wasChanged = true;
        }
        return wasChanged;
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
        List<Note.Goal> currentTopLevelGoals = new ArrayList<>();
        currentTopLevelGoals = goalsTreeView.getRoot().getChildren().stream()
                .map(TreeItem::getValue).collect(Collectors.toList());
        if (areGoalListsDifferent(currentTopLevelGoals, initialNoteState.getGoals())) return true;

        return !tempTags.equals(initialNoteState.getTags());
    }

    /**
     * Recursively compares two lists of goals for any differences.
     */
    private boolean areGoalListsDifferent(List<Note.Goal> list1, List<Note.Goal> list2) {
        if (list1.size() != list2.size()) return true;
        for (int i = 0; i < list1.size(); i++) {
            Note.Goal goal1 = list1.get(i);
            Note.Goal goal2 = list2.get(i);
            if (!Objects.equals(goal1.getDescription(), goal2.getDescription())) return true;
            if (goal1.isCompleted() != goal2.isCompleted()) return true;
            // Recursive call for sub-goals
            if (areGoalListsDifferent(goal1.getSubGoals(), goal2.getSubGoals())) return true;
        }
        return false;
    }

    private void updateGoalsProgress() {
        if (goalsTreeView.getRoot() == null) {
            goalsProgressBar.setProgress(0.0);
            return;
        }
        List<Note.Goal> allGoals = new ArrayList<>();
        collectAllGoals(goalsTreeView.getRoot(), allGoals);

        long totalGoals = allGoals.size();
        if (totalGoals == 0) {
            goalsProgressBar.setProgress(0.0);
            return;
        }

        long completedGoals = allGoals.stream()
                .filter(Note.Goal::isCompleted)
                .count();

        double progress = (double) completedGoals / totalGoals;
        goalsProgressBar.setProgress(progress);
    }

    /**
     * Recursively traverses a TreeItem and collects all Goal objects into a flat list.
     */
    private void collectAllGoals(TreeItem<Note.Goal> item, List<Note.Goal> allGoals) {
        // Don't add the root's value if it's a dummy item
        if (item.getValue() != null) {
            allGoals.add(item.getValue());
        }
        for (TreeItem<Note.Goal> child : item.getChildren()) {
            collectAllGoals(child, allGoals);
        }
    }

    /**
     * Recursively sets the completion status of all sub-goals of a given goal.
     * @param parentItem The parent TreeItem.
     * @param completed The completion status to set.
     */
    private void setSubGoalsCompletion(TreeItem<Note.Goal> parentItem, boolean completed) {
        if (parentItem == null) {
            return;
        }
        // Recurse for all children
        for (TreeItem<Note.Goal> childItem : parentItem.getChildren()) {
            // Set the completion for the child's goal
            if (childItem.getValue() != null && childItem.getValue().isCompleted() != completed) {
                childItem.getValue().setCompleted(completed);
            }
            // Recurse for grandchildren
            setSubGoalsCompletion(childItem, completed);
        }
    }

    /**
     * Recursively checks and updates the completion status of parent goals.
     * If all sub-goals of a parent are complete, the parent is marked as complete.
     * If any sub-goal of a parent becomes incomplete, the parent is marked as incomplete.
     * @param item The TreeItem whose parent's status needs to be checked.
     */
    private void updateParentCompletion(TreeItem<Note.Goal> item) {
        if (item == null) {
            return;
        }

        TreeItem<Note.Goal> parentItem = item.getParent();

        // Stop if we've reached the (hidden) root or there's no parent
        if (parentItem == null || parentItem.getValue() == null) {
            return;
        }

        Note.Goal parentGoal = parentItem.getValue();

        // Check if all children of the parent are completed by looking at the model
        boolean allChildrenCompleted = parentGoal.getSubGoals().stream()
                .allMatch(Note.Goal::isCompleted);

        // If the parent's completion status needs to change, update it and recurse upwards
        if (parentGoal.isCompleted() != allChildrenCompleted) {
            parentGoal.setCompleted(allChildrenCompleted);
            // Recurse to check the grandparent
            updateParentCompletion(parentItem);
        }
    }

    /**
     * An inner class to define the look and behavior of each cell in the goals TreeView.
     */
    private class GoalTreeCell extends TreeCell<Note.Goal> {
        private final HBox hbox = new HBox(5);
        private final CheckBox checkBox = new CheckBox();
        private final Button addSubGoalButton = new Button("+");
        private final Button deleteButton = new Button("x");
        private TextField editField;

        public GoalTreeCell() {
            Tooltip.install(addSubGoalButton, new Tooltip("Add Sub-goal"));
            Tooltip.install(deleteButton, new Tooltip("Delete Goal"));
            addSubGoalButton.getStyleClass().add("rich-text-editor-button");
            deleteButton.getStyleClass().add("rich-text-editor-button");

            checkBox.setOnAction(event -> {
                if (getItem() != null) {
                    boolean isSelected = checkBox.isSelected();
                    getItem().setCompleted(isSelected);

                    // Propagate completion status downwards to all sub-goals
                    setSubGoalsCompletion(getTreeItem(), isSelected);

                    // Propagate completion status upwards to parent goals
                    updateParentCompletion(getTreeItem());

                    goalsTreeView.refresh();
                    updateGoalsProgress();
                }
            });

            deleteButton.setOnAction(event -> {
                TreeItem<Note.Goal> treeItem = getTreeItem();
                if (treeItem != null && treeItem.getParent() != null) {
                    TreeItem<Note.Goal> parentTreeItem = treeItem.getParent();
                    // If the parent is not the invisible root, remove the goal from the parent goal's list of sub-goals
                    if (parentTreeItem.getValue() != null) {
                        parentTreeItem.getValue().removeSubGoal(getItem());
                    }
                    // Remove the item from the TreeView
                    parentTreeItem.getChildren().remove(treeItem);

                    // After removing, check if the parent should now be marked as complete
                    updateParentCompletion(parentTreeItem);
                    goalsTreeView.refresh();

                    updateGoalsProgress();
                }
            });

            addSubGoalButton.setOnAction(event -> {
                if (getTreeItem() != null) {
                    Note.Goal parentGoal = getItem();

                    // If parent was completed, un-complete it since we're adding a new task
                    if (parentGoal.isCompleted()) {
                        parentGoal.setCompleted(false);
                        // Also need to update grandparents
                        updateParentCompletion(getTreeItem());
                    }

                    Note.Goal newSubGoal = new Note.Goal("New Sub-goal");
                    parentGoal.addSubGoal(newSubGoal); // Update model

                    TreeItem<Note.Goal> newTreeItem = new TreeItem<>(newSubGoal); // Create UI item
                    getTreeItem().getChildren().add(newTreeItem); // Add to UI tree
                    getTreeItem().setExpanded(true);

                    goalsTreeView.refresh();
                    updateGoalsProgress();
                }
            });

            HBox.setHgrow(checkBox, Priority.ALWAYS);
            hbox.getChildren().addAll(checkBox, addSubGoalButton, deleteButton);
        }

        @Override
        public void startEdit() {
            super.startEdit();
            if (getItem() == null) {
                return;
            }
            if (editField == null) {
                createTextField();
            }
            editField.setText(getItem().getDescription());
            setGraphic(editField);
            setText(null);
            editField.selectAll();
            editField.requestFocus();
        }

        @Override
        public void cancelEdit() {
            super.cancelEdit();
            setGraphic(hbox);
            setText(null);
        }

        @Override
        protected void updateItem(Note.Goal item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setGraphic(null);
                setText(null);
            } else {
                if (isEditing()) {
                    if (editField != null) {
                        editField.setText(item.getDescription());
                    }
                    setGraphic(editField);
                    setText(null);
                } else {
                    checkBox.setText(item.getDescription());
                    checkBox.setSelected(item.isCompleted());
                    setGraphic(hbox);
                    setText(null);
                }
            }
        }

        private void createTextField() {
            editField = new TextField();
            editField.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    getItem().setDescription(editField.getText());
                    commitEdit(getItem());
                } else if (event.getCode() == KeyCode.ESCAPE) {
                    cancelEdit();
                }
            });
            editField.focusedProperty().addListener((obs, wasFocused, isNowFocused) -> {
                if (wasFocused && !isNowFocused) {
                    getItem().setDescription(editField.getText());
                    commitEdit(getItem());
                }
            });
        }
    }
}