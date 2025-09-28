package com.tarek.notetool;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.concurrent.Worker;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.Region;
import javafx.scene.web.WebEngine;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;

import java.util.List;
import java.util.Arrays;
import java.net.URL;
import java.util.Objects;

public class WhatsNewController {

    @FXML
    private Label headerLabel;

    @FXML
    private ListView<String> changesListView;

    @FXML
    private Button closeButton;

    private Stage dialogStage;

    private final Parser markdownParser;
    private final HtmlRenderer markdownRenderer;

    public WhatsNewController() {
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
        headerLabel.setText("What's New in v" + VersionInfo.CURRENT_VERSION);

        List<String> changes = List.of(
                "**✨ Smoother Animations:** Dragging and dropping notes now feels more fluid with improved visual feedback and physics-based 'settle' animations.",
                "**✨ Resizable Gallery:** The image gallery panel can now be resized by dragging its left edge to give you more space.",
                "**✨ Image Hover Effect:** Reference images inside a note now smoothly scale up when you hover over them, making them easier to see.",
                "**🐛 Bug Fixes & Polish:** This update includes numerous small improvements, including corrected icons and fixes for startup crashes and UI component behavior.",
                "**✨ Enhanced Markdown Preview:** The live preview now supports syntax highlighting for code blocks and renders GitHub-style task lists (`- [x]`).",
                "**✨ Live Markdown Editor:** The note editor now features a real-time, side-by-side preview. No more switching tabs to see your formatted text and images!",
                "**✨ Slide-out Image Gallery:** A new global panel on the right to collect reference images. Toggle it with the new image icon in the top bar.",
                "**✨ Customizable Columns:** Right-click a column header to rename, add, delete, or reorder columns.",
                "**✨ Embed Images in Notes:** Type `@` in the note editor to search and embed images from your gallery directly into the note content.",
                "**✨ Add images to the gallery** from your computer, by pasting image files, or by pasting image data (like screenshots) from your clipboard.",
                "**✨ Drag-and-Drop References:** Drag images from the gallery and drop them onto a note to create a visual reference.",
                "**✨ Note Model Update:** The data model for notes and boards has been updated to support customizable columns and image references."
        );

        changesListView.setItems(FXCollections.observableArrayList(changes));
        // Render list view items as Markdown
        changesListView.setCellFactory(param -> new ListCell<>() {
            private final WebView webView = new WebView();
            private final WebEngine webEngine = webView.getEngine();

            {
                // Bind WebView's preferred width to the ListView's width, adjusting for padding/scrollbars
                webView.prefWidthProperty().bind(changesListView.widthProperty().subtract(20));
                webView.setPrefHeight(Region.USE_COMPUTED_SIZE); // Allow height to be computed

                // Listener to adjust WebView height after content is loaded
                webEngine.getLoadWorker().stateProperty().addListener((obs, oldState, newState) -> {
                    if (newState == Worker.State.SUCCEEDED) {
                        // Execute JavaScript to get the content height
                        // This needs to be run on the JavaFX Application Thread
                        Platform.runLater(() -> {
                            // Get the height of the content, including padding
                            Double height = (Double) webEngine.executeScript("document.body.scrollHeight");
                            if (height != null && height > 0) {
                                // Add some extra padding to prevent scrollbars from appearing unnecessarily
                                webView.setPrefHeight(height + 10);
                                // Request layout to ensure the ListCell resizes
                                getListView().requestLayout();
                            }
                        });
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    // Render markdown to HTML
                    com.vladsch.flexmark.util.ast.Node document = markdownParser.parse(item);
                    String html = markdownRenderer.render(document);

                    // Get the path to the external CSS file.
                    URL cssResource = getClass().getResource("/com/tarek/notetool/markdown-preview.css");
                    String cssLink = "";
                    if (cssResource != null) {
                        cssLink = "<link rel=\"stylesheet\" href=\"" + cssResource.toExternalForm() + "\">";
                    } else {
                        System.err.println("Warning: markdown-preview.css not found for What's New dialog.");
                    }

                    String fullHtml = """
                        <html>
                            <head>
                                %s
                                <style>
                                    body { margin: 0; padding: 5px; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif; font-size: 1em; }
                                    p, ul, ol { margin-top: 0; margin-bottom: 0.5em; }
                                </style>
                            </head>
                            <body>
                                %s
                            </body>
                        </html>
                    """.formatted(cssLink, html);

                    webEngine.loadContent(fullHtml);
                    setGraphic(webView);
                    setText(null); // Clear text as content is in WebView
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