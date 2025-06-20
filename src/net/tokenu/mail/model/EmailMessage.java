package net.tokenu.mail.model;

import com.commons.FileUtil;
import com.commons.LogUtil;
import com.commons.ThrowableUtil;
import net.tokenu.mail.service.MailContentExtractor;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.mail.*;
import javax.mail.FolderClosedException;
import javax.mail.internet.MimeBodyPart;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.tokenu.mail.service.Microsoft.lazyLoad;

/**
 * Represents an email message with basic information.
 */
public class EmailMessage {
    private String id;
    private String subject;
    private String from;
    private String to;
    private String preview;
    private String body;
    private String contentType;//html, text
    private String receivedDateTime;

    // Fields for lazy loading IMAP messages
    private Message originalMessage;
    private AtomicBoolean bodyLoaded = new AtomicBoolean(true); // Default to true for non-IMAP messages
    private boolean isImapMessage = false;

    /**
     * Creates an EmailMessage from a JSONObject returned by Microsoft Graph API.
     * 
     * @param jsonObject The JSONObject containing email data
     * @return A new EmailMessage instance
     */
    public static EmailMessage fromJson(JSONObject jsonObject) {
        EmailMessage message = new EmailMessage();

        message.id = jsonObject.optString("id", "");
        message.subject = jsonObject.optString("subject", "No Subject");
        message.preview = jsonObject.optString("bodyPreview", "");
        message.receivedDateTime = jsonObject.optString("receivedDateTime", "");

        JSONObject from = jsonObject.optJSONObject("from");
        if (from != null) {
            JSONObject emailAddress = from.optJSONObject("emailAddress");
            if (emailAddress != null) {
                message.from = emailAddress.optString("address", "Unknown");
            } else {
                message.from = "Unknown";
            }
        } else {
            message.from = "Unknown";
        }

        JSONArray to = jsonObject.optJSONArray("toRecipients");
        if (to != null) {
            JSONObject emailAddress = to.getJSONObject(0).optJSONObject("emailAddress");
            if (emailAddress != null) {
                message.to = emailAddress.optString("address", "Unknown");
            } else {
                message.to = "Unknown";
            }
        } else {
            message.to = "Unknown";
        }

        JSONObject bodyObj = jsonObject.optJSONObject("body");
        if (bodyObj != null) {
            message.contentType = bodyObj.optString("contentType", "text");
            message.body = bodyObj.optString("content", "");
        } else {
            message.contentType = "text";
            message.body = "";
        }

        if (message.preview.contains("\n")||message.preview.contains("\r")) {
            message.preview = message.preview.replaceAll("[\\n\\r]+", "⏎");
            message.preview = message.preview.replaceAll("⏎+", "⏎");
            if (message.preview.startsWith("⏎"))
                message.preview = message.preview.replaceAll("^⏎+", "");
        }

        return message;
    }
    public static EmailMessage fromIMAP(Message message, boolean lazyLoad) {
        EmailMessage emailMessage = new EmailMessage();

        try {
            // Mark as IMAP message
            emailMessage.isImapMessage = true;

            // Store original message for lazy loading if needed
            if (lazyLoad) {
                emailMessage.originalMessage = message;
                emailMessage.bodyLoaded.set(false);
            }

            // Extract message ID (may not be available in all messages)
            emailMessage.id = message.getHeader("Message-ID") != null ?
                    message.getHeader("Message-ID")[0] : String.valueOf(message.getMessageNumber());

            // Extract subject
            emailMessage.subject = message.getSubject() != null ? message.getSubject() : "No Subject";

            // Extract received date
            emailMessage.receivedDateTime = message.getReceivedDate().toString();

            // Extract sender
            Address[] fromAddresses = message.getFrom();
            if (fromAddresses != null && fromAddresses.length > 0) {
                emailMessage.from = fromAddresses[0].toString().replaceAll(".+ <|>$", "");
            } else {
                emailMessage.from = "Unknown";
            }

            // Extract sender
            Address[] toAddresses = message.getAllRecipients();
            if (toAddresses != null && toAddresses.length > 0) {
                emailMessage.to = toAddresses[0].toString().replaceAll(".+ <|>$", "");
            } else {
                emailMessage.to = "Unknown";
            }

            // If lazy loading is enabled, only set placeholder values for body and preview
            if (lazyLoad) {
                emailMessage.contentType = "text"; // Default, will be updated when loaded
                emailMessage.body = "Loading content..."; // Will be loaded on demand
                emailMessage.preview = "Click to load content..."; // Placeholder
            } else {
                // Extract body and preview immediately (original behavior)
                try {
                    // preview (plain text content)
                    String textContent = MailContentExtractor.getPlainTextContent(message);

                    // body (HTML content) if null use (plain text content)
                    String htmlContent = MailContentExtractor.getHtmlContent(message);
                    emailMessage.body = htmlContent != null ? htmlContent : textContent;

                    // Set content type based on whether HTML content is available
                    emailMessage.contentType = htmlContent != null ? "html" : "text";

                    // Create a preview (first % characters or less)
                    int characters = 200;
                    if (textContent != null && !textContent.isEmpty()) {
                        emailMessage.preview = textContent.length() > characters ?
                                textContent.substring(0, characters) + "..." : textContent;
                        if (emailMessage.preview.startsWith("------")) {
                            emailMessage.preview = emailMessage.preview.replaceAll("--+", "");
                        }
                        if (emailMessage.preview.contains("\n")||emailMessage.preview.contains("\r")) {
                            emailMessage.preview = emailMessage.preview.replaceAll("[\\n\\r]+", "⏎");
                            emailMessage.preview = emailMessage.preview.replaceAll("⏎+", "⏎");
                            if (emailMessage.preview.startsWith("⏎"))
                                emailMessage.preview = emailMessage.preview.replaceAll("^⏎+", "");
                        }
                    } else {
                        emailMessage.preview = "No preview available";
                    }
                } catch (Exception e) {
                    ThrowableUtil.println(e);
                    emailMessage.body = "Error retrieving message content";
                    emailMessage.preview = "Error retrieving message content";
                }
            }
        } catch (MessagingException e) {
            ThrowableUtil.println(e);
        }

        return emailMessage;
    }

