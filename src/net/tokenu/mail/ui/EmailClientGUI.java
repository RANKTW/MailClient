package net.tokenu.mail.ui;

import com.sun.mail.util.MailConnectException;
import com.commons.LogUtil;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.web.WebView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.Stage;
import com.commons.ThrowableUtil;
import javafx.util.Duration;
import net.tokenu.mail.model.EmailAccount;
import net.tokenu.mail.model.EmailMessage;
import net.tokenu.mail.service.Microsoft;
import net.tokenu.mail.util.AuthType;
import net.tokenu.mail.util.InvalidAuthenticationToken;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EmailClientGUI extends Application {

    private ListView<EmailAccount> accountListView;
    private TableView<EmailMessage> emailTableView;
    private WebView emailContentView;
    private BorderPane emailContentPane;
    private Button closeButton;
    private Button loadImageButton;
    private Button deleteButton;
    private Label subjectLabel;
    private Label receivedDateLabel;
    private Label fromLabel;
    private Label toLabel;
    private SplitPane splitPane;
    private Label statusLabel;
    private Button refreshButton;
    private TextField searchField;

    // Store original HTML content for restoring images
    private String originalHtmlContent;

    private List<EmailAccount> accounts;
    private ObservableList<EmailAccount> accountsObservable = FXCollections.observableArrayList();
    private FilteredList<EmailAccount> filteredAccounts;
    private ObservableList<EmailMessage> emails = FXCollections.observableArrayList();
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("EmailClient - Made by TOKENU.NET");

        // Create the main layout
        BorderPane mainLayout = new BorderPane();

        // Create the left panel with account list
        VBox leftPanel = createLeftPanel();
        mainLayout.setLeft(leftPanel);

        // Create the center panel with email list and content
        SplitPane centerPanel = createCenterPanel();
        mainLayout.setCenter(centerPanel);

        // Create the bottom panel with status bar
        HBox bottomPanel = createBottomPanel();
        mainLayout.setBottom(bottomPanel);

        // Load email accounts
        loadEmailAccounts();

        Scene scene = new Scene(mainLayout, 1000, 600);
        primaryStage.setScene(scene);

        // Set application icon
        Image icon = new Image(getClass().getResourceAsStream("/net/tokenu/mail/outlook-icon.png"));
        primaryStage.getIcons().add(icon);

        primaryStage.show();
    }

    private VBox createLeftPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(200);

        Label accountsLabel = new Label("Email Accounts");

        // Create search field
        searchField = new TextField();
        searchField.setPromptText("Search email addresses...");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filterAccountsList(newValue);
        });

        // Initialize the accounts list view
        accountListView = new ListView<>();
        accountListView.setPrefHeight(Integer.MAX_VALUE);
        accountListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                loadEmails(newVal);
            }
        });

        // Create context menu for right-click functionality
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyEmailItem = new MenuItem("Copy Email Address");
        copyEmailItem.setOnAction(event -> {
            EmailAccount selectedAccount = accountListView.getSelectionModel().getSelectedItem();
            if (selectedAccount != null) {
                // Copy email address to clipboard
                final Clipboard clipboard = Clipboard.getSystemClipboard();
                final ClipboardContent content = new ClipboardContent();
                content.putString(selectedAccount.getEmail());
                clipboard.setContent(content);

                // Update status to provide feedback
                statusLabel.setText("Email address copied to clipboard: " + selectedAccount.getEmail());
            }
        });
        contextMenu.getItems().add(copyEmailItem);

        // Set the context menu on the ListView
        accountListView.setContextMenu(contextMenu);

        panel.getChildren().addAll(accountsLabel, searchField, accountListView);
        VBox.setVgrow(accountListView, Priority.ALWAYS);

        return panel;
    }

    private SplitPane createCenterPanel() {
        splitPane = new SplitPane();

        // Email list table
        emailTableView = new TableView<>();
        emailTableView.setPlaceholder(new Label("Select an account to view emails"));

        TableColumn<EmailMessage, String> subjectCol = new TableColumn<>("Subject");
        subjectCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getSubject()));
        subjectCol.setPrefWidth(200);

        TableColumn<EmailMessage, String> senderCol = new TableColumn<>("From");
        senderCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getFrom()));
        senderCol.setPrefWidth(150);

        TableColumn<EmailMessage, String> previewCol = new TableColumn<>("Preview");
        previewCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPreview()));
        previewCol.setPrefWidth(150);

        emailTableView.getColumns().addAll(subjectCol, senderCol, previewCol);
        emailTableView.setItems(emails);

        emailTableView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                displayEmailContent(newVal);
            }
        });

        // Add mouse click handler to handle clicks on already selected emails
        emailTableView.setOnMouseClicked(event -> {
            EmailMessage selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
            if (selectedEmail != null) {
                displayEmailContent(selectedEmail);
            }
        });

        // Email content view (initially not added to the splitPane)
        emailContentView = new WebView();

        // Setup context menu for copying HTML content
        setupWebViewContextMenu(emailContentView);

        // Create a BorderPane to hold the email content view and close button
        emailContentPane = new BorderPane();
        emailContentPane.setCenter(emailContentView);

        // Create labels for subject, from, to, and received date
        subjectLabel = new Label();
        subjectLabel.setStyle("-fx-font-weight: bold;");
        fromLabel = new Label();
        toLabel = new Label();
        receivedDateLabel = new Label();

        // Add context menus for copying values
        setupCopyContextMenu(subjectLabel, "Subject");
        setupCopyContextMenu(fromLabel, "From address");
        setupCopyContextMenu(toLabel, "To address");

        // Create an HBox for fromLabel and toLabel to place them on the same row
        HBox addressBox = new HBox(10); // 10 pixels spacing between labels
        addressBox.getChildren().addAll(fromLabel, toLabel);

        // Create a container for the labels (left side)
        VBox labelsBox = new VBox(5);
        labelsBox.getChildren().addAll(subjectLabel, addressBox, receivedDateLabel);
        labelsBox.setPadding(new Insets(5));
        HBox.setHgrow(labelsBox, Priority.ALWAYS);

        // Create a close button in the top-right corner
        closeButton = new Button("Close");
        closeButton.setOnAction(e -> {
            // Remove the email content pane from the split pane
            splitPane.getItems().remove(emailContentPane);
        });

        // Create a load image button
        loadImageButton = new Button("Load Images");
        loadImageButton.setOnAction(e -> {
            // Restore original HTML content with images
            if (originalHtmlContent != null) {
                emailContentView.getEngine().loadContent(originalHtmlContent, "text/html");
                loadImageButton.setDisable(true); // Disable button after loading images
            }
        });

        Tooltip tooltip = new Tooltip(
                //"This message contains remote content."
                //"Image has not been loaded in order to protect your privacy."
                "Tracker protection prevented some images from loading. Load them if you trust the sender."
        );
        tooltip.setShowDelay(Duration.millis(0));

        loadImageButton.setTooltip(tooltip);
        loadImageButton.setDisable(true); // Initially disabled until email with images is loaded

        // Create delete button with trash icon
        ImageView trashIcon = new ImageView(new Image(getClass().getResourceAsStream("/net/tokenu/mail/trash.png")));
        trashIcon.setFitHeight(16);
        trashIcon.setFitWidth(16);
        deleteButton = new Button();
        deleteButton.setGraphic(trashIcon);
        deleteButton.setTooltip(new Tooltip("Delete"));
        deleteButton.setOnAction(e -> {
            EmailMessage selectedEmail = emailTableView.getSelectionModel().getSelectedItem();
            if (selectedEmail != null) {
                deleteEmail(selectedEmail);
            }
        });

        // Create an HBox for deleteButton and loadImageButton
        HBox actionButtonsBox = new HBox(10); // 10 pixels spacing between buttons
        actionButtonsBox.getChildren().addAll(loadImageButton, deleteButton);

        // Add the actionButtonsBox to the labelsBox
        labelsBox.getChildren().add(actionButtonsBox);

        // Create a VBox to hold the close button
        VBox buttonsVBox = new VBox(18); // 18 pixels spacing between buttons
        buttonsVBox.getChildren().addAll(closeButton);
        buttonsVBox.setAlignment(Pos.TOP_RIGHT);

        // Create a container for the labels and buttons
        HBox buttonBox = new HBox(10); // 10 pixels spacing between components
        buttonBox.getChildren().addAll(labelsBox, buttonsVBox);
        buttonBox.setPadding(new Insets(5));
        buttonBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        emailContentPane.setTop(buttonBox);

        // Initially only add the email table to the split pane
        splitPane.getItems().add(emailTableView);

        return splitPane;
    }

    private HBox createBottomPanel() {
        HBox panel = new HBox(10);
        panel.setPadding(new Insets(5));

        statusLabel = new Label("Ready");
        refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> {
            EmailAccount selectedAccount = accountListView.getSelectionModel().getSelectedItem();
            if (selectedAccount != null) {
                loadEmails(selectedAccount);
            }
        });

        panel.getChildren().addAll(statusLabel, new Pane(), refreshButton);
        HBox.setHgrow(panel.getChildren().get(1), Priority.ALWAYS);

        return panel;
    }

    private void loadEmailAccounts() {
        statusLabel.setText("Loading email accounts...");

        executorService.submit(() -> {
            try {
                accounts = Microsoft.loadEmailAccounts();

                Platform.runLater(() -> {
                    // Create observable list from accounts
                    accountsObservable.clear();
                    accountsObservable.addAll(accounts);

                    // Initialize filtered list if not already done
                    if (filteredAccounts == null) {
                        filteredAccounts = new FilteredList<>(accountsObservable, p -> true);
                        accountListView.setItems(filteredAccounts);
                    }

                    // Don't automatically select the first account
                    // Let the user explicitly select an account
                    statusLabel.setText("Email accounts loaded: " + accounts.size() + ". Please select an account to view emails.");
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error loading accounts: " + e.getMessage());
                    ThrowableUtil.println(e);
                });
            }
        });
    }

    private void loadEmails(EmailAccount account) {
        statusLabel.setText("Loading emails for " + account.getEmail() + "...");
        refreshButton.setDisable(true);
        emails.clear();

        // Reset placeholder text to loading message
        emailTableView.setPlaceholder(new Label("Loading emails for " + account.getEmail() + "..."));

        executorService.submit(() -> {
            try {
                if (Microsoft.ensureValidAccessToken(account)) {
                    List<EmailMessage> messages;
                    if (account.getType().equals(AuthType.GRAPH)) {
                        try {
                            messages = Microsoft.getInboxMessagesGraphAPI(account.getAccessToken());
                        }
                        catch (InvalidAuthenticationToken e) {
                            messages = Microsoft.getInboxMessagesIMAPOAuth(account.getEmail(), account.getAccessToken());
                            account.setType(AuthType.IMAP_OAUTH);
                        }
                    }
                    else if (account.getType().equals(AuthType.IMAP_OAUTH)) {
                        messages = Microsoft.getInboxMessagesIMAPOAuth(account.getEmail(), account.getAccessToken());
                    }
                    else {// account.getType().equals(AuthType.IMAP_BASIC)
                        messages = Microsoft.getInboxMessagesIMAPBasic(account.getEmail(), account.getPassword());
                    }

                    List<EmailMessage> finalMessages = messages;
                    Platform.runLater(() -> {
                        emails.addAll(finalMessages);
                        statusLabel.setText("Loaded " + finalMessages.size() + " emails for " + account.getEmail());
                        refreshButton.setDisable(false);

                        // Update placeholder text if no emails are found
                        if (finalMessages.isEmpty()) {
                            emailTableView.setPlaceholder(new Label("No emails found in inbox for " + account.getEmail()));
                        }
                    });

                    // Save updated tokens
                    Microsoft.saveEmailAccounts(accounts);
                } else {
                    Platform.runLater(() -> {
                        statusLabel.setText("Failed to authenticate account: " + account.getEmail());
                        refreshButton.setDisable(false);
                    });
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    statusLabel.setText("Error loading emails: " + e.getMessage());
                    refreshButton.setDisable(false);
                    ThrowableUtil.println(e);
                });
            }
        });
    }

    private void displayEmailContent(EmailMessage email) {
        // Reset state for new email
        originalHtmlContent = null;
        loadImageButton.setDisable(true);

        // Set subject, from, to, and received date labels
        subjectLabel.setText(email.getSubject());
        fromLabel.setText("From: " + email.getFrom());
        toLabel.setText("To: " + email.getTo());

        // Set received date label
        String receivedDateTime = email.getReceivedDateTime();
        if (receivedDateTime != null && !receivedDateTime.isEmpty()) {
            // Format the date if needed (it's already a string in this case)
            receivedDateLabel.setText("Date: " + receivedDateTime);
        } else {
            receivedDateLabel.setText("Date: Unknown");
        }

        // Show loading indicator
        emailContentView.getEngine().loadContent(
                "<div style='text-align:center; margin-top:50px;font-family:Ubuntu, Helvetica, Arial, sans-serif;'>" +
                        "<h3>Loading content...</h3>" +
                        "</div>",
                "text/html"
        );

        // Add the email content pane to the split pane if it's not already there
        if (!splitPane.getItems().contains(emailContentPane)) {
            splitPane.getItems().add(emailContentPane);
            splitPane.setDividerPositions(0.4); // Set divider position to 40% for email list
        }

        // Load email content in background thread
        executorService.submit(() -> {
            try {
                // Get email content (this will trigger loadBodyIfNeeded)
                String content = email.getBody();
                String contentType = email.getContentType();

                // Prepare final content
                final String finalContent;
                if (content == null || content.isEmpty()) {
                    finalContent = "<p><i>No content available</i></p>";
                    originalHtmlContent = null;
                    Platform.runLater(() -> loadImageButton.setDisable(true));
                }
                else if (contentType.equalsIgnoreCase("html")) {
                    // Store original HTML content for restoring images later
                    originalHtmlContent = content;

                    // Check if content contains images
                    boolean hasImages = content.contains("<img") || content.contains("<image");

                    // Replace images with placeholders
                    finalContent = replaceImagesWithPlaceholders(content);

                    // Enable or disable load images button based on whether content has images
                    Platform.runLater(() -> loadImageButton.setDisable(!hasImages));
                }
                else {
                    // Convert plain text to HTML
                    finalContent = "<pre>" + content.replace("\n", "<br>")
                                                  .replace(" ", "&nbsp;") + "</pre>";
                    originalHtmlContent = null;
                    Platform.runLater(() -> loadImageButton.setDisable(true));
                }

                // Update UI on JavaFX thread
                Platform.runLater(() -> emailContentView.getEngine().loadContent(finalContent, "text/html"));
            } catch (Exception e) {
                // Handle any errors
                Platform.runLater(() -> {
                    emailContentView.getEngine().loadContent("<div style='color:red; margin:20px;'><h3>Error loading content</h3><p>" + e.getMessage() + "</p></div>", "text/html");
                    ThrowableUtil.println(e);
                });
            }
        });
    }

    /**
     * Filters the accounts list based on the search text.
     * 
     * @param searchText The text to filter by
     */
    private void filterAccountsList(String searchText) {
        if (filteredAccounts == null) {
            return; // Not initialized yet
        }

        if (searchText == null || searchText.isEmpty()) {
            // Show all accounts if search text is empty
            filteredAccounts.setPredicate(account -> true);
            statusLabel.setText("Showing all email accounts: " + filteredAccounts.size());
        } else {
            // Filter accounts by email address (case-insensitive)
            filteredAccounts.setPredicate(account -> 
                account.getEmail().toLowerCase().contains(searchText.toLowerCase()));
            statusLabel.setText("Found " + filteredAccounts.size() + " accounts matching '" + searchText + "'");
        }
    }

    @Override
    public void stop() {
        // Close any open IMAP connections
        Microsoft.closeCurrentConnection();

        // Shutdown the executor service
        executorService.shutdown();
    }

    public static void main(String[] args) {
        launch(args);
    }

    /**
     * Sets up a context menu for a label to allow copying its text to clipboard.
     * 
     * @param label The label to add the context menu to
     * @param description Description of what's being copied (for status message)
     */
    private void setupCopyContextMenu(Label label, String description) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyItem = new MenuItem("Copy " + description);
        copyItem.setOnAction(event -> {
            String textToCopy = label.getText();
            // Extract the actual value (remove the prefix like "Subject: ", "From: ", etc.)
            if (textToCopy.contains(": ")) {
                textToCopy = textToCopy.substring(textToCopy.indexOf(": ") + 2);
            }

            // Copy to clipboard
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content = new ClipboardContent();
            content.putString(textToCopy);
            clipboard.setContent(content);

            // Update status to provide feedback
            statusLabel.setText(description + " copied to clipboard: " + textToCopy);
        });
        contextMenu.getItems().add(copyItem);
        label.setContextMenu(contextMenu);
    }

    /**
     * Sets up a context menu for the WebView to allow copying the HTML content to clipboard.
     * 
     * @param webView The WebView to add the context menu to
     */
    private void setupWebViewContextMenu(WebView webView) {
        ContextMenu contextMenu = new ContextMenu();
        MenuItem copyHtmlItem = new MenuItem("Copy HTML Content");
        copyHtmlItem.setOnAction(event -> {
            // Get the HTML content from the WebView
            String htmlContent = (String) webView.getEngine().executeScript(
                    "document.documentElement.outerHTML");

            // Copy to clipboard
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content = new ClipboardContent();
            content.putString(htmlContent);
            clipboard.setContent(content);

            // Update status to provide feedback
            statusLabel.setText("HTML content copied to clipboard");
        });
        contextMenu.getItems().add(copyHtmlItem);

        // Set up the context menu to appear on right-click
        webView.setOnContextMenuRequested(event -> {
            contextMenu.show(webView, event.getScreenX(), event.getScreenY());
        });
    }

    /**
     * Deletes the selected email message.
     * 
     * @param email The email message to delete
     */
    private void deleteEmail(EmailMessage email) {
        // Get the currently selected account
        EmailAccount selectedAccount = accountListView.getSelectionModel().getSelectedItem();
        if (selectedAccount == null) {
            statusLabel.setText("No account selected");
            return;
        }

        // Confirm deletion
        Alert confirmDialog = new Alert(Alert.AlertType.CONFIRMATION);
        //confirmDialog.setTitle("Delete Email");
        confirmDialog.setHeaderText("Delete Email");
        confirmDialog.setContentText("Are you sure you want to delete this email?\n\nSubject: " + email.getSubject());
        confirmDialog.getDialogPane().setGraphic(null);

        confirmDialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Disable the delete button while deleting
                deleteButton.setDisable(true);
                statusLabel.setText("Deleting email...");

                // Delete the email in a background thread
                executorService.submit(() -> {
                    try {
                        // Ensure we have a valid access token
                        if (Microsoft.ensureValidAccessToken(selectedAccount)) {
                            boolean success = Microsoft.deleteEmail(selectedAccount, email.getId());

                            Platform.runLater(() -> {
                                if (success) {
                                    // Remove the email from the list
                                    emails.remove(email);

                                    // Close the email content pane if it's open
                                    if (splitPane.getItems().contains(emailContentPane)) {
                                        splitPane.getItems().remove(emailContentPane);
                                    }

                                    statusLabel.setText("Email deleted successfully");
                                } else {
                                    statusLabel.setText("Failed to delete email");
                                }
                                deleteButton.setDisable(false);
                            });
                        } else {
                            Platform.runLater(() -> {
                                statusLabel.setText("Failed to authenticate account: " + selectedAccount.getEmail());
                                deleteButton.setDisable(false);
                            });
                        }
                    } catch (Exception ex) {
                        Platform.runLater(() -> {
                            statusLabel.setText("Error deleting email: " + ex.getMessage());
                            deleteButton.setDisable(false);
                            ThrowableUtil.println(ex);
                        });
                    }
                });
            }
        });
    }

    /**
     * Replaces all image tags in HTML content with SVG placeholders.
     * 
     * @param htmlContent The original HTML content
     * @return The modified HTML content with image tags replaced by SVG placeholders
     */
    private String replaceImagesWithPlaceholders(String htmlContent) {
        if (htmlContent == null || htmlContent.isEmpty()) {
            return htmlContent;
        }

        // Add CSS styles for tooltip
        String cssStyles = "<style>\n" +
                "table {\n" +
                "    border-collapse: separate;\n" +
                "    text-indent: initial;\n" +
                "    line-height: normal;\n" +
                "    font-weight: normal;\n" +
                "    font-size: medium;\n" +
                "    font-style: normal;\n" +
                "    color: -internal-quirk-inherit;\n" +
                "    text-align: start;\n" +
                "    white-space: normal;\n" +
                "    font-variant: normal;\n" +
                "}" +
                ".image-placeholder {\n" +
                "  position: relative;\n" +
                "  display: inline-block;\n" +
                "}\n" +
                "\n" +
                ".image-placeholder .tooltiptext {\n" +
                "  font-family:Ubuntu, Helvetica, Arial, sans-serif;\n" +
                "  font-size: 11px;\n" +
                "  white-space: normal;\n" +
                //"  min-height: 26px;\n" +
                "  visibility: hidden;\n" +
                //"  width: 300px;\n" +
                "  background-color: rgb(0 0 0 / 80%);\n" +
                "  color: #fff !important;\n" +
                "  text-align: center;\n" +
                "  border-radius: 6px;\n" +
                "  padding: 5px 7px;\n" +
                "  left: 50%;\n" +
                "  transform: translateX(-50%);\n" +
                "  /* Position the tooltip */\n" +
                "  top: 110%;\n" +
                //"  bottom: 110%;\n" +
                "  position: absolute;\n" +
                "  z-index: 999;\n" +
                "}\n" +
                "\n" +
                ".image-placeholder:hover .tooltiptext {\n" +
                "  visibility: visible;\n" +
                "}\n" +
                "</style>";

        // Check if the HTML content has a <head> section
        if (htmlContent.contains("<head>") && htmlContent.contains("</head>")) {
            // Add CSS styles to the head section
            htmlContent = htmlContent.replace("</head>", cssStyles + "</head>");
        } else if (htmlContent.contains("<body>")) {
            // Add CSS styles to the beginning of the body section
            htmlContent = htmlContent.replace("<body>", "<body>" + cssStyles);
        } else {
            // Add CSS styles to the beginning of the HTML content
            htmlContent = cssStyles + htmlContent;
        }

        // SVG placeholder for images
        String svgPlaceholder = "<svg xmlns:xlink=\"http://www.w3.org/1999/xlink\" xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 16 16\" class=\"icon-size-4\" style=\"color: #5c5958;\" role=\"img\" focusable=\"false\" aria-hidden=\"true\" width=\"16\" height=\"16\"><defs><g id=\"ic-file-image\"><path fill-rule=\"evenodd\" d=\"M13 6v7a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1h5v2.5A1.5 1.5 0 0 0 10.5 6H13Zm-.414-1L10 2.414V4.5a.5.5 0 0 0 .5.5h2.086ZM2 3a2 2 0 0 1 2-2h5.172a2 2 0 0 1 1.414.586l2.828 2.828A2 2 0 0 1 14 5.828V13a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V3Zm9.557 9.3c.361 0 .57-.386.358-.663L9.433 8.404a.275.275 0 0 0-.43 0L7.2 10.755 6.195 9.448a.275.275 0 0 0-.43 0l-1.68 2.19c-.212.276-.003.662.358.662h7.114ZM6.8 8.3a.8.8 0 1 0 0-1.6.8.8 0 0 0 0 1.6Z\"></path></g></defs><g><path fill-rule=\"evenodd\" d=\"M13 6v7a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1V3a1 1 0 0 1 1-1h5v2.5A1.5 1.5 0 0 0 10.5 6H13Zm-.414-1L10 2.414V4.5a.5.5 0 0 0 .5.5h2.086ZM2 3a2 2 0 0 1 2-2h5.172a2 2 0 0 1 1.414.586l2.828 2.828A2 2 0 0 1 14 5.828V13a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V3Zm9.557 9.3c.361 0 .57-.386.358-.663L9.433 8.404a.275.275 0 0 0-.43 0L7.2 10.755 6.195 9.448a.275.275 0 0 0-.43 0l-1.68 2.19c-.212.276-.003.662.358.662h7.114ZM6.8 8.3a.8.8 0 1 0 0-1.6.8.8 0 0 0 0 1.6Z\"></path></g></svg>";

        // Regular expression to match image tags and capture width attribute if present
        // This regex matches both <img> and <image> tags with any attributes
        String regex = "<img[^>]*>|<image[^>]*>";

        // Create a pattern and matcher for finding image tags
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher matcher = pattern.matcher(htmlContent);

        StringBuffer result = new StringBuffer();

        while (matcher.find()) {
            String imgTag = matcher.group();
            String width = "auto"; // Default width if not specified
            String minHeight = "25";

            // Try to extract width attribute from the image tag
            java.util.regex.Pattern widthPattern = java.util.regex.Pattern.compile("width=[\"']?(\\d+)[\"']?");
            java.util.regex.Matcher widthMatcher = widthPattern.matcher(imgTag);

            if (widthMatcher.find()) {
                width = widthMatcher.group(1);
            }

            if (width.equals("1")) width = "auto";
            if (!width.equals("auto") && Integer.parseInt(width)>=64) minHeight = "64";

            // Create the placeholder with the extracted width
            String placeholder = "<span class=\"image-placeholder\" style=\"min-height: " + minHeight + "px;min-width: 25px;height: auto;line-height: 100%;max-width: 100%;text-decoration: none;width: " + width + "px;background-color: rgba(0, 0, 0, .0666666667);box-sizing: border-box;display: inline-flex;border-radius: calc(0.5 * 8 * 0.0625rem);justify-content: center;align-items: center;color: #5c5958;margin-block-end: 7px;margin-inline-end: 7px;\">" +
                    svgPlaceholder +
                    "<span class=\"tooltiptext\">" +
                    //"Tracker protection prevented some images from loading. Load them if you trust the sender." +
                    "<nobr>Image has not been loaded in order to protect your privacy." +
                    "</span>" +
                    "</span>";

            // Replace the current match with the placeholder
            matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(placeholder));
        }

        matcher.appendTail(result);
        return result.toString();
    }
}
