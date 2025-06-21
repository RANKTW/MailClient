package net.tokenu.mail.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.commons.*;
import com.commons.Timer;
import com.commons.http.Headers;
import com.commons.http.HttpClient;
import com.commons.http.ProxyUtil;
import com.commons.http.ResponseContent;
import com.commons.exception.ConnectException;
import com.commons.json.JsonObjectUtil;
import com.commons.json.JsonUtil;
import net.tokenu.mail.Main;
import net.tokenu.mail.model.EmailAccount;
import net.tokenu.mail.model.EmailMessage;
import net.tokenu.mail.util.AuthType;
import net.tokenu.mail.util.Format;
import net.tokenu.mail.util.InvalidAuthenticationToken;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.mail.*;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class Microsoft {
    public static boolean multipleThreaded = false;
    public static boolean lazyLoad = true;
    public static int IMAP_MAXIMUM_LOAD_MESSAGE = 5;
    public static ProxyUtil IMAP_PROXY;

    public static String fileName = "emails.txt";
    public static String hosts = "hosts.json";
    public static Format formatType;

    // Store the current open folder and store for IMAP
    private static Folder currentFolder;
    private static Store currentStore;
    private static String currentEmail;

    public static void main(String[] args) throws Exception {
        List<EmailAccount> accounts = loadEmailAccounts();

        AtomicInteger index = new AtomicInteger();
        for (EmailAccount account : accounts) {
            try {
                LogUtil.log("> "+account.getEmail());
                if (ensureValidAccessToken(account)) {
                    List<EmailMessage> messages;

                    if (account.getType().equals(AuthType.GRAPH)) {
                        try {
                            messages = getInboxMessagesGraphAPI(account.getAccessToken());
                        }
                        catch (InvalidAuthenticationToken e) {
                            messages = getInboxMessagesIMAPOAuth(account.getEmail(), account.getAccessToken());
                            account.setType(AuthType.IMAP_OAUTH);
                        }
                    }
                    else if (account.getType().equals(AuthType.IMAP_OAUTH)) {
                        messages = getInboxMessagesIMAPOAuth(account.getEmail(), account.getAccessToken());
                    }
                    else {// account.getType().equals(AuthType.IMAP_BASIC)
                        messages = getInboxMessagesIMAPBasic(account.getEmail(), account.getPassword());
                    }

                    // Print messages to console (for backward compatibility)
                    for (EmailMessage message : messages) {
                        System.out.println(message);
                        System.out.println("----------------------------------------");
                    }

                    LogUtil.successful(String.format("[%d] %s", index.incrementAndGet(), account));
                    continue;
                }
            } catch (Throwable e) {
                ThrowableUtil.println(e);
            }
            LogUtil.error(String.format("[%d] %s", index.incrementAndGet(), account));
            FileUtil.save(account.toJson().toString(), "invalid.txt");
        }

        saveEmailAccounts(accounts);
    }

    private static JsonArray loadFile(){
        JsonArray array = new JsonArray();
        try {
            String content = FileUtil.readString(fileName);
            if (content.isEmpty()) return array;

            if (content.startsWith("[") && content.endsWith("]")) {
                formatType = Format.ARRAY;
                Arrays.stream(content.split("\n"))
                        .distinct()
                        .forEach(s -> {
                            try {
                                array.addAll(JsonUtil.parseJsonArray(s));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
            else if (content.startsWith("{") && content.endsWith("}")) {
                formatType = Format.OBJECT_LIST;
                Arrays.stream(content.split("\n"))
                        .distinct()
                        .forEach(s -> {
                            try {
                                array.add(JsonUtil.parseJsonObject(s));
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
            else if (content.contains(":")) {
                formatType = Format.COLON_SEPARATED;
                Arrays.stream(content.split("\n"))
                        .distinct()
                        .forEach(s -> {
                            try {
                                String[] email = s.split(":");
                                if (email.length == 2) {
                                    array.add(
                                            JsonObjectUtil.create()
                                                    .add("email", email[0])
                                                    .add("password", email[1])
                                                    .build()
                                    );
                                }
                                else {
                                    array.add(
                                            JsonObjectUtil.create()
                                                    .add("email", email[0])
                                                    .add("password", email[1])
                                                    .add("clientId", email[3])
                                                    .add("refreshToken", email[2])
                                                    .build()
                                    );
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
            else if (content.contains("----")) {
                formatType = Format.DASH_SEPARATED;
                Arrays.stream(content.split("\n"))
                        .distinct()
                        .forEach(s -> {
                            try {
                                String[] email = s.split("----");
                                if (email.length == 2) {
                                    array.add(
                                            JsonObjectUtil.create()
                                                    .add("email", email[0])
                                                    .add("password", email[1])
                                                    .build()
                                    );
                                }
                                else {
                                    array.add(
                                            JsonObjectUtil.create()
                                                    .add("email", email[0])
                                                    .add("password", email[1])
                                                    .add("clientId", email[2])
                                                    .add("refreshToken", email[3])
                                                    .build()
                                    );
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        });
            }
            else {
                throw new RuntimeException("Unsupported format");
            }
        }
        catch (Exception e) {
            ThrowableUtil.println(e);
            System.exit(1);
        }
        return array;
    }
    /**
     * Loads email accounts from the emails.txt file.
     *
     * @return A list of EmailAccount objects
     */
    public static List<EmailAccount> loadEmailAccounts() {
        List<EmailAccount> accounts = new ArrayList<>();
        List<EmailAccount> duplicates = new ArrayList<>();
        JsonArray array = loadFile();

        try {
            for (int i = 0; i < array.size(); i++) {
                JsonObject jsonObj = array.get(i).getAsJsonObject();
                EmailAccount account = EmailAccount.fromJson(jsonObj);
                if (accounts.contains(account)) {
                    duplicates.add(account);
                    continue;
                }
                accounts.add(account);
            }
        }
        catch (Exception e) {
            ThrowableUtil.println(e);
        }

        if (!duplicates.isEmpty()) {
            LogUtil.error("Found " + duplicates.size() + " duplicate email accounts");
        }

        return accounts;
    }

    /**
     * Saves email accounts to the emails.txt file.
     *
     * @param accounts The list of EmailAccount objects
     */
    public static void saveEmailAccounts(List<EmailAccount> accounts) {
        if (accounts.isEmpty()) return;
        if (accounts.stream().findFirst().get().getType().equals(AuthType.IMAP_BASIC)) return;
        LogUtil.log("Saving email accounts...");
        try {
            JsonArray array = loadFile();

            // Create a map of email addresses to JsonObjects from the file
            Map<String, JsonObject> emailToJsonMap = new HashMap<>();
            for (int i = 0; i < array.size(); i++) {
                JsonObject jsonObj = array.get(i).getAsJsonObject();
                String email = EmailAccount.getJsonValueCaseInsensitive(jsonObj, "email");
                if (emailToJsonMap.containsKey(email)) continue;
                emailToJsonMap.put(email, jsonObj);
            }

            // Update JsonObjects with account data
            for (EmailAccount account : accounts) {
                JsonObject jsonObj = emailToJsonMap.get(account.getEmail());
                if (jsonObj != null) {
                    account.updateJsonObject(jsonObj);
                }
            }

            // Remove duplicate email entries from array 
            Set<String> seenEmails = new HashSet<>();
            for (int i = 0; i < array.size(); i++) {
                String email = EmailAccount.getJsonValueCaseInsensitive(array.get(i).getAsJsonObject(), "email");
                if (!seenEmails.add(email)) {
                    array.remove(i);
                    i--;
                }
            }

            FileUtil.write(JsonUtil.getJsonArrayToObjectList(array)
                    .stream()
                    .map(JsonElement::toString)
                    .collect(Collectors.toList()),
                    fileName);
        }
        catch (Exception e) {
            ThrowableUtil.println(e);
        }
    }

    /**
     * Ensures the account has a valid access token, refreshing it if necessary.
     *
     * @param account The EmailAccount to validate
     * @return true if the account has a valid access token, false otherwise
     */
    public static boolean ensureValidAccessToken(EmailAccount account) {
        try {
            if (!account.hasValidAccessToken()) {
                JSONObject jsonResponse = getAccessToken(account.getClientId(), account.getRefreshToken());
                String refreshToken = jsonResponse.getString("refresh_token");
                String accessToken = jsonResponse.getString("access_token");
                long expires_in = jsonResponse.getLong("expires_in");
                String scope = jsonResponse.getString("scope");
                // "scope": "https://graph.microsoft.com/Mail.ReadWrite",
                // "scope": "https://outlook.office.com/IMAP.AccessAsUser.All https://outlook.office.com/POP.AccessAsUser.All https://outlook.office.com/EWS.AccessAsUser.All https://outlook.office.com/SMTP.Send",
                AuthType type = scope.contains("graph") ? AuthType.GRAPH : AuthType.IMAP_OAUTH;
                account.updateAccessToken(refreshToken, accessToken, expires_in, type);
                return true;
            }
            return true;
        } catch (NullPointerException e) {
            ThrowableUtil.println(e);
            return false;
        } catch (Exception e) {
            LogUtil.error(ThrowableUtil.getString(e)+"   "+account.getEmail());
            return false;
        }
    }

    // GraphAPI or IMAP
    public static JSONObject getAccessToken(String clientId, String refreshToken) throws Exception {
        String tokenUrl = "https://login.microsoftonline.com/common/oauth2/v2.0/token";

        String data = "client_id=" + URLEncoder.encode(clientId, "UTF-8") +
                "&grant_type=refresh_token" +
                "&refresh_token=" + URLEncoder.encode(refreshToken, "UTF-8");

        HttpClient client = Main.proxies.isEmpty() ? HttpClient.create()
                : HttpClient.proxy(ProxyUtil.http(Main.proxies.pick()));

        ResponseContent response = client
                .setKeepAlive(false)
                .postRequest(tokenUrl,
                        data,
                        Headers.create()
                                .setContentType("application/x-www-form-urlencoded")
                                .get());
        LogUtil.log(response.printMinimum());

        // Parse JSON response
        JSONObject jsonResponse = new JSONObject(response.getContent());
        if (!jsonResponse.has("access_token")) {
            String error = response.getContent();
            if (jsonResponse.has("error_description"))
                error = jsonResponse.getString("error_description");
            if (error.contains("Trace ID")) error = error.replaceAll(" Trace ID: .+", "");
            throw new AuthenticationFailedException(error);
        }

        return jsonResponse;
    }

    // GraphAPI
    public static List<EmailMessage> getInboxMessagesGraphAPI(String accessToken) throws Exception {
        String tokenUrl = "https://graph.microsoft.com/v1.0/me/mailfolders/inbox/messages";
        List<EmailMessage> emailMessages = new ArrayList<>();

        HttpClient client = Main.proxies.isEmpty() ? HttpClient.create()
                : HttpClient.proxy(ProxyUtil.http(Main.proxies.pick()));

        ResponseContent response = client
                .setKeepAlive(false)
                .getRequest(tokenUrl,
                        Headers.create()
                                .setAuthorization("Bearer " + accessToken)
                                .setDefaultContentType()
                                .get());

        System.out.println(response.printInfo());

        /*
        {
            "error":
            {
                "code": "InvalidAuthenticationToken",
                "message": "IDX14100: JWT is not well formed, there are no dots (.).\nThe token needs to be in JWS or JWE Compact Serialization Format. (JWS): 'EncodedHeader.EncodedPayload.EncodedSignature'. (JWE): 'EncodedProtectedHeader.EncodedEncryptedKey.EncodedInitializationVector.EncodedCiphertext.EncodedAuthenticationTag'.",
                "innerError":
                {
                    "date": "yyyy-MM-ddThh:mm:ss",
                    "request-id": "",
                    "client-request-id": ""
                }
            }
        }
        */
        if (response.getContent().contains("IDX14100: JWT is not well formed"))
            throw new InvalidAuthenticationToken(response.getContent());

        if (response.getCode() != 200) throw new ConnectException(response);

        JSONObject messageJson = new JSONObject(response.getContent());
        JSONArray messages = messageJson.optJSONArray("value");

        if (messages != null) {
            for (int i = 0; i < messages.length(); i++) {
                JSONObject msg = messages.getJSONObject(i);
                EmailMessage emailMessage = EmailMessage.fromJson(msg);
                emailMessages.add(emailMessage);
            }
        }

        return emailMessages;
    }

    // IMAP OAuth
    public static List<EmailMessage> getInboxMessagesIMAPOAuth(String email, String accessToken) throws Exception {
        return getInboxMessages(email, accessToken, true);
    }

    // IMAP Basic
    public static List<EmailMessage> getInboxMessagesIMAPBasic(String email, String password) throws Exception {
        return getInboxMessages(email, password, false);
    }

    // Unified method for both OAuth and Basic authentication
    private static List<EmailMessage> getInboxMessages(String email, String credential, boolean isOAuth) throws Exception {
        List<EmailMessage> emailMessages = new ArrayList<>();

        Folder inbox = null;
        Store store = null;
        final boolean closeAfterDone = multipleThreaded;

        try {
            // Check if we can reuse existing connection (only when not in multipleThreaded mode)
            if (!multipleThreaded && canReuseConnection(email)) {
                inbox = currentFolder;
                store = currentStore;
                LogUtil.log("Using existing connection for " + email);
            }
            else {
                // Close previous connection if different email account
                if (!multipleThreaded && Objects.equals(currentEmail, email)) {
                    closeCurrentConnection();
                }

                // Create new connection
                try {
                    Properties props = getIMAPProperties(email, isOAuth);

                    // Create session and store
                    Session session = Session.getInstance(props);
                    store = session.getStore("imaps");

                    // Connect using appropriate authentication method
                    store.connect(email, credential);

                    // Access inbox
                    inbox = store.getFolder("INBOX");
                    inbox.open(Folder.READ_ONLY);
                }
                catch (Exception e) {
                    LogUtil.error(String.format("Error connecting to IMAP server %s for %s",
                            getHost(email),
                            email));
                    throw e;
                }

                // Store connection for reuse (only when not in multipleThreaded mode)
                if (!multipleThreaded) {
                    currentFolder = inbox;
                    currentStore = store;
                    currentEmail = email;
                }
            }

            return getEmailMessages(email, inbox, emailMessages);
        }
        catch (AuthenticationFailedException e) {
            LogUtil.error("Authentication failed for " + email);
            ThrowableUtil.println(e);
            closeCurrentConnection();
            throw e;
        }
        finally {
            if (closeAfterDone) {
                closeConnection(inbox, store, email);
            }
        }
    }
    private static List<EmailMessage> getEmailMessages(String email, Folder inbox, List<EmailMessage> emailMessages) throws MessagingException {
        try {
            Message[] mailMessages = inbox.getMessages();

            LogUtil.log(String.format("Inbox for %s: %d messages | Unread: %d",
                    email, inbox.getMessageCount(), inbox.getUnreadMessageCount()));

            // Process the most recent % messages (or all if less than %)
            int startIndex = Math.max(0, mailMessages.length - IMAP_MAXIMUM_LOAD_MESSAGE);
            List<Message> messagesToProcess = Arrays.asList(Arrays.copyOfRange(mailMessages, startIndex, mailMessages.length));
            Collections.reverse(messagesToProcess);

            boolean parallel = false;

            // Determine whether to use lazy loading
            // If multipleThreaded is true, disable lazy loading
            boolean useLazyLoad = multipleThreaded ? false : lazyLoad;
            //if (multipleThreaded) {
            //    LogUtil.log("multipleThreaded mode: lazy loading disabled");
            //}

            Date currentDate = new Date();
            Timer timer = Timer.getInstance();
            if (parallel) {
                emailMessages = messagesToProcess.parallelStream().map(message -> {
                            try {
                                if (message.isExpunged()) {
                                    // Message has been expunged, skip it
                                    LogUtil.log("Message " + message.getMessageNumber() + " has been expunged, skipping");
                                    return null;
                                }
                                if (message.isSet(Flags.Flag.DELETED)) {
                                    // Message is marked for deletion
                                    LogUtil.log("Message " + message.getMessageNumber() + " is marked for deletion, skipping");
                                    return null;
                                }

                                long diffInMillis = currentDate.getTime() - message.getReceivedDate().getTime();
                                System.out.printf("[%d] Loading subject: %s\t| %s ago%n",
                                        message.getMessageNumber(), MailContentExtractor.decodeMimeHeader(message.getSubject()),
                                        TimeUtil.millisToTime(diffInMillis));
                                // Use lazy loading based on the determined setting
                                return EmailMessage.fromIMAP(message, useLazyLoad);
                            }
                            catch (MessagingException e) {
                                throw new RuntimeException(e);
                            }
                        })
                        .filter(Objects::nonNull) // Filter out any nulls from errors
                        .collect(Collectors.toList());
            }
            else {
                for (int i = mailMessages.length - 1; i >= startIndex; i--) {
                    if (mailMessages[i].isExpunged()) {
                        // Message has been expunged, skip it
                        LogUtil.log("Message " + mailMessages[i].getMessageNumber() + " has been expunged, skipping");
                        continue;
                    }
                    if (mailMessages[i].isSet(Flags.Flag.DELETED)) {
                        // Message is marked for deletion
                        LogUtil.log("Message " + mailMessages[i].getMessageNumber() + " is marked for deletion, skipping");
                        continue;
                    }

                    long diffInMillis = currentDate.getTime() - mailMessages[i].getReceivedDate().getTime();
                    System.out.printf("[%d] Loading subject: %s\t| %s ago%n",
                            i, MailContentExtractor.decodeMimeHeader(mailMessages[i].getSubject()),
                            TimeUtil.millisToTime(diffInMillis));
                    // Use lazy loading based on the determined setting
                    EmailMessage message = EmailMessage.fromIMAP(mailMessages[i], useLazyLoad);
                    emailMessages.add(message);
                }
            }
            LogUtil.log("Passed time: " + timer.getTimeString());

            // Don't close connections here - keep them open for lazy loading
        }
        catch (Exception e) {
            LogUtil.error("Error retrieving messages for " + email);
            throw e;
        }
        return emailMessages;
    }

    public static Properties getIMAPProperties(String email, boolean isOAuth) {
        // Connection properties
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", getHost(email));
        props.put("mail.imaps.port", "993");
        //props.put("mail.debug", "true");

        props.setProperty("mail.imaps.ssl.trust", "*");
        props.setProperty("mail.imaps.ssl.checkserveridentity", "false");

        // Enforce SSL socket factory
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.socketFactory.port", "993");
        props.put("mail.imaps.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        props.put("mail.imaps.socketFactory.fallback", "false");

        // STARTTLS
        //props.put("mail.imap.host", "imap.example.com");
        //props.put("mail.imap.port", "143");
        //props.put("mail.imap.starttls.enable", "true");

        if (isOAuth) {
            props.put("mail.imaps.auth.mechanisms", "XOAUTH2");
            props.put("mail.imaps.auth.login.disable", "true");
            props.put("mail.imaps.auth.plain.disable", "true");
        }

        if (IMAP_PROXY != null) {
            props.setProperty("mail.imaps.proxy.host", IMAP_PROXY.getHost());
            props.setProperty("mail.imaps.proxy.port", String.valueOf(IMAP_PROXY.getPort()));
        }

        props.setProperty("mail.imaps.connectiontimeout", "30000"); // Timeout in milliseconds (30 seconds)
        props.setProperty("mail.imaps.timeout", "30000");           // I/O timeout in milliseconds

        return props;
    }
    public static String getHost(String email){
        String domain = email.split("@")[1].toLowerCase();

        try {
            // Read hosts.json file
            String content = FileUtil.readString(hosts);

            // Parse JSON
            JsonObject jsonObject = JsonParser.parseString(content).getAsJsonObject();
            JsonArray domains = jsonObject.getAsJsonArray("domains");

            // Find matching domain pattern
            for (JsonElement element : domains) {
                JsonObject domainObj = element.getAsJsonObject();
                JsonElement patternElement = domainObj.get("pattern");
                String host = domainObj.get("host").getAsString();

                // Check if pattern is a string or an array
                if (patternElement.isJsonArray()) {
                    // Handle array of patterns
                    JsonArray patterns = patternElement.getAsJsonArray();
                    for (JsonElement patternItem : patterns) {
                        String pattern = patternItem.getAsString();
                        if (patternMatch(domain, pattern)) {
                            if (host.contains("{domain}")) {
                                return host.replace("{domain}", domain);
                            }
                            return host;
                        }
                    }
                }
                else {
                    // Handle single pattern (string)
                    String pattern = patternElement.getAsString();
                    if (patternMatch(domain, pattern)) {
                        if (host.contains("{domain}")) {
                            return host.replace("{domain}", domain);
                        }
                        return host;
                    }
                }
            }

            // Default fallback
            return "imap." + domain;
        }
        catch (Exception e) {
            LogUtil.error("Error reading hosts.json: " + e.getMessage());
            ThrowableUtil.println(e);
            // Fallback to default behavior if file can't be read
            return "imap." + domain;
        }
    }
    public static boolean patternMatch(String domain, String pattern){
        // Check for exact match
        if (pattern.equals(domain)) {
            return true;
        }

        // Check for wildcard match
        if (pattern.contains("*")) {
            String patternRegex = pattern.replace("*", ".*");
            return domain.matches(patternRegex);
        }

        return false;
    }

    // Check if existing connection can be reused
    public static boolean canReuseConnection(String email) {
        return currentFolder != null &&
                currentFolder.isOpen() &&
                currentEmail != null &&
                currentEmail.equals(email);
    }

    /**
     * Closes the current IMAP folder and store connection.
     * This should be called when switching to a different email account or when the application exits.
     */
    public static void closeCurrentConnection() {
        boolean closed = false;

        if (currentFolder != null && currentFolder.isOpen()) {
            try {
                currentFolder.close(false);
                closed = true;
            } catch (MessagingException e) {
                ThrowableUtil.println(e);
            } finally {
                currentFolder = null;
            }
        }

        if (currentStore != null) {
            try {
                currentStore.close();
                closed = true;
            } catch (MessagingException e) {
                ThrowableUtil.println(e);
            } finally {
                currentStore = null;
            }
        }

        if (closed) {
            LogUtil.warning(currentEmail + " connection closed");
        }

        currentEmail = null;
    }
    public static void closeConnection(Folder inbox, Store store, String email) {
        try {
            if (inbox != null && inbox.isOpen()) {
                inbox.close(false);
            }
            if (store != null) {
                store.close();
            }
            LogUtil.warning(email + " connection closed (multipleThreaded mode)");
        }
        catch (MessagingException e) {
            ThrowableUtil.println(e);
        }
    }

    /**
     * Retrieves a specific message by ID using the Microsoft Graph API.
     * 
     * @param accessToken The access token for authentication
     * @param messageId The ID of the message to retrieve
     * @return The EmailMessage object, or null if not found
     * @throws Exception If an error occurs during the API call
     */
    public static EmailMessage getMessageGraphAPI(String accessToken, String messageId) throws Exception {
        String tokenUrl = "https://graph.microsoft.com/v1.0/me/messages/" + messageId;

        ResponseContent response = HttpClient.create()
                .getRequest(tokenUrl,
                        Headers.create()
                                .setAuthorization("Bearer " + accessToken)
                                .setDefaultContentType()
                                .get());

        if (response.getCode() != 200) throw new ConnectException(response);

        JSONObject messageJson = new JSONObject(response.getContent());
        return EmailMessage.fromJson(messageJson);
    }

    /**
     * Deletes an email message based on the account type and message ID.
     * 
     * @param account The email account
     * @param messageId The ID of the message to delete
     * @return true if deletion was successful, false otherwise
     * @throws Exception If an error occurs during the deletion process
     */
    public static boolean deleteEmail(EmailAccount account, String messageId) throws Exception {
        if (account.getType().equals(AuthType.GRAPH)) {
            return deleteEmailGraphAPI(account.getAccessToken(), messageId);
        }
        else if (account.getType().equals(AuthType.IMAP_OAUTH)) {
            return deleteEmailIMAPOAuth(account.getEmail(), account.getAccessToken(), messageId);
        }
        else { // account.getType().equals(AuthType.IMAP_BASIC)
            return deleteEmailIMAPBasic(account.getEmail(), account.getPassword(), messageId);
        }
    }

    /**
     * Deletes an email message using the Microsoft Graph API.
     * 
     * @param accessToken The access token for authentication
     * @param messageId The ID of the message to delete
     * @return true if deletion was successful, false otherwise
     * @throws Exception If an error occurs during the API call
     */
    public static boolean deleteEmailGraphAPI(String accessToken, String messageId) throws Exception {
        String tokenUrl = "https://graph.microsoft.com/v1.0/me/messages/" + messageId;

        HttpClient client = Main.proxies.isEmpty() ? HttpClient.create()
                : HttpClient.proxy(ProxyUtil.http(Main.proxies.pick()));

        ResponseContent response = client
                .setKeepAlive(false)
                .deleteRequest(tokenUrl,
                        Headers.create()
                                .setAuthorization("Bearer " + accessToken)
                                .setDefaultContentType()
                                .get());

        System.out.println(response.printInfo());

        // 204 No Content is the expected response for successful deletion
        return response.getCode() == 204;
    }

    /**
     * Deletes an email message using IMAP with OAuth authentication.
     * 
     * @param email The email address
     * @param accessToken The OAuth access token
     * @param messageId The ID of the message to delete
     * @return true if deletion was successful, false otherwise
     * @throws Exception If an error occurs during the IMAP operation
     */
    public static boolean deleteEmailIMAPOAuth(String email, String accessToken, String messageId) throws Exception {
        return deleteEmail(email, accessToken, messageId, true);
    }

    /**
     * Deletes an email message using IMAP with basic authentication.
     * 
     * @param email The email address
     * @param password The password
     * @param messageId The ID of the message to delete
     * @return true if deletion was successful, false otherwise
     * @throws Exception If an error occurs during the IMAP operation
     */
    public static boolean deleteEmailIMAPBasic(String email, String password, String messageId) throws Exception {
        return deleteEmail(email, password, messageId, false);
    }

    // Unified delete method for both OAuth and Basic authentication
    private static boolean deleteEmail(String email, String credential, String messageId, boolean isOAuth) throws Exception {
        Folder inbox = null;
        Store store = null;

        try {
            // Connection properties
            Properties props = getIMAPProperties(email, isOAuth);

            // Create session and store
            Session session = Session.getInstance(props);
            store = session.getStore("imaps");

            // Connect using appropriate authentication method
            store.connect(email, credential);

            // Access inbox in READ_WRITE mode (required for deletion)
            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_WRITE);

            // Search for and delete the message
            return findAndDeleteMessage(inbox, messageId);
        }
        catch (Exception e) {
            String authType = isOAuth ? "OAuth" : "Basic";
            LogUtil.error("Error deleting message via IMAP " + authType + " for " + email);
            ThrowableUtil.println(e);
            throw e;
        }
        finally {
            closeDeleteConnection(inbox, store);
        }
    }

    // Find and delete message by ID
    private static boolean findAndDeleteMessage(Folder inbox, String messageId) throws Exception {
        Message[] messages = inbox.getMessages();

        for (Message message : messages) {
            String msgId = message.getHeader("Message-ID") != null ?
                    message.getHeader("Message-ID")[0] :
                    String.valueOf(message.getMessageNumber());

            if (msgId.equals(messageId)) {
                // Mark the message for deletion
                message.setFlag(Flags.Flag.DELETED, true);
                return true;
            }
        }

        return false; // Message not found
    }

    // Safely close delete connection
    private static void closeDeleteConnection(Folder inbox, Store store) {
        try {
            if (inbox != null && inbox.isOpen()) {
                // Close with expunge to actually delete the messages
                inbox.close(true); // Expunge deleted messages
            }
            if (store != null) {
                store.close();
            }
        } catch (Exception e) {
            LogUtil.error("Error closing IMAP connection");
            ThrowableUtil.println(e);
        }
    }
}
