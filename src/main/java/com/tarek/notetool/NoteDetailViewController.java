package com.tarek.notetool;

import javafx.application.Platform;
import javafx.animation.PauseTransition;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Tab;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.ListCell;
import javafx.scene.control.SplitPane;
import javafx.scene.control.ListView;
import javafx.scene.control.TabPane;
import javafx.scene.layout.HBox;
import javafx.geometry.Insets;
import javafx.scene.image.ImageView;
import javafx.geometry.Pos;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.control.DatePicker;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;
import javafx.scene.layout.FlowPane;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.geometry.Side;
import javafx.scene.layout.VBox;
import javafx.scene.image.Image;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.util.Pair;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignP;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.IntStream;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.UUID;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
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
    private TextArea contentArea;
    @FXML
    private WebView contentPreview;
    @FXML
    private ComboBox<Note.Priority> priorityComboBox;
    @FXML
    private DatePicker dueDatePicker;
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

    // --- NEW FXML Fields ---
    @FXML
    private ListView<String> attachmentsListView;
    @FXML
    private Button addAttachmentButton;

    @FXML
    private ListView<Note.Dependency> dependenciesListView;
    @FXML
    private Button addDependencyButton;

    @FXML
    private FlowPane referenceImagesFlowPane;

    @FXML
    private Button copyLinkButton;

    private Stage dialogStage;
    private Note noteCopy; // The editable copy of the note
    private Note initialNoteState; // A snapshot of the note's state when the editor was opened
    private boolean saved = false;
    private User currentUser;
    private NoteManager noteManager; // To search for notes when adding dependencies
    private Set<String> tempTags; // A temporary set to stage tag changes

    // Temporary lists to stage changes for new features
    private List<String> tempAttachmentPaths;
    private List<Note.Dependency> tempDependencies;
    private List<String> tempReferenceImagePaths;
    
    private ContextMenu noteSuggestionsPopup;
    private ContextMenu imageSuggestionsPopup;
    private UUID noteToOpen = null;
    private final Parser markdownParser;
    private final HtmlRenderer markdownRenderer;
    private PauseTransition markdownRenderDebounce;

    public NoteDetailViewController() {
        MutableDataSet options = new MutableDataSet();
        // Enable GitHub Flavored Markdown extensions for task lists and strikethrough
        options.set(Parser.EXTENSIONS, Arrays.asList(
                TaskListExtension.create(),
                StrikethroughExtension.create()
        ));
        this.markdownParser = Parser.builder(options).build();
        this.markdownRenderer = HtmlRenderer.builder(options).build();
    }

    @FXML
    private void initialize() {
        priorityComboBox.getItems().setAll(Note.Priority.values());

        // --- Live Markdown Preview Setup ---
        // Create a SplitPane to hold the editor and preview side-by-side.
        SplitPane editorSplitPane = new SplitPane(contentArea, contentPreview);
        editorSplitPane.setDividerPositions(0.5); // Start with a 50/50 split

        // Find the TabPane that contains the contentArea and replace it entirely
        // with the new SplitPane. This removes the old "Edit" and "Preview" tabs.
        Platform.runLater(() -> {
            Node current = contentArea;
            while (current != null && !(current instanceof TabPane)) {
                current = current.getParent();
            }
            if (current instanceof TabPane tabPane) {
                if (tabPane.getParent() instanceof Pane parentPane) {
                    int index = parentPane.getChildren().indexOf(tabPane);
                    if (index != -1) {
                        parentPane.getChildren().set(index, editorSplitPane);
                    }
                }
            }
        });

        // Listen for input in the goal field to show suggestions or add a new goal.
        noteSuggestionsPopup = new ContextMenu();
        newGoalField.textProperty().addListener((obs, oldVal, newVal) -> handleGoalInput(newVal));

        // Listen for '@' in the main content area to suggest images.
        imageSuggestionsPopup = new ContextMenu();
        contentArea.textProperty().addListener((obs, oldVal, newVal) -> handleContentInput(newVal));
        newGoalField.setOnAction(e -> handleAddGoal()); // For pressing enter on plain text


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

        // --- NEW: Attachments Setup ---
        if (addAttachmentButton != null) {
            addAttachmentButton.setOnAction(e -> handleAddAttachment());
        }
        if (attachmentsListView != null) {
            setupAttachmentsListView();
        }

        // --- NEW: Dependencies Setup ---
        if (addDependencyButton != null) {
            addDependencyButton.setOnAction(e -> handleAddDependency());
        }
        if (dependenciesListView != null) {
            setupDependenciesListView();
        }

        // --- NEW: Copy Link Button Setup ---
        if (copyLinkButton != null) {
            copyLinkButton.setGraphic(new FontIcon(MaterialDesignL.LINK));
            Tooltip.install(copyLinkButton, new Tooltip("Copy Link to Note"));
            copyLinkButton.setOnAction(e -> handleCopyLink());
        }

        // --- NEW: Reference Images Setup ---
        if (referenceImagesFlowPane != null) {
            setupReferencePaneDragAndDrop();
        }

        // --- Debounced Markdown Rendering ---
        // This PauseTransition ensures we don't re-render the markdown on every single keystroke,
        // which would be inefficient. Instead, it waits for a brief pause in typing.
        markdownRenderDebounce = new PauseTransition(Duration.millis(300));
        markdownRenderDebounce.setOnFinished(event -> renderMarkdown());

        contentArea.textProperty().addListener((obs, oldVal, newVal) -> {
            // Restart the debounce timer on every text change.
            markdownRenderDebounce.playFromStart();
        });
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

    public void setNoteManager(NoteManager noteManager) {
        this.noteManager = noteManager;
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

        // Populate attachments
        this.tempAttachmentPaths = new ArrayList<>(noteCopy.getAttachmentPaths());
        if (attachmentsListView != null) {
            attachmentsListView.setItems(FXCollections.observableArrayList(this.tempAttachmentPaths));
        }

        // Populate dependencies
        this.tempDependencies = new ArrayList<>(noteCopy.getDependencies());
        if (dependenciesListView != null) {
            dependenciesListView.setItems(FXCollections.observableArrayList(this.tempDependencies));
        }

        // Populate reference images
        this.tempReferenceImagePaths = new ArrayList<>(noteCopy.getReferenceImagePaths());
        if (referenceImagesFlowPane != null) {
            refreshReferenceImagesPane();
        }

        // Perform an initial render of the markdown content.
        renderMarkdown();
        // Auto-focus the title field
        Platform.runLater(titleField::requestFocus);
    }

    public Optional<UUID> getNoteToOpen() {
        return Optional.ofNullable(noteToOpen);
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
        if (titleField.getText() != null) {
            noteCopy.setTitle(titleField.getText());
        }
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

        // Save attachments and dependencies
        noteCopy.setAttachmentPaths(this.tempAttachmentPaths);
        noteCopy.setDependencies(this.tempDependencies);
        noteCopy.setReferenceImagePaths(this.tempReferenceImagePaths);

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
        TreeItem<Note.Goal> root = goalsTreeView.getRoot();
        if (root == null) return;

        boolean wasChanged = removeCompletedGoals(root);
        if (wasChanged) updateGoalsProgress();
    }

    /**
     * Recursively traverses a TreeItem and removes any children that are marked as completed.
     * @param parent The parent TreeItem to clean.
     * @return true if any goals were removed, false otherwise.
     */
    private boolean removeCompletedGoals(TreeItem<Note.Goal> parent) {
        // --- FIX: Update the underlying data model ---
        Note.Goal parentGoal = parent.getValue();

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

        if (parentGoal != null && parentGoal.removeCompletedSubGoals()) {
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

    private void renderMarkdown() {
        if (contentArea == null || contentPreview == null) {
            return;
        }
        String markdownText = contentArea.getText();
        com.vladsch.flexmark.util.ast.Node document = markdownParser.parse(markdownText);
        String rawHtml = markdownRenderer.render(document);

        // This HTML uses CSS variables defined by the AtlantaFX theme, so it will adapt to light/dark mode.
        // --- NEW: Process custom 'gallery://' links ---
        // Replace our custom protocol with a valid file URI that the WebView can understand.
        String galleryUri = MainApp.getGalleryDirectory().toUri().toString();
        String processedHtml = rawHtml.replaceAll("src=\"gallery://([^\"]+)\"", "src=\"" + galleryUri + "$1\"");

        // Get the path to the external CSS file.
        String markdownCssPath = Objects.requireNonNull(getClass().getResource("/com/tarek/notetool/markdown-preview.css")).toExternalForm();

        String fullHtml = """
            <html>
                <head>
                    <link rel="stylesheet" href="%s">
                </head>
                <body>
                    %s
                    <script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
                    <script>
                        // Find all <pre><code> blocks and apply syntax highlighting
                        document.addEventListener('DOMContentLoaded', (event) => {
                            document.querySelectorAll('pre code').forEach((el) => { hljs.highlightElement(el); });
                        });
                    </script>
                </body>
            </html>
        """.formatted(markdownCssPath, processedHtml);

        contentPreview.getEngine().loadContent(fullHtml);
    }

    // --- Reference Image Methods ---

    private void setupReferencePaneDragAndDrop() {
        referenceImagesFlowPane.setOnDragOver(event -> {
            // Accept the drag if it has our custom data format
            if (event.getGestureSource() != referenceImagesFlowPane && event.getDragboard().hasContent(ImageGalleryViewController.GALLERY_IMAGE_DATA_FORMAT)) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });

        referenceImagesFlowPane.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasContent(ImageGalleryViewController.GALLERY_IMAGE_DATA_FORMAT)) {
                String imageFileName = (String) db.getContent(ImageGalleryViewController.GALLERY_IMAGE_DATA_FORMAT);
                if (!tempReferenceImagePaths.contains(imageFileName)) {
                    tempReferenceImagePaths.add(imageFileName);
                    refreshReferenceImagesPane();
                }
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void refreshReferenceImagesPane() {
        referenceImagesFlowPane.getChildren().clear();
        for (String imageFileName : tempReferenceImagePaths) {
            referenceImagesFlowPane.getChildren().add(createReferenceImageView(imageFileName));
        }
    }

    private Node createReferenceImageView(String imageFileName) {
        Path imagePath = MainApp.getGalleryDirectory().resolve(imageFileName);
        Image image = new Image(imagePath.toUri().toString(), 80, 80, true, true);
        ImageView imageView = new ImageView(image);

        Button removeButton = new Button("x");
        removeButton.getStyleClass().add("tag-remove-button");
        removeButton.setOnAction(e -> {
            tempReferenceImagePaths.remove(imageFileName);
            refreshReferenceImagesPane();
        });
        return new VBox(5, imageView, removeButton);
    }

    // --- Attachment Methods ---

    private void setupAttachmentsListView() {
        attachmentsListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // The stored name is unique; we extract the original name for display.
                    String originalFileName = item.substring(item.indexOf('-') + 1);
                    setText(originalFileName);
                    setGraphic(new FontIcon(MaterialDesignP.PAPERCLIP));
                }
            }
        });

        ContextMenu contextMenu = new ContextMenu();
        MenuItem openItem = new MenuItem("Open File");
        openItem.setOnAction(e -> {
            String selectedPath = attachmentsListView.getSelectionModel().getSelectedItem();
            if (selectedPath != null) {
                try {
                    File fileToOpen = MainApp.getAttachmentsDirectory().resolve(selectedPath).toFile();
                    if (fileToOpen.exists()) {
                        Desktop.getDesktop().open(fileToOpen);
                    } else {
                        showError("File Not Found", "The attached file could not be found at its expected location.");
                    }
                } catch (IOException ex) {
                    showError("Could Not Open File", "An error occurred while trying to open the file: " + ex.getMessage());
                }
            }
        });

        MenuItem removeItem = new MenuItem("Remove Attachment");
        removeItem.setOnAction(e -> {
            String selectedPath = attachmentsListView.getSelectionModel().getSelectedItem();
            if (selectedPath != null) {
                tempAttachmentPaths.remove(selectedPath);
                attachmentsListView.getItems().remove(selectedPath);
                // Note: We are not deleting the file from disk, just the reference.
            }
        });

        contextMenu.getItems().addAll(openItem, new SeparatorMenuItem(), removeItem);
        attachmentsListView.setContextMenu(contextMenu);
    }

    private void handleAddAttachment() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Attach File");
        File selectedFile = fileChooser.showOpenDialog(dialogStage);

        if (selectedFile != null) {
            try {
                Path attachmentsDir = MainApp.getAttachmentsDirectory();
                // Create a unique filename to prevent collisions, but keep the original name for context
                String uniqueFileName = UUID.randomUUID().toString().substring(0, 8) + "-" + selectedFile.getName();
                Path targetPath = attachmentsDir.resolve(uniqueFileName);

                Files.copy(selectedFile.toPath(), targetPath, StandardCopyOption.REPLACE_EXISTING);

                tempAttachmentPaths.add(uniqueFileName);
                attachmentsListView.getItems().add(uniqueFileName);

            } catch (IOException e) {
                showError("Attachment Failed", "Could not attach the file. Error: " + e.getMessage());
            }
        }
    }

    /**
     * Handles text input in the main content area to provide image suggestions.
     * When the user types '@' followed by text, it shows a popup with matching
     * images from the gallery.
     */
    private void handleContentInput(String text) {
        if (text == null || noteManager == null) {
            imageSuggestionsPopup.hide();
            return;
        }

        int caretPosition = contentArea.getCaretPosition();
        if (caretPosition == 0) {
            imageSuggestionsPopup.hide();
            return;
        }

        // Find the start of the '@' query
        int atIndex = text.lastIndexOf('@', caretPosition - 1);
        if (atIndex == -1 || (atIndex > 0 && Character.isWhitespace(text.charAt(atIndex - 1)) == false)) {
            imageSuggestionsPopup.hide();
            return;
        }

        if (atIndex + 1 > caretPosition || caretPosition > text.length()) {
            imageSuggestionsPopup.hide();
            return;
        }
        String query = text.substring(atIndex + 1, caretPosition);

        List<String> matchingImages = noteManager.getGalleryImagePaths().stream()
                .filter(imageName -> imageName.toLowerCase().contains(query.toLowerCase()))
                .limit(10) // Limit results for performance
                .toList();

        if (!matchingImages.isEmpty()) {
            populateImageSuggestionsPopup(matchingImages, atIndex, caretPosition);
            if (!imageSuggestionsPopup.isShowing()) {
                // Show popup at the location of the '@' symbol
                imageSuggestionsPopup.show(contentArea, Side.BOTTOM, 0, 0);
            }
        } else {
            imageSuggestionsPopup.hide();
        }
    }

    private void populateImageSuggestionsPopup(List<String> imageNames, int startIndex, int endIndex) {
        imageSuggestionsPopup.getItems().clear();
        for (String imageName : imageNames) {
            MenuItem item = new MenuItem(imageName);
            item.setOnAction(e -> {
                // Create the markdown link for the image
                String markdownLink = String.format("![%s](gallery://%s)", imageName, imageName);

                // Replace the '@query' with the markdown link
                contentArea.replaceText(startIndex, endIndex, markdownLink);

                imageSuggestionsPopup.hide();
            });
            imageSuggestionsPopup.getItems().add(item);
        }
    }

    private void handleGoalInput(String text) {
        if (text != null && text.startsWith("@")) {
            String query = text.substring(1);
            if (!query.trim().isEmpty()) {
                // Search for notes
                Map<Note, Board> results = noteManager.searchAllNotes(query);
                List<Pair<Note, Board>> resultList = results.entrySet().stream()
                        .filter(entry -> !entry.getKey().getId().equals(noteCopy.getId())) // Exclude self
                        .map(entry -> new Pair<>(entry.getKey(), entry.getValue()))
                        .collect(Collectors.toList());

                if (!resultList.isEmpty()) {
                    // Populate and show popup
                    populateSuggestionsPopup(resultList);
                    if (!noteSuggestionsPopup.isShowing()) {
                        noteSuggestionsPopup.show(newGoalField, Side.BOTTOM, 0, 0);
                    }
                } else {
                    noteSuggestionsPopup.hide();
                }
            } else {
                noteSuggestionsPopup.hide();
            }
        } else {
            noteSuggestionsPopup.hide();
        }
    }

    private void populateSuggestionsPopup(List<Pair<Note, Board>> notes) {
        noteSuggestionsPopup.getItems().clear();
        for (Pair<Note, Board> pair : notes) {
            Note note = pair.getKey();
            Board board = pair.getValue();
            String itemText = "'" + note.getTitle() + "' on [" + board.getName() + "]";
            MenuItem item = new MenuItem(itemText);
            item.setOnAction(e -> {
                // Create a linked goal
                addLinkedGoal(note);
                newGoalField.clear();
                noteSuggestionsPopup.hide();
            });
            noteSuggestionsPopup.getItems().add(item);
        }
    }

    private void addLinkedGoal(Note targetNote) {
        // The description will be the note's title. The GoalTreeCell will prepend an icon.
        Note.Goal newGoal = new Note.Goal(targetNote.getTitle(), targetNote.getId(), targetNote.getTitle());
        TreeItem<Note.Goal> newGoalItem = new TreeItem<>(newGoal);

        // Add to the currently selected item's sub-goals, or to the root if nothing is selected.
        TreeItem<Note.Goal> selectedItem = goalsTreeView.getSelectionModel().getSelectedItem();
        (selectedItem != null ? selectedItem : goalsTreeView.getRoot()).getChildren().add(newGoalItem);

        updateGoalsProgress();
    }

    private void handleCopyLink() {
        if (noteCopy == null) return;

        // Create a custom URI for the note
        String link = "notetool://note/" + noteCopy.getId().toString();

        final Clipboard clipboard = Clipboard.getSystemClipboard();
        final ClipboardContent content = new ClipboardContent();
        content.putString(link);
        clipboard.setContent(content);

        // --- Provide temporary user feedback ---
        final Node originalGraphic = copyLinkButton.getGraphic();
        copyLinkButton.setGraphic(new FontIcon(MaterialDesignC.CHECK));
        copyLinkButton.setText("Copied!");

        // Revert the button's state after a short delay
        javafx.animation.PauseTransition pause = new javafx.animation.PauseTransition(Duration.seconds(2));
        pause.setOnFinished(event -> {
            copyLinkButton.setGraphic(originalGraphic);
            copyLinkButton.setText(""); // Assuming it was icon-only before
        });
        pause.play();
    }

    // --- Dependency Methods ---

    private void setupDependenciesListView() {
        dependenciesListView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Note.Dependency item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    String typeStr = item.type().toString().replace('_', ' ').toLowerCase();
                    setText(String.format("%s: %s", typeStr, item.otherNoteTitle()));
                    setGraphic(new FontIcon(MaterialDesignL.LINK_VARIANT));
                }
            }
        });

        ContextMenu contextMenu = new ContextMenu();
        MenuItem removeItem = new MenuItem("Remove Dependency");
        removeItem.setOnAction(e -> {
            Note.Dependency selectedDep = dependenciesListView.getSelectionModel().getSelectedItem();
            if (selectedDep != null) {
                // --- REMOVE INVERSE DEPENDENCY ---
                // Find the other note that this dependency points to
                noteManager.findNoteAcrossAllBoards(selectedDep.otherNoteId()).ifPresent(otherNote -> {
                    // Determine what the inverse dependency would look like. The title doesn't matter for equality.
                    Note.DependencyType inverseType = selectedDep.type().getInverse();
                    Note.Dependency inverseDependency = new Note.Dependency(noteCopy.getId(), inverseType, "");

                    // Get the other note's dependencies, remove the inverse link, and set them back
                    List<Note.Dependency> otherNoteDependencies = new ArrayList<>(otherNote.getDependencies());
                    if (otherNoteDependencies.remove(inverseDependency)) {
                        otherNote.setDependencies(otherNoteDependencies);
                        noteManager.markAsDirty(); // Mark manager as dirty for the change on the other note
                    }
                });

                // --- REMOVE FORWARD DEPENDENCY (from the current note) ---
                tempDependencies.remove(selectedDep);
                dependenciesListView.getItems().remove(selectedDep);
            }
        });
        contextMenu.getItems().add(removeItem);
        dependenciesListView.setContextMenu(contextMenu);
    }

    private void handleAddDependency() {
        if (noteManager == null) {
            showError("Error", "NoteManager is not available to search for notes.");
            return;
        }

        // Use the existing SearchResultsViewController in a dialog
        Dialog<Pair<Note, Board>> searchDialog = new SearchNoteDialog(noteManager, noteCopy.getId());
        Optional<Pair<Note, Board>> searchResult = searchDialog.showAndWait();

        if (searchResult.isEmpty()) {
            return; // User cancelled or didn't select anything
        }

        Note selectedNote = searchResult.get().getKey();

        // Ask for dependency type
        ChoiceDialog<Note.DependencyType> typeDialog = new ChoiceDialog<>(Note.DependencyType.RELATED_TO, Note.DependencyType.values());
        typeDialog.setTitle("Select Dependency Type");
        typeDialog.setHeaderText("How is this note related to '" + selectedNote.getTitle() + "'?");
        typeDialog.setContentText("This note...");

        Optional<Note.DependencyType> typeResult = typeDialog.showAndWait();

        // Create and add the dependency
        typeResult.ifPresent(type -> {
            // --- FORWARD DEPENDENCY (Current Note -> Selected Note) ---
            Note.Dependency forwardDependency = new Note.Dependency(selectedNote.getId(), type, selectedNote.getTitle());
            if (!tempDependencies.contains(forwardDependency)) { // Avoid duplicates
                tempDependencies.add(forwardDependency);
                dependenciesListView.getItems().add(forwardDependency);

                // --- INVERSE DEPENDENCY (Selected Note -> Current Note) ---
                // Get the inverse type
                Note.DependencyType inverseType = type.getInverse();
                // Create the dependency record for the other note. The title of the current note is used for display on the other note.
                Note.Dependency inverseDependency = new Note.Dependency(noteCopy.getId(), inverseType, noteCopy.getTitle());

                // Get the other note's current dependencies and add the new one
                List<Note.Dependency> otherNoteDependencies = new ArrayList<>(selectedNote.getDependencies());
                if (!otherNoteDependencies.contains(inverseDependency)) {
                    otherNoteDependencies.add(inverseDependency);
                    selectedNote.setDependencies(otherNoteDependencies);
                    noteManager.markAsDirty(); // IMPORTANT: Mark the manager as dirty because we changed another note
                }
            }
        });
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

        // Compare tags
        if (!tempTags.equals(initialNoteState.getTags())) return true;

        // Compare attachments (order doesn't matter)
        if (!new HashSet<>(tempAttachmentPaths).equals(new HashSet<>(initialNoteState.getAttachmentPaths()))) return true;

        // Compare dependencies (order does matter for display, but for dirty checking, a set is fine)
        if (!new HashSet<>(tempDependencies).equals(new HashSet<>(initialNoteState.getDependencies()))) return true;

        // Compare reference images
        if (!new HashSet<>(tempReferenceImagePaths).equals(new HashSet<>(initialNoteState.getReferenceImagePaths()))) return true;

        return false;
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

    private void handleOpenLinkedNote(UUID noteId) {
        // We need to check for unsaved changes before just closing.
        if (isDirty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Unsaved Changes");
            alert.setHeaderText("You have unsaved changes. Opening the link will discard them.");
            alert.setContentText("Do you want to discard your changes and open the linked note?");
            alert.getButtonTypes().setAll(ButtonType.YES, ButtonType.NO);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.YES) {
                this.noteToOpen = noteId;
                this.saved = false; // Explicitly mark as not saved
                dialogStage.close();
            }
            // if NO, do nothing, stay in the editor.
        } else {
            // No unsaved changes, just open it.
            this.noteToOpen = noteId;
            this.saved = false;
            dialogStage.close();
        }
    }

    private void showError(String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        if (dialogStage != null) alert.initOwner(dialogStage);
        alert.setTitle("Error");
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
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
        private final FontIcon linkIcon = new FontIcon(MaterialDesignL.LINK_VARIANT);
        private final Label linkLabel = new Label();
        private TextField editField;

        public GoalTreeCell() {
            Tooltip.install(addSubGoalButton, new Tooltip("Add Sub-goal"));
            Tooltip.install(deleteButton, new Tooltip("Delete Goal"));

            // Handle clicks on linked goals
            this.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY && getItem() != null && getItem().isLink()) {
                    getItem().getLinkedNoteId().ifPresent(noteId -> {
                        // Call the handler method in the outer class
                        NoteDetailViewController.this.handleOpenLinkedNote(noteId);
                    });
                    event.consume();
                }
            });

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
                        // Manually update the checkbox in the UI to reflect the model change
                        checkBox.setSelected(false);
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
                getStyleClass().remove("linked-goal");
            } else {
                if (item.isLink()) {
                    // It's a link, show it differently and disable editing/completion
                    getStyleClass().add("linked-goal");
                    setTooltip(new Tooltip("Click to open note: " + item.getDescription()));

                    linkLabel.setText(item.getDescription());
                    HBox linkBox = new HBox(5, linkIcon, linkLabel);
                    linkBox.setAlignment(Pos.CENTER_LEFT);
                    setGraphic(linkBox);
                    setText(null);
                } else {
                    // It's a normal goal
                    getStyleClass().remove("linked-goal");
                    setTooltip(null);
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

    /**
     * A private helper class to create a standardized dialog for searching notes.
     */
    private static class SearchNoteDialog extends Dialog<Pair<Note, Board>> {
        SearchNoteDialog(NoteManager noteManager, UUID noteToExclude) {
            setTitle("Find Note to Link");
            getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

            VBox content = new VBox(10);
            content.setPadding(new Insets(10));
            TextField searchField = new TextField();
            searchField.setPromptText("Search for note by title...");
            ListView<Pair<Note, Board>> resultsView = new ListView<>();
            resultsView.setPrefHeight(200);

            resultsView.setCellFactory(lv -> new ListCell<>() {
                @Override
                protected void updateItem(Pair<Note, Board> item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : "'" + item.getKey().getTitle() + "' on board [" + item.getValue().getName() + "]");
                }
            });

            searchField.setOnAction(e -> {
                String query = searchField.getText();
                if (query != null && !query.trim().isEmpty()) {
                    Map<Note, Board> results = noteManager.searchAllNotes(query);
                    List<Pair<Note, Board>> resultList = results.entrySet().stream()
                            .filter(entry -> !entry.getKey().getId().equals(noteToExclude)) // Exclude self
                            .map(entry -> new Pair<>(entry.getKey(), entry.getValue()))
                            .collect(Collectors.toList());
                    resultsView.setItems(FXCollections.observableArrayList(resultList));
                }
            });

            content.getChildren().addAll(new Label("Search for a note to link:"), searchField, resultsView);
            getDialogPane().setContent(content);
            Platform.runLater(searchField::requestFocus);

            setResultConverter(dialogButton -> {
                return dialogButton == ButtonType.OK ? resultsView.getSelectionModel().getSelectedItem() : null;
            });
        }
    }
}