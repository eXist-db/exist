package org.exist.http;

public class MethodNotAllowedException extends Exception {

    private static final long serialVersionUID = -8399138417913514619L;

    public MethodNotAllowedException(String message) {
        super(message);
    }

    public MethodNotAllowedException(String message, Throwable cause) {
        super(message, cause);
    }

}
