package net.tokenu.mail.util;

public class InvalidAuthenticationToken extends RuntimeException {
    public InvalidAuthenticationToken(String message) {
        super(message);
    }
}
