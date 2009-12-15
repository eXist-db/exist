package org.exist.util;

public class DatabaseConfigurationException extends Exception {

	private static final long serialVersionUID = 5583749487314182182L;

	public DatabaseConfigurationException() {
		super();
	}

	public DatabaseConfigurationException(String message) {
		super(message);
	}

    public DatabaseConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