    // For backward compatibility
    public static EmailMessage fromIMAP(Message message) {
        return fromIMAP(message, lazyLoad);
    }

    // Getters
    public String getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getPreview() {
        //loadBodyIfNeeded();
        return preview;
    }

    public String getBody() {
        loadBodyIfNeeded();
        return body;
    }

    public String getContentType() {
        loadBodyIfNeeded();
        return contentType;
    }

    /**
     * Loads the body and preview content if they haven't been loaded yet.
     * This is used for lazy loading IMAP messages.
     */
    private synchronized void loadBodyIfNeeded() {
        // If this is not an IMAP message or the body is already loaded, do nothing
        if (!isImapMessage || bodyLoaded.get()) {
            return;
        }

        // Try to load the body and preview
        try {
            if (originalMessage != null) {
                System.out.println("Loading body: " + getSubject());

                // Use getText for preview (plain text content)
                String textContent = getText(originalMessage.getContent());

                // Use getHtml for body (HTML content) if null use (plain text content)
                String htmlContent = getHtml(originalMessage.getContent());
                body = htmlContent != null ? htmlContent : textContent;

                // Set content type based on whether HTML content is available
                contentType = htmlContent != null ? "html" : "text";

                // Create a preview (first % characters or less)
                int characters = 200;
                if (textContent != null && !textContent.isEmpty()) {
                    preview = textContent.length() > characters ?
                            textContent.substring(0, characters) + "..." : textContent;
                    if (preview.startsWith("------")) {
                        preview = preview.replaceAll("--+", "");
                    }
                    if (preview.contains("\n") || preview.contains("\r")) {
                        preview = preview.replaceAll("[\\n\\r]+", "⏎");
                        preview = preview.replaceAll("⏎+", "⏎");
                        if (preview.startsWith("⏎"))
                            preview = preview.replaceAll("^⏎+", "");
                    }
                } else {
                    preview = "No preview available";
                }

                // Mark as loaded
                bodyLoaded.set(true);

                // Clear the reference to the original message to free memory
                originalMessage = null;
            }
        } catch (FolderClosedException e) {
            LogUtil.warning("Folder closed, cannot load message content: " + e.getMessage());
            body = "<p><i>Cannot load content - the email connection was closed. Please refresh or select a different account.</i></p>";
            preview = "Content unavailable - folder closed";
            contentType = "html";
            bodyLoaded.set(true); // Mark as loaded to prevent repeated attempts
            originalMessage = null; // Clear reference to avoid further attempts
        } catch (Exception e) {
            ThrowableUtil.println(e);
            body = "Error retrieving message content";
            preview = "Error retrieving message content";
            bodyLoaded.set(true); // Mark as loaded to prevent repeated attempts
            originalMessage = null; // Clear reference to avoid further attempts
        }
    }

    public String getReceivedDateTime() {
        return receivedDateTime;
    }

    @Override
    public String toString() {
        loadBodyIfNeeded();
        return "Subject: " + subject + "\nFrom: " + from + "\nPreview: " + preview;
    }
}
