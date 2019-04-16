package org.exist.start;

public class StartException extends Exception {
    public int getErrorCode() {
        return errorCode;
    }

    private final int errorCode;

    public StartException(final int errorCode) {
        this.errorCode = errorCode;
    }

    @Override
    public String getMessage() {
        return "Error code: " + getErrorCode();
    }
}
