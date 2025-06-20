package net.tokenu.mail.service;
import com.commons.ThrowableUtil;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeUtility;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MailContentExtractor {
    public static String decodeMimeHeader(String header) {
        if (!header.contains("=?")) return header;

        try {
            Pattern p = Pattern.compile("=\\?[^?]+\\?[QqBb]\\?[^?]*\\?=");
            Matcher m = p.matcher(header);
            StringBuilder decoded = new StringBuilder();
            int lastEnd = 0;

            while (m.find()) {
                // Append any plain text before this encoded section
                decoded.append(header, lastEnd, m.start());
                // Decode this encoded section
                decoded.append(MimeUtility.decodeText(m.group()));
                lastEnd = m.end();
            }

            // Append any remaining plain text
            decoded.append(header.substring(lastEnd));
            return decoded.toString();
        }
        catch (UnsupportedEncodingException e) {
            ThrowableUtil.println(e);
            return header;
        }
    }

    /**
     * Extracts HTML content from a JavaMail Message.
     * Handles both simple messages and multipart messages.
     *
     * @param message the JavaMail Message
     * @return HTML content as String, or null if no HTML content found
     * @throws MessagingException if there's an error accessing the message
     * @throws IOException if there's an error reading the content
     */
    public static String getHtmlContent(Message message) throws MessagingException, IOException {
        return extractHtmlContent(message);
    }

    /**
     * Recursively extracts HTML content from a Part (Message or BodyPart).
     *
     * @param part the Part to extract from
     * @return HTML content as String, or null if no HTML content found
     * @throws MessagingException if there's an error accessing the part
     * @throws IOException if there's an error reading the content
     */
    private static String extractHtmlContent(Part part) throws MessagingException, IOException {
        String contentType = part.getContentType().toLowerCase();

        // Direct HTML content
        if (contentType.startsWith("text/html")) {
            return (String) part.getContent();
        }

        // Multipart content - recursively search
        if (contentType.startsWith("multipart/")) {
            Multipart multipart = (Multipart) part.getContent();

            // For multipart/alternative, prefer HTML over plain text
            if (contentType.startsWith("multipart/alternative")) {
                return getHtmlFromAlternative(multipart);
            }

            // For other multipart types, search all parts
            for (int i = 0; i < multipart.getCount(); i++) {
                MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(i);
                String htmlContent = extractHtmlContent(bodyPart);
                if (htmlContent != null) {
                    return htmlContent;
                }
            }
        }

        return null;
    }

    /**
     * Handles multipart/alternative by preferring HTML content over plain text.
     *
     * @param multipart the multipart/alternative content
     * @return HTML content if found, otherwise null
     * @throws MessagingException if there's an error accessing the multipart
     * @throws IOException if there's an error reading the content
     */
    private static String getHtmlFromAlternative(Multipart multipart) throws MessagingException, IOException {
        String htmlContent = null;

        for (int i = 0; i < multipart.getCount(); i++) {
            MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(i);
            String partContentType = bodyPart.getContentType().toLowerCase();

            if (partContentType.startsWith("text/html")) {
                return (String) bodyPart.getContent();
            } else if (partContentType.startsWith("multipart/")) {
                // Recursively handle nested multipart
                String nestedHtml = extractHtmlContent(bodyPart);
                if (nestedHtml != null) {
                    htmlContent = nestedHtml;
                }
            }
        }

        return htmlContent;
    }

    /**
     * Utility method that also extracts plain text as fallback.
     * Returns HTML if available, otherwise plain text.
     *
     * @param message the JavaMail Message
     * @return HTML content if found, plain text as fallback, or null if neither found
     * @throws MessagingException if there's an error accessing the message
     * @throws IOException if there's an error reading the content
     */
    public static String getContentWithFallback(Message message) throws MessagingException, IOException {
        String htmlContent = getHtmlContent(message);
        if (htmlContent != null) {
            return htmlContent;
        }

        // Fallback to plain text
        return getPlainTextContent(message);
    }

    /**
     * Extracts plain text content from a Message.
     *
     * @param message the JavaMail Message
     * @return plain text content, or null if not found
     * @throws MessagingException if there's an error accessing the message
     * @throws IOException if there's an error reading the content
     */
    public static String getPlainTextContent(Message message) throws MessagingException, IOException {
        return extractPlainTextContent(message);
    }

    private static String extractPlainTextContent(Part part) throws MessagingException, IOException {
        String contentType = part.getContentType().toLowerCase();

        if (contentType.startsWith("text/plain")) {
            return (String) part.getContent();
        }

        if (contentType.startsWith("multipart/")) {
            Multipart multipart = (Multipart) part.getContent();

            for (int i = 0; i < multipart.getCount(); i++) {
                MimeBodyPart bodyPart = (MimeBodyPart) multipart.getBodyPart(i);
                String textContent = extractPlainTextContent(bodyPart);
                if (textContent != null) {
                    return textContent;
                }
            }
        }

        return null;
    }
}