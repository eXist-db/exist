package org.exist.util;

public class DatabaseConfigurationException extends Exception {

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
