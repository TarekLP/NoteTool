package com.tarek.notetool;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javafx.scene.paint.Color;

public class PreferencesViewController {

    @FXML
    private ComboBox<String> themeComboBox;

    @FXML
    private ListView<User> userListView;

    @FXML
    private TextField newUserField;

    @FXML
    private ListView<String> tagListView;

    @FXML
    private TextField newTagField;

    @FXML
    private TextField currentUserNameField;

    // --- Color Customizer FXML Fields ---
    @FXML
    private ColorPicker accentFgColorPicker;
    @FXML
    private ColorPicker accentEmphasisColorPicker;
    @FXML
    private ColorPicker accentMutedColorPicker;
    @FXML
    private ColorPicker accentSubtleColorPicker;
    @FXML
    private Button resetColorsButton;


    private Stage dialogStage;
    private Scene mainScene;
    private ObservableList<User> systemUsers;
    private ObservableList<String> systemTags;
    private User currentUser;

    @FXML
    private void initialize() {
        // --- Appearance Tab ---
        themeComboBox.setItems(FXCollections.observableArrayList("Dark", "Light"));
        // Determine current theme to set initial value
        String primerDarkStylesheet = new PrimerDark().getUserAgentStylesheet();
        if (Application.getUserAgentStylesheet() != null && Application.getUserAgentStylesheet().equals(primerDarkStylesheet)) {
            themeComboBox.setValue("Dark");
        } else {
            themeComboBox.setValue("Light");
        }
        themeComboBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                handleThemeSwitch(newVal);
            }
        });

        // --- Color Customizer Setup ---
        initializeColorPickers();

        // --- Users Tab ---
        newUserField.setOnAction(e -> handleAddUser());

        userListView.setCellFactory(lv -> new ListCell<>() {
            private final HBox hbox = new HBox(10);
            private final Label nameLabel = new Label();
            private final Button deleteButton = new Button("Delete");

            {
                deleteButton.setStyle("-fx-text-fill: -color-danger-fg;");
                deleteButton.setOnAction(event -> {
                    if (getItem() != null) {
                        getListView().getItems().remove(getItem());
                    }
                });
                HBox.setHgrow(nameLabel, Priority.ALWAYS);
                hbox.getChildren().addAll(nameLabel, deleteButton);
            }

            @Override
            protected void updateItem(User item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(item.name());
                    setGraphic(hbox);
                }
            }
        });

        // --- Tags Tab ---
        newTagField.setOnAction(e -> handleAddTag());
        tagListView.setCellFactory(lv -> new ListCell<>() {
            private final HBox hbox = new HBox(10);
            private final Label nameLabel = new Label();
            private final Button deleteButton = new Button("Delete");

            {
                deleteButton.setStyle("-fx-text-fill: -color-danger-fg;");
                deleteButton.setOnAction(event -> {
                    if (getItem() != null) {
                        getListView().getItems().remove(getItem());
                    }
                });
                HBox.setHgrow(nameLabel, Priority.ALWAYS);
                hbox.getChildren().addAll(nameLabel, deleteButton);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    nameLabel.setText(item);
                    setGraphic(hbox);
                }
            }
        });
    }

    public void setDialogStage(Stage dialogStage) {
        this.dialogStage = dialogStage;
        this.dialogStage.getScene().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                // Closing via ESC should also save settings.
                handleClose(); // handleClose now saves settings.
            }
        });

        // Save settings when the window is closed via the 'X' button.
        this.dialogStage.setOnCloseRequest(event -> {
            saveColorSettings();
        });
    }

    public void setMainScene(Scene mainScene) {
        this.mainScene = mainScene;
    }

    public void setUsers(List<User> users) {
        this.systemUsers = FXCollections.observableArrayList(users);
        userListView.setItems(this.systemUsers);
    }

    public void setTags(Set<String> tags) {
        this.systemTags = FXCollections.observableArrayList(tags);
        tagListView.setItems(this.systemTags);
    }

    public void setCurrentUser(User currentUser) {
        this.currentUser = currentUser;
        if (currentUser != null) {
            currentUserNameField.setText(currentUser.name());
        }
    }

    public List<User> getUpdatedUsers() {
        return new ArrayList<>(systemUsers);
    }

    public Set<String> getUpdatedTags() {
        return new HashSet<>(systemTags);
    }

    public User getUpdatedCurrentUser() {
        String name = currentUserNameField.getText();
        if (name == null || name.trim().isEmpty()) {
            return this.currentUser; // Return old one if empty
        }
        if (this.currentUser != null && this.currentUser.name().equals(name)) {
            return this.currentUser;
        }
        return new User(name);
    }

    private void handleThemeSwitch(String theme) {
        if (mainScene == null) {
            return;
        }

        if ("Dark".equals(theme)) {
            Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
        } else { // "Light"
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
        }

        // The purple theme should apply to both light and dark modes.
        // We ensure it's always present, as it might have been removed by the old logic.
        String purpleThemePath = getClass().getResource("/com/tarek/notetool/purple-theme.css").toExternalForm();
        if (!mainScene.getStylesheets().contains(purpleThemePath)) {
            mainScene.getStylesheets().add(purpleThemePath);
        }
    }

    @FXML
    private void handleResetColors() {
        // Set color pickers to default values
        accentFgColorPicker.setValue(ThemeManager.DEFAULT_ACCENT_FG);
        accentEmphasisColorPicker.setValue(ThemeManager.DEFAULT_ACCENT_EMPHASIS);
        accentMutedColorPicker.setValue(ThemeManager.DEFAULT_ACCENT_MUTED);
        accentSubtleColorPicker.setValue(ThemeManager.DEFAULT_ACCENT_SUBTLE);

        // The above setters will trigger the listeners and apply changes live.
        // We then save these default values.
        saveColorSettings();
    }

    private void initializeColorPickers() {
        // Load saved preferences or use defaults
        java.util.prefs.Preferences prefs = java.util.prefs.Preferences.userNodeForPackage(MainApp.class);

        accentFgColorPicker.setValue(Color.web(prefs.get("colorAccentFg", ThemeManager.colorToWeb(ThemeManager.DEFAULT_ACCENT_FG))));
        accentEmphasisColorPicker.setValue(Color.web(prefs.get("colorAccentEmphasis", ThemeManager.colorToWeb(ThemeManager.DEFAULT_ACCENT_EMPHASIS))));
        accentMutedColorPicker.setValue(Color.web(prefs.get("colorAccentMuted", ThemeManager.colorToWeb(ThemeManager.DEFAULT_ACCENT_MUTED))));
        accentSubtleColorPicker.setValue(Color.web(prefs.get("colorAccentSubtle", ThemeManager.colorToWeb(ThemeManager.DEFAULT_ACCENT_SUBTLE))));

        // Add listeners to apply changes live
        accentFgColorPicker.valueProperty().addListener((obs, oldVal, newVal) -> applyLiveColorChanges());
        accentEmphasisColorPicker.valueProperty().addListener((obs, oldVal, newVal) -> applyLiveColorChanges());
        accentMutedColorPicker.valueProperty().addListener((obs, oldVal, newVal) -> applyLiveColorChanges());
        accentSubtleColorPicker.valueProperty().addListener((obs, oldVal, newVal) -> applyLiveColorChanges());
    }

    /**
     * Applies the currently selected colors from the ColorPickers to the main scene.
     */
    private void applyLiveColorChanges() {
        if (mainScene == null) return;

        String fg = ThemeManager.colorToWeb(accentFgColorPicker.getValue());
        String emphasis = ThemeManager.colorToWeb(accentEmphasisColorPicker.getValue());
        String muted = ThemeManager.colorToWeb(accentMutedColorPicker.getValue());
        String subtle = ThemeManager.colorToWeb(accentSubtleColorPicker.getValue());

        ThemeManager.applyColors(mainScene, fg, emphasis, muted, subtle);
    }

    /**
     * Saves the current color picker values to user preferences.
     */
    private void saveColorSettings() {
        ThemeManager.saveColorPreferences(accentFgColorPicker.getValue(), accentEmphasisColorPicker.getValue(),
                accentMutedColorPicker.getValue(), accentSubtleColorPicker.getValue());
    }

    @FXML
    private void handleAddUser() {
        String name = newUserField.getText();
        if (name != null && !name.trim().isEmpty()) {
            systemUsers.add(new User(name));
            newUserField.clear();
        }
    }

    @FXML
    private void handleAddTag() {
        String name = newTagField.getText();
        if (name != null && !name.trim().isEmpty()) {
            String lowerCaseTag = name.toLowerCase();
            if (!systemTags.contains(lowerCaseTag)) {
                systemTags.add(lowerCaseTag);
            }
            newTagField.clear();
        }
    }

    @FXML
    private void handleClose() {
        saveColorSettings();
        dialogStage.close();
    }
}