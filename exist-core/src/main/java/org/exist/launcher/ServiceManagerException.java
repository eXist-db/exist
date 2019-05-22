package org.exist.launcher;

public class ServiceManagerException extends Exception {
    public ServiceManagerException(final String message) {
        super(message);
    }

    public ServiceManagerException(final Throwable cause) {
        super(cause);
    }

    public ServiceManagerException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
