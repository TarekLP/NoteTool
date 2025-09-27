package com.tarek.notetool;

import javafx.beans.property.SimpleStringProperty;
import javafx.application.Platform;
import javafx.animation.Interpolator;
import javafx.collections.FXCollections;
import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.util.Duration;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.geometry.Pos;
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
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.MenuButton;
import javafx.scene.Cursor;
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
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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
import java.util.Comparator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.List;
import java.util.stream.Collectors;
import java.util.UUID;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.io.File;
import java.util.Arrays;
import javafx.beans.value.ChangeListener;
import org.kordamp.ikonli.javafx.FontIcon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignB;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignC;
import org.kordamp.ikonli.materialdesign2.MaterialDesignI;
import org.kordamp.ikonli.materialdesign2.MaterialDesignD;
import org.kordamp.ikonli.materialdesign2.MaterialDesignF;
import org.kordamp.ikonli.materialdesign2.MaterialDesignS;
import org.kordamp.ikonli.materialdesign2.MaterialDesignL;

public class MainViewController {

    @FXML
    private HBox columnsContainer;

    @FXML
    private StackPane rootStackPane; // The new root pane from your FXML

    @FXML
    private VBox imageGalleryPane; // The VBox containing the loaded ImageGalleryView

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
    private ToggleButton imageGalleryToggle; // The button to show/hide the gallery

    private NoteManager noteManager;
    private Board currentBoard;
    private final Map<UUID, VBox> noteContainersMap = new HashMap<>();

    private ImageGalleryViewController imageGalleryViewController;    public void setNoteManager(NoteManager noteManager) {
        this.noteManager = noteManager;        // Pass the manager to the gallery controller
        if (imageGalleryViewController != null) imageGalleryViewController.setNoteManager(noteManager);
    }


    private void setupImageGallery() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/tarek/notetool/ImageGalleryView.fxml"));
            // Manually create and set controller
            imageGalleryViewController = new ImageGalleryViewController();
            loader.setController(imageGalleryViewController);
            Node galleryContent = loader.load();

            // Set the close handler so the gallery can ask to be closed.
            imageGalleryViewController.setOnCloseRequestHandler(() -> {
                if (imageGalleryToggle.isSelected()) {
                    imageGalleryToggle.fire(); // Trigger the toggle button's action to close the panel
                }
            });

            // --- Resizable Gallery Setup ---
            Region resizeHandle = new Region();
            resizeHandle.setPrefWidth(7);
            resizeHandle.setCursor(Cursor.W_RESIZE);
            resizeHandle.setStyle("-fx-background-color: -color-border-muted;");

            // Use an HBox to contain the handle and the gallery content
            HBox galleryContainer = new HBox(resizeHandle, galleryContent);
            HBox.setHgrow(galleryContent, Priority.ALWAYS);

            // Add the container to the main gallery pane
            imageGalleryPane.getChildren().add(galleryContainer);

            // --- Drag-to-Resize Logic ---
            final double[] startX = new double[1];
            resizeHandle.setOnMousePressed(event -> {
                startX[0] = event.getSceneX();
            });

            resizeHandle.setOnMouseDragged(event -> {
                double deltaX = event.getSceneX() - startX[0];
                double newWidth = imageGalleryPane.getWidth() - deltaX;

                // Clamp the width between min and max values
                newWidth = Math.max(250, Math.min(600, newWidth));

                imageGalleryPane.setPrefWidth(newWidth);
                startX[0] = event.getSceneX();
            });

