package net.tokenu.mail.model;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.tokenu.mail.util.AuthType;

import java.time.Instant;
import java.util.Objects;

/**
 * Represents an email account with authentication information.
 */
public class EmailAccount {
    private String email;
    private String password;
    private String clientId;
    private String refreshToken;
    private String accessToken;
    private long expiresIn;

    private AuthType type;

    private static final String refreshTokenKey = "refreshToken";
    private static final String accessTokenKey = "accessToken";
    private static final String expiresInKey = "expiresIn";

    /**
     * Creates an EmailAccount from a JsonObject.
     * 
     * @param jsonObj The JsonObject containing account data
     * @return A new EmailAccount instance
     */
    public static EmailAccount fromJson(JsonObject jsonObj) {
        EmailAccount account = new EmailAccount();

        account.clientId = getJsonValueCaseInsensitive(jsonObj, "clientId");
        account.email = getJsonValueCaseInsensitive(jsonObj, "email");
        account.password = getJsonValueCaseInsensitive(jsonObj, "password");
        account.refreshToken = getJsonValueCaseInsensitive(jsonObj, "refreshToken");

        account.accessToken = (String) getJsonValueCaseInsensitive(jsonObj, accessTokenKey, null);
        account.expiresIn = ((Number) getJsonValueCaseInsensitive(jsonObj, expiresInKey, 0)).longValue();

        account.type = AuthType.valueOf(getJsonValueCaseInsensitive(jsonObj, "type", AuthType.GRAPH.toString()).toString());

        if (account.refreshToken  == null) {
            account.type = AuthType.IMAP_BASIC;
        }
        return account;
    }

    public static EmailAccount fromText(String email, String password) {
        EmailAccount account = new EmailAccount();

        account.email = email;
        account.password = password;

        account.type = AuthType.IMAP_BASIC;

        return account;
    }

    /**
     * Converts this EmailAccount to a JsonObject.
     *
     * @return JsonObject containing the account data
     */
    public JsonObject toJson() {
        JsonObject json = new JsonObject();
        json.addProperty("clientId", clientId);
        json.addProperty("email", email);
        json.addProperty("password", password);
        json.addProperty("refreshToken", refreshToken);
        json.addProperty(accessTokenKey, accessToken);
        json.addProperty(expiresInKey, expiresIn);
        json.addProperty("type", type.name());
        return json;
    }

    /**
     * Updates the access token and expiration time.
     *
     * @param accessToken The new access token
     * @param expiresIn The expiration time in seconds from now
     */
    public void updateAccessToken(String accessToken, long expiresIn, AuthType type) {
        this.type = type;
        this.accessToken = accessToken;
        this.expiresIn = Instant.now().plusSeconds(expiresIn).toEpochMilli();
    }
    /**
     * Updates the access token and expiration time.
     *
     * @param accessToken The new access token
     * @param expiresIn The expiration time in seconds from now
     */
    public void updateAccessToken(String refreshToken, String accessToken, long expiresIn, AuthType type) {
        this.type = type;
        this.refreshToken = refreshToken;
        this.accessToken = accessToken;
        this.expiresIn = Instant.now().plusSeconds(expiresIn).toEpochMilli();
    }

    /**
     * Checks if the access token is valid (not null and not expired).
     * 
     * @return true if the access token is valid, false otherwise
     */
    public boolean hasValidAccessToken() {
        if (type.equals(AuthType.IMAP_BASIC)) return true;
        return accessToken != null && expiresIn > Instant.now().toEpochMilli();
    }

    /**
     * Updates the JsonObject with the current access token and expiration time.
     * 
     * @param jsonObj The JsonObject to update
     */
    public void updateJsonObject(JsonObject jsonObj) {
        if (accessToken == null) return;
        jsonObj.addProperty(refreshTokenKey, refreshToken);
        jsonObj.addProperty(accessTokenKey, accessToken);
        jsonObj.addProperty(expiresInKey, expiresIn);
        jsonObj.addProperty("type", type.name());
    }

    // Getters
    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }

    public String getClientId() {
        return clientId;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public AuthType getType() {
        return type;
    }

    public void setType(AuthType type) {
        this.type = type;
    }

    // Input key should be camelCase
    // Helper method to get JSON value regardless of case
    public static String getJsonValueCaseInsensitive(JsonObject jsonObject, String key) {
        String lowercaseKey = key.toLowerCase();
        String pascalCaseKey = key.substring(0, 1).toUpperCase() + key.substring(1);
        String snakeCaseKey = toSnakeCase(key);

        return jsonObject.has(key) ? jsonObject.get(key).getAsString() :
                jsonObject.has(snakeCaseKey) ? jsonObject.get(snakeCaseKey).getAsString() :
                        jsonObject.has(lowercaseKey) ? jsonObject.get(lowercaseKey).getAsString() :
                                jsonObject.has(pascalCaseKey) ? jsonObject.get(pascalCaseKey).getAsString() :
                                null;
    }
    public static Object getJsonValueCaseInsensitive(JsonObject jsonObject, String key, Object defaultValue) {
        if (jsonObject == null || key == null || key.isEmpty()) {
            return defaultValue;
        }

        String lowercaseKey = key.toLowerCase();
        String pascalCaseKey = key.substring(0, 1).toUpperCase() + key.substring(1);
        String snakeCaseKey = toSnakeCase(key);

        return jsonObject.has(key) ? getJsonValueSafely(jsonObject, key, defaultValue) :
                jsonObject.has(snakeCaseKey) ? getJsonValueSafely(jsonObject, snakeCaseKey, defaultValue) :
                        jsonObject.has(lowercaseKey) ? getJsonValueSafely(jsonObject, lowercaseKey, defaultValue) :
                                jsonObject.has(pascalCaseKey) ? getJsonValueSafely(jsonObject, pascalCaseKey, defaultValue) :
                                        defaultValue;
    }
    public static Object getJsonValueSafely(JsonObject jsonObject, String key, Object defaultValue) {
        try {
            if (!jsonObject.has(key) || jsonObject.get(key).isJsonNull())
                return defaultValue;
            if (jsonObject.get(key).isJsonPrimitive()) {
                JsonPrimitive primitive = jsonObject.getAsJsonPrimitive(key);
                if (primitive.isString()) {
                    return primitive.getAsString();
                } else if (primitive.isNumber()) {
                    return primitive.getAsNumber();
                } else if (primitive.isBoolean()) {
                    return primitive.getAsBoolean();
                }
            }
            return jsonObject.get(key); // Return as is for complex types
        } catch (Exception e) {
            return defaultValue;
        }
    }

    public static String toSnakeCase(String input) {
        // Insert underscores before uppercase letters, except at the start
        String withUnderscores = input.replaceAll("([a-z])([A-Z])", "$1_$2");
        // Convert the entire string to lowercase
        return withUnderscores.toLowerCase();
    }

    @Override
    public String toString() {
        return email;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof EmailAccount)) return false;
        EmailAccount that = (EmailAccount) o;
        return Objects.equals(email, that.email);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(email);
    }
}