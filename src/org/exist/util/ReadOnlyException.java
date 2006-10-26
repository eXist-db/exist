package org.exist.util;


public class ReadOnlyException extends Exception {

	/**
	 * Constructor for ReadOnlyException.
	 */
	public ReadOnlyException() {
		super();
	}

	/**
	 * Constructor for ReadOnlyException.
	 * @param message
	 */
	public ReadOnlyException(String message) {
		super(message);
	}

}