            // Initially hide the gallery off-screen to the right
            imageGalleryPane.setPrefWidth(350); // Set initial width
            imageGalleryPane.setVisible(false);
            Platform.runLater(() -> imageGalleryPane.setTranslateX(imageGalleryPane.getWidth()));

        } catch (IOException e) {
            e.printStackTrace();
            showError("UI Error", "Could not load the Image Gallery component.");
        }
    }

    public void displayBoard(Board board) {
        this.currentBoard = board;
        boardTitleLabel.setText(board.getName());
        columnsContainer.getChildren().clear(); // Clear previous board
        noteContainersMap.clear();
 
        int i = 0;
        for (Column boardColumn : board.getColumns()) {
            if (boardColumn.getName().equalsIgnoreCase("Archived") && !showArchivedToggle.isSelected()) {
                continue; // Skip archived column if toggle is off
            }
            VBox column = createColumn(boardColumn);
            columnsContainer.getChildren().add(column);

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

    @FXML
    private void initialize() {
        // Add listener for search field to re-filter the board
        searchField.setOnAction(event -> handleGlobalSearch());

        // Setup preferences button - This is now hidden, but we keep the logic in case it's re-added.
        preferencesButton.setGraphic(new FontIcon(MaterialDesignC.COG_OUTLINE));
        Tooltip.install(preferencesButton, new Tooltip("Preferences"));
        preferencesButton.setVisible(false);
        preferencesButton.setManaged(false);

        // Setup archive toggle button
        showArchivedToggle.setGraphic(new FontIcon(MaterialDesignA.ARCHIVE_OUTLINE));
        showArchivedToggle.selectedProperty().addListener((obs, oldVal, newVal) -> {
            if (currentBoard != null) {
                displayBoard(currentBoard);
            }
        });

        // Setup image gallery toggle button
        imageGalleryToggle.setGraphic(new FontIcon(MaterialDesignI.IMAGE_MULTIPLE_OUTLINE));
        Tooltip.install(imageGalleryToggle, new Tooltip("Toggle Image Gallery"));
        imageGalleryToggle.setOnAction(e -> handleToggleImageGallery());
        setupImageGallery();
    }

    private void handleToggleImageGallery() {
        // ... (code is unchanged)
    }

    // ... (rest of the file from createColumn onwards is largely the same, but with some methods removed)

    private VBox createColumn(Column boardColumn) {
        // Column Header
        List<Note> notesInColumn = currentBoard.getNotesInColumn(boardColumn.getId());
        int noteCount = notesInColumn.size();

        HBox columnHeader = new HBox();
        columnHeader.setSpacing(5);
        columnHeader.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label(boardColumn.getName() + " (" + noteCount + ")");
        titleLabel.setFont(new Font("System Bold", 16));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        MenuButton optionsButton = new MenuButton();
        optionsButton.setGraphic(new FontIcon(MaterialDesignD.DOTS_VERTICAL));
        Tooltip.install(optionsButton, new Tooltip("Column Options"));
        optionsButton.getStyleClass().add("rich-text-editor-button");

        // --- Column Options Menu ---
        MenuItem renameItem = new MenuItem("Rename Column");
        renameItem.setOnAction(e -> handleRenameColumn(boardColumn));

        MenuItem addLeftItem = new MenuItem("Add Column to Left");
        addLeftItem.setOnAction(e -> handleAddColumn(boardColumn, -1));

        MenuItem addRightItem = new MenuItem("Add Column to Right");
        addRightItem.setOnAction(e -> handleAddColumn(boardColumn, 1));

        MenuItem deleteItem = new MenuItem("Delete Column");
        deleteItem.setStyle("-fx-text-fill: -color-danger-fg;");
        deleteItem.setOnAction(e -> handleDeleteColumn(boardColumn));

        // --- Sorting Menu ---
        Menu sortMenu = new Menu("Sort by");
        Comparator<Note> byPriority = Comparator.comparing(Note::getPriority).reversed();
        Comparator<Note> byDueDate = Comparator.comparing(Note::getDueDate, Comparator.nullsLast(Comparator.naturalOrder()));
        Comparator<Note> byTitle = Comparator.comparing(Note::getTitle, String.CASE_INSENSITIVE_ORDER);

        MenuItem sortByPriority = new MenuItem("Priority (High to Low)");
        sortByPriority.setOnAction(e -> handleSortColumn(boardColumn.getId(), byPriority));
        MenuItem sortByDueDate = new MenuItem("Due Date (Soonest First)");
        sortByDueDate.setOnAction(e -> handleSortColumn(boardColumn.getId(), byDueDate));
        MenuItem sortByTitle = new MenuItem("Title (A-Z)");
        sortByTitle.setOnAction(e -> handleSortColumn(boardColumn.getId(), byTitle));
        sortMenu.getItems().addAll(sortByPriority, sortByDueDate, sortByTitle);

        optionsButton.getItems().addAll(renameItem, sortMenu, new SeparatorMenuItem(), addLeftItem, addRightItem, new SeparatorMenuItem(), deleteItem);

        columnHeader.getChildren().addAll(titleLabel, spacer, optionsButton);

        // --- Column Drag & Drop for Reordering ---
        SimpleStringProperty dragData = new SimpleStringProperty();
        columnHeader.setOnDragDetected(event -> {
            Dragboard db = columnHeader.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(boardColumn.getId().toString());
            db.setContent(content);
            dragData.set(boardColumn.getId().toString());
            event.consume();
        });

        columnHeader.setOnDragDone(event -> dragData.set(null));

        columnHeader.setOnDragOver(event -> {
            if (event.getGestureSource() != columnHeader && event.getDragboard().hasString()) {
                // Ensure we are dragging a column from the same board
                if (!event.getDragboard().getString().equals(boardColumn.getId().toString())) {
                    event.acceptTransferModes(TransferMode.MOVE);
                }
            }
            event.consume();
        });

        columnHeader.setOnDragEntered(event -> {
            if (event.getGestureSource() != columnHeader && event.getDragboard().hasString()) {
                columnHeader.getParent().getStyleClass().add("column-vbox-drag-over");
            }
        });

        columnHeader.setOnDragExited(event -> {
            columnHeader.getParent().getStyleClass().remove("column-vbox-drag-over");
        });

        columnHeader.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                UUID draggedColumnId = UUID.fromString(db.getString());
                handleReorderColumn(draggedColumnId, boardColumn.getId());
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });

        // VBox for notes
        VBox notesContainer = new VBox(5);
        noteContainersMap.put(boardColumn.getId(), notesContainer);

        // Populate with all notes for the given status
        notesInColumn.forEach(note -> {
            VBox noteCard = createNoteCard(note);
            notesContainer.getChildren().add(noteCard);
        });

        // Assemble the column
        VBox columnVBox = new VBox(10, columnHeader, notesContainer);

        // Only add the "Add Note" button for non-archived columns
        if (!boardColumn.getName().equalsIgnoreCase("Archived")) {
            Button addNoteButton = new Button("+ Add Note");
            addNoteButton.setMaxWidth(Double.MAX_VALUE);
            addNoteButton.setOnAction(e -> handleNewNote(boardColumn));
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
                // Dropping on a column moves the note to that column.
                handleReorderNote(noteId, boardColumn.getId(), -1); // -1 means add to end
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
        for (Column boardColumn : currentBoard.getColumns()) {
            VBox notesContainer = noteContainersMap.get(boardColumn.getId());
            if (notesContainer != null && notesContainer.getParent() instanceof VBox) {
                VBox columnVBox = (VBox) notesContainer.getParent();
                // The header is now an HBox, which is the first child.
                if (!columnVBox.getChildren().isEmpty() && columnVBox.getChildren().get(0) instanceof HBox) {
                    HBox headerBox = (HBox) columnVBox.getChildren().get(0);
                    // Find the Label within the HBox
                    Optional<Node> titleLabelOpt = headerBox.getChildren().stream()
                            .filter(node -> node instanceof Label)
                            .findFirst();

                    titleLabelOpt.ifPresent(node -> {
                        Label titleLabel = (Label) node;
                        int noteCount = currentBoard.getNotesInColumn(boardColumn.getId()).size();
                        titleLabel.setText(boardColumn.getName() + " (" + noteCount + ")");
                    });
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
        HBox detailsBox = new HBox(10);
        detailsBox.getChildren().add(createPriorityLabel(note));
        createDueDateLabel(note).ifPresent(detailsBox.getChildren()::add);
        createAssigneeLabel(note).ifPresent(detailsBox.getChildren()::add);
        createBlockerIcon(note).ifPresent(detailsBox.getChildren()::add);
        createBlocksIcon(note).ifPresent(detailsBox.getChildren()::add);
        createLinkIcon(note).ifPresent(detailsBox.getChildren()::add);
        return detailsBox;
    }

    private Label createPriorityLabel(Note note) {
        Label priorityLabel = new Label(note.getPriority().toString());
        priorityLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-fg-muted;");
        Tooltip.install(priorityLabel, new Tooltip("Priority: " + note.getPriority()));
        return priorityLabel;
    }

    private Optional<Label> createDueDateLabel(Note note) {
        if (note.getDueDate() == null) {
            return Optional.empty();
        }
        Label dueDateLabel = new Label("🗓️ " + note.getDueDate().format(DateTimeFormatter.ofPattern("MMM dd")));
        String tooltipText = "Due: " + note.getDueDate().format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' hh:mm a"));
        String style = "-fx-font-size: 10px; -fx-text-fill: -color-fg-muted;";

        if (note.isOverdue()) {
            style = "-fx-font-size: 10px; -fx-text-fill: -color-danger-fg;";
            tooltipText += " (Overdue)";
        }

        dueDateLabel.setStyle(style);
        Tooltip.install(dueDateLabel, new Tooltip(tooltipText));
        return Optional.of(dueDateLabel);
    }

    private Optional<Label> createAssigneeLabel(Note note) {
        if (note.getAssignees() == null || note.getAssignees().isEmpty()) {
            return Optional.empty();
        }
        String assigneeNames = note.getAssignees().stream().map(User::name).collect(Collectors.joining(", "));
        Label assigneeLabel = new Label("👤 " + assigneeNames);
        assigneeLabel.setMaxWidth(100);
        assigneeLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: -color-fg-muted;");
        Tooltip.install(assigneeLabel, new Tooltip("Assigned to: " + assigneeNames));
        return Optional.of(assigneeLabel);
    }

    private Optional<FontIcon> createBlockerIcon(Note note) {
        List<String> activeBlockerTitles = getOpenBlockers(note);
        if (activeBlockerTitles.isEmpty()) {
            return Optional.empty();
        }
        FontIcon depIcon = new FontIcon(MaterialDesignL.LOCK_OUTLINE);
        depIcon.setIconSize(12);
        depIcon.getStyleClass().addAll("detail-icon", "danger-icon");
        Tooltip.install(depIcon, new Tooltip("Blocked by: " + String.join(", ", activeBlockerTitles)));
        return Optional.of(depIcon);
    }

    private Optional<FontIcon> createBlocksIcon(Note note) {
        if (note.getDependencies() == null) return Optional.empty();
        List<String> blockedByThisNoteTitles = note.getDependencies().stream()
                .filter(dep -> dep.type() == Note.DependencyType.BLOCKS)
                .map(Note.Dependency::otherNoteTitle)
                .collect(Collectors.toList());

        if (blockedByThisNoteTitles.isEmpty()) {
            return Optional.empty();
        }
        FontIcon depIcon = new FontIcon(MaterialDesignB.BLOCK_HELPER);
        depIcon.setIconSize(12);
        depIcon.getStyleClass().addAll("detail-icon", "warning-icon");
        Tooltip.install(depIcon, new Tooltip("Blocks: " + String.join(", ", blockedByThisNoteTitles)));
        return Optional.of(depIcon);
    }

    private Optional<FontIcon> createLinkIcon(Note note) {
        if (note.getDependencies() == null) return Optional.empty();
        List<String> relatedNoteTitles = note.getDependencies().stream()
                .filter(dep -> dep.type() == Note.DependencyType.RELATED_TO)
                .map(Note.Dependency::otherNoteTitle)
                .collect(Collectors.toList());

        boolean hasLinkedGoals = note.getGoals().stream().anyMatch(this::goalHasLinks);

        if (relatedNoteTitles.isEmpty() && !hasLinkedGoals) {
            return Optional.empty();
        }

        FontIcon linkIcon = new FontIcon(MaterialDesignL.LINK_VARIANT);
        linkIcon.setIconSize(12);
        linkIcon.getStyleClass().add("detail-icon");

        List<String> tooltipParts = new ArrayList<>();
        if (!relatedNoteTitles.isEmpty()) {
            tooltipParts.add("Related to: " + String.join(", ", relatedNoteTitles));
        }
        if (hasLinkedGoals) {
            tooltipParts.add("Contains links to other notes in its goals.");
        }

        Tooltip.install(linkIcon, new Tooltip(String.join("\n", tooltipParts)));
        return Optional.of(linkIcon);
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

        MenuItem moveToTop = new MenuItem("Move to Top");
        moveToTop.setGraphic(new FontIcon(MaterialDesignA.ARROW_UP_BOLD_BOX_OUTLINE));
        moveToTop.setOnAction(e -> handleReorderNote(note.getId(), note.getColumnId(), 0));

        MenuItem moveToBottom = new MenuItem("Move to Bottom");
        moveToBottom.setGraphic(new FontIcon(MaterialDesignA.ARROW_DOWN_BOLD_BOX_OUTLINE));
        moveToBottom.setOnAction(e -> {
            VBox container = noteContainersMap.get(note.getColumnId());
            int newIndex = container != null ? container.getChildren().size() : -1;
            handleReorderNote(note.getId(), note.getColumnId(), newIndex);
        });

        Menu moveMenu = new Menu("Move to");
        moveMenu.setGraphic(new FontIcon(MaterialDesignF.FOLDER_MOVE_OUTLINE));
        for (Column newColumn : currentBoard.getColumns()) {
            if (!newColumn.getId().equals(note.getColumnId())) {
                MenuItem moveItem = new MenuItem(newColumn.getName());
                moveItem.setOnAction(e -> {
                    VBox newContainer = noteContainersMap.get(newColumn.getId());
                    // Move to the end of the target column
                    handleReorderNote(note.getId(), newColumn.getId(), newContainer.getChildren().size());
                });
                moveMenu.getItems().add(moveItem);
            }
        }
        // Disable the "Move to" menu if there are no other columns to move to.
        if (moveMenu.getItems().isEmpty()) {
            moveMenu.setDisable(true);
        }

        MenuItem duplicateItem = new MenuItem("Duplicate Note");
        duplicateItem.setGraphic(new FontIcon(MaterialDesignC.CONTENT_COPY));
        duplicateItem.setOnAction(e -> handleDuplicateNote(note));
        MenuItem deleteItem = new MenuItem("Delete");
        deleteItem.setGraphic(new FontIcon(MaterialDesignD.DELETE_OUTLINE));
        deleteItem.setOnAction(e -> handleDeleteNote(note));
        contextMenu.getItems().addAll(moveToTop, moveToBottom, new SeparatorMenuItem(),
                duplicateItem, moveMenu, new SeparatorMenuItem(), deleteItem);
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
        setupDragSourceForCard(card, note);
        setupDropTargetForCard(card);
    }

    /**
     * Configures the event handlers for a note card to act as a drag source.
     * This includes starting the drag operation and cleaning up when it's done.
     *
     * @param card The UI node for the note card.
     * @param note The data object for the note.
     */
    private void setupDragSourceForCard(VBox card, Note note) {
        DropShadow shadow = new DropShadow();
        shadow.setColor(Color.web("#9370DB", 0.7)); // Use accent color
        shadow.setRadius(15);
        shadow.setSpread(0.1);

        card.setOnDragDetected(event -> {
            if (event.getButton() != MouseButton.PRIMARY) return;

            Dragboard db = card.startDragAndDrop(TransferMode.MOVE);
            ClipboardContent content = new ClipboardContent();
            content.putString(note.getId().toString());
            db.setContent(content);

            // Use a snapshot of the card as the drag view
            WritableImage snapshot = card.snapshot(new SnapshotParameters(), null);
            db.setDragView(snapshot);

            card.setEffect(shadow);
            event.consume();
        });

        card.setOnDragDone(event -> {
            card.setEffect(null);
            // Clear any lingering drop indicators on this card, in case the drag was cancelled.
            card.getStyleClass().removeAll("note-card-drop-above", "note-card-drop-below");
            event.consume();
        });
    }

    /**
     * Configures the event handlers for a note card to act as a drop target for reordering.
     *
     * @param card The UI node for the note card.
     */
    private void setupDropTargetForCard(VBox card) {
        card.setOnDragOver(event -> {
            if (event.getGestureSource() != card && event.getDragboard().hasString()) {
                event.acceptTransferModes(TransferMode.MOVE);

                // Update visual indicator based on cursor position
                card.getStyleClass().removeAll("note-card-drop-above", "note-card-drop-below");
                if (event.getY() < card.getHeight() / 2) {
                    card.getStyleClass().add("note-card-drop-above");
                } else {
                    card.getStyleClass().add("note-card-drop-below");
                }
            }
            event.consume();
        });

        card.setOnDragExited(event -> {
            card.getStyleClass().removeAll("note-card-drop-above", "note-card-drop-below");
            event.consume();
        });

        card.setOnDragDropped(event -> {
            Dragboard db = event.getDragboard();
            boolean success = false;
            if (db.hasString()) {
                UUID draggedNoteId = UUID.fromString(db.getString());
                Note targetNote = (Note) card.getUserData();
                int targetIndex = ((VBox) card.getParent()).getChildren().indexOf(card);
                int newIndex = (event.getY() < card.getHeight() / 2) ? targetIndex : targetIndex + 1;

                handleReorderNote(draggedNoteId, targetNote.getColumnId(), newIndex);
                success = true;
            }
            event.setDropCompleted(success);
            event.consume();
        });
    }

    private void handleNewNote(Column column) {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("New Note");
        dialog.setHeaderText("Create a new note in the " + column.getName() + " column");

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
            newNote.setColumnId(column.getId());

            // Automatically assign the current user to the new note
            if (noteManager.getCurrentUser() != null) {
                newNote.setAssignees(List.of(noteManager.getCurrentUser()));
            }

            currentBoard.addNote(newNote);
            noteManager.markAsDirty();

            // Incrementally update the UI
            noteContainersMap.get(column.getId()).getChildren().add(createNoteCard(newNote));
            updateColumnCounts();
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
                Note targetNote = pair.getKey();
                if (currentBoard != null && currentBoard.getName().equals(targetBoard.getName())) {
                    showNoteDetailView(targetNote, null);
                } else {
                    showInfo("Note on Different Board", "The selected note '" + targetNote.getTitle() + "' is on the board '" + targetBoard.getName() + "'.\n\nPlease open that board to view the note.");
                }
            });
        } catch (IOException e) {
            showError("Search Error", "Could not open search results view. Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleDuplicateNote(Note originalNote) {
        Note newNote = originalNote.duplicate(); // Use the dedicated duplicate method

        // Add the current user as an assignee to the duplicated note, if not already present.
        User currentUser = noteManager.getCurrentUser();
        if (currentUser != null) {
            List<User> newAssignees = new ArrayList<>(newNote.getAssignees());
            if (!newAssignees.contains(currentUser)) {
                newAssignees.add(currentUser);
                newNote.setAssignees(newAssignees);
            }
        }

        currentBoard.addNote(newNote);
        noteManager.markAsDirty();

        // Incrementally add the new card to the UI instead of redrawing everything
        VBox container = noteContainersMap.get(newNote.getColumnId());
        if (container != null) {
            container.getChildren().add(createNoteCard(newNote));
        }
        updateColumnCounts();
    }

    /**
     * Handles the sorting of a column. It sorts the underlying data model
     * and then re-populates the UI with a staggered fade-in animation.
     * @param status The status of the column to sort.
     * @param comparator The comparator to use for sorting.
     */
    private void handleSortColumn(UUID columnId, Comparator<Note> comparator) {
        if (currentBoard == null) {
            return;
        }

        // 1. Get the notes and sort them for display
        List<Note> notesToSort = new ArrayList<>(currentBoard.getNotesInColumn(columnId));
        notesToSort.sort(comparator);

        // 2. Re-populate the UI container
        VBox notesContainer = noteContainersMap.get(columnId);
        if (notesContainer != null) {
            notesContainer.getChildren().clear();

            for (int i = 0; i < notesToSort.size(); i++) {
                Note note = notesToSort.get(i);
                VBox noteCard = createNoteCard(note);

                noteCard.setOpacity(0);
                FadeTransition ft = new FadeTransition(Duration.millis(300), noteCard);
                ft.setToValue(1);
                ft.setDelay(Duration.millis(i * 40)); // Stagger the animation
                ft.play();

                notesContainer.getChildren().add(noteCard);
            }
        }
        // Note: This is a UI-only sort, so we don't mark the model as dirty.
    }

    private void handleReorderNote(UUID draggedNoteId, UUID targetColumnId, int newIndexInUI) {
        // Find the note first to perform checks before any UI manipulation
        currentBoard.findNoteById(draggedNoteId).ifPresent(noteToMove -> {
            currentBoard.findColumnById(targetColumnId).ifPresent(targetColumn -> {
                // --- Check for blockers before moving to DONE ---
                if (targetColumn.getName().equalsIgnoreCase("Done") || targetColumn.getName().equalsIgnoreCase("Archived")) {
                    List<String> openBlockers = getOpenBlockers(noteToMove);
                    if (!openBlockers.isEmpty()) {
                        String blockersList = openBlockers.stream()
                                .map(title -> "- " + title)
                                .collect(Collectors.joining("\n"));
                        showError("Move Blocked", "This note cannot be completed because it is still blocked by:\n\n" +
                                blockersList);
                        return; // Abort the move
                    }
                }

                // If checks pass, proceed with the move
                VBox oldContainer = noteContainersMap.get(noteToMove.getColumnId());
                VBox newContainer = noteContainersMap.get(targetColumnId);

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
                        if (currentBoard.moveNote(draggedNoteId, targetColumnId, modelIndex)) {
                            // If model update is successful, update the UI
                            oldContainer.getChildren().remove(cardNode);
                            if (newContainer != null) { // Defensive check
                                // Add to the correct position in the UI
                                if (newIndexInUI >= 0 && newIndexInUI <= newContainer.getChildren().size()) {
                                    newContainer.getChildren().add(newIndexInUI, cardNode);
                                } else {
                                    newContainer.getChildren().add(cardNode); // Fallback to adding at the end
                                }
                            }
                            updateColumnCounts();
                            noteManager.markAsDirty();
                        }
                    });
                }
            });
        });
    }

    private void handleRenameColumn(Column column) {
        TextInputDialog dialog = new TextInputDialog(column.getName());
        dialog.setTitle("Rename Column");
        dialog.setHeaderText("Enter a new name for the column.");
        dialog.setContentText("Name:");

        dialog.showAndWait().ifPresent(newName -> {
            if (!newName.trim().isEmpty() && !newName.equals(column.getName())) {
                column.setName(newName);
                noteManager.markAsDirty();
                displayBoard(currentBoard); // Redraw board to reflect name change
            }
        });
    }

    private void handleAddColumn(Column existingColumn, int offset) {
        List<Column> columns = new ArrayList<>(currentBoard.getColumns());
        int index = columns.indexOf(existingColumn);
        if (index != -1) {
            columns.add(index + (offset > 0 ? 1 : 0), new Column("New Column"));
            currentBoard.setColumns(columns);
            noteManager.markAsDirty();
            displayBoard(currentBoard);
        }
    }

    private void handleDeleteColumn(Column columnToDelete) {
        if (currentBoard.getColumns().size() <= 1) {
            showError("Cannot Delete", "You cannot delete the last column on the board.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Column");
        confirmation.setHeaderText("Are you sure you want to delete the column '" + columnToDelete.getName() + "'?");
        confirmation.setContentText("All notes in this column will be moved to the first column. This action cannot be undone.");

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                List<Column> columns = new ArrayList<>(currentBoard.getColumns());
                Column firstColumn = columns.get(0);
                // Move notes from the deleted column to the first column
                List<Note> notesToMove = new ArrayList<>(currentBoard.getNotesInColumn(columnToDelete.getId()));
                notesToMove.forEach(note -> {
                    currentBoard.moveNote(note.getId(), firstColumn.getId(), -1); // Move to end of the first column
                });

                columns.remove(columnToDelete);
                currentBoard.setColumns(columns);
                noteManager.markAsDirty();
                // The underlying model for notes has changed columns, so a full redraw is the safest way
                // to ensure the UI is perfectly in sync.
                displayBoard(currentBoard); 
            }
        });
    }

    private void handleReorderColumn(UUID draggedColumnId, UUID targetColumnId) {
        List<Column> columns = new ArrayList<>(currentBoard.getColumns());
        Optional<Column> dragged = columns.stream().filter(c -> c.getId().equals(draggedColumnId)).findFirst();
        if (dragged.isPresent()) {
            int targetIndex = -1;
            for (int i = 0; i < columns.size(); i++) {
                if (columns.get(i).getId().equals(targetColumnId)) {
                    targetIndex = i;
                    break;
                }
            }
            if (targetIndex != -1) {
                columns.remove(dragged.get());
                columns.add(targetIndex, dragged.get());
                currentBoard.setColumns(columns);
                noteManager.markAsDirty();
                displayBoard(currentBoard);
            }
        }
    }


    private void handleDeleteNote(Note note) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Note");
        confirmation.setHeaderText("Are you sure you want to delete this note?");
        confirmation.setContentText("Note: " + note.getTitle());

        confirmation.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                VBox container = noteContainersMap.get(note.getColumnId());
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
                () -> showInfo("Quick Add", "Quick Add can be accessed from the Welcome screen.")
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
            controller.setNoteManager(noteManager); // Pass the NoteManager for dependency searching
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
            UUID originalColumnId = note.getColumnId();

            // Record access before showing
            noteManager.recordNoteAccess(note.getId());

            // Show the dialog and wait until the user closes it.
            // The WelcomeViewController will now be responsible for refreshing its recent notes list.
            dialogStage.showAndWait();

            // Check if the user clicked a link to open another note
            Optional<UUID> noteToOpenId = controller.getNoteToOpen();
            if (noteToOpenId.isPresent()) {
                noteManager.findNoteAndBoard(noteToOpenId.get()).ifPresent(pair -> {
                    // Use Platform.runLater to avoid issues with opening a new dialog
                    // while the old one is still in its closing phase.
                    Platform.runLater(() -> {
                        // Switch to the board if it's not the current one
                        // Since we can't open a new board from here, we just show an info message.
                        // A more advanced implementation could use a callback to the MainApp to open the new window.
                        showInfo("Note on Different Board", "The linked note '" + pair.note.getTitle() + "' is on the board '" + pair.board.getName() + "'.\n\nPlease open that board to view the note.");
                        // If it was on the same board, we would open it:
                        // if (currentBoard != null && currentBoard.getName().equals(pair.board.getName())) { showNoteDetailView(pair.note, null); }
                    });
                });
                // Stop further processing since we are opening a new note and any other result is irrelevant.
                return;
            }

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
                    if (!Objects.equals(originalColumnId, note.getColumnId())) {
                        VBox oldContainer = noteContainersMap.get(originalColumnId);
                        oldContainer.getChildren().remove(noteCard);
                        VBox newContainer = noteContainersMap.get(note.getColumnId());
                        if (newContainer != null) newContainer.getChildren().add(createNoteCard(note)); // Re-create the card in the new column
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

    /**
     * Checks a note for any "BLOCKED_BY" dependencies that are not yet completed.
     *
     * @param note The note to check.
     * @return A list of titles of the notes that are actively blocking the given note.
     */
    private List<String> getOpenBlockers(Note note) {
        if (note == null || note.getDependencies() == null) {
            return Collections.emptyList();
        }
        return note.getDependencies().stream()
                .filter(dep -> dep.type() == Note.DependencyType.BLOCKED_BY)
                .filter(dep -> noteManager.findNoteAndBoard(dep.otherNoteId())
                        .map(pair -> !pair.board.findColumnById(pair.note.getColumnId()).map(c -> c.getName().equalsIgnoreCase("Done") || c.getName().equalsIgnoreCase("Archived")).orElse(false))
                        .orElse(false)) // Treat a non-existent note as a non-blocker
                .map(Note.Dependency::otherNoteTitle)
                .collect(Collectors.toList());
    }

    /**
     * Recursively checks if a goal or any of its sub-goals are links to other notes.
     *
     * @param goal The goal to check.
     * @return true if a link is found, false otherwise.
     */
    private boolean goalHasLinks(Note.Goal goal) {
        if (goal.isLink()) {
            return true;
        }
        // The getSubGoals() method on the Goal class returns an unmodifiable list, which is never null.
        return goal.getSubGoals().stream().anyMatch(this::goalHasLinks);
    }
}