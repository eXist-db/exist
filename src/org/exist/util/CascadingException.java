package org.exist.util;

/**
 * CascadingException.java
 * 
 * @author Wolfgang Meier
 */
public class CascadingException
	extends Exception
	implements CascadingThrowable {

    Throwable m_throwable;
    
	/**
	 * Constructor for CascadingException.
	 */
	public CascadingException() {
		super();
	}

	/**
	 * Constructor for CascadingException.
	 * @param message
	 */
	public CascadingException(String message) {
		super(message);
	}

	/**
	 * Constructor for CascadingException.
	 * @param message
	 * @param cause
	 */
	public CascadingException(String message, Throwable cause) {
		super(message);
        m_throwable = cause;
	}

	/**
	 * Constructor for CascadingException.
	 * @param cause
	 */
	public CascadingException(Throwable cause) {
		m_throwable = cause;
	}
    
    /**
     * Return the root cause.
     * 
     * @see java.lang.Throwable#getCause()
     */
    public Throwable getCause() {
        return m_throwable;
    }

}
