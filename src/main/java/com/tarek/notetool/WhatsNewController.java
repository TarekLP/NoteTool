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
                "**New Note Model:** A remade and redesigned Note Model ensures only the best saving (not really obviously)"
                "**Image Gallery Improvements:** Obviously ive remade the Image Gallery to be a lot better and you can even link images in your notes."
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
                            Object result = webEngine.executeScript("document.body.scrollHeight");
                            if (result instanceof Number) {
                                double height = ((Number) result).doubleValue();
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