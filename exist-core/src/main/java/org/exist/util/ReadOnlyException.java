package org.exist.util;


public class ReadOnlyException extends Exception {

	private static final long serialVersionUID = 7077941517830242672L;

	/**
	 * Constructor for ReadOnlyException.
	 */
	public ReadOnlyException() {
		super();
	}

	/**
	 * Constructor for ReadOnlyException.
	 *
	 * @param message the exception message
	 */
	public ReadOnlyException(String message) {
		super(message);
	}

}
