package org.exist.xquery;

public class XPathException extends Exception {

	/**
	 * 
	 */
	public XPathException() {
		super();
	}

	/**
	 * @param message
	 */
	public XPathException(String message) {
		super(message);
	}

	/**
	 * @param cause
	 */
	public XPathException(Throwable cause) {
		super(cause);
	}

	/**
	 * @param message
	 * @param cause
	 */
	public XPathException(String message, Throwable cause) {
		super(message, cause);
	}

}
