package org.exist.fluent;

/**
 * Signals all errors and failures related to database access.
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 * @version $Revision: 1.4 $ ($Date: 2006/04/13 19:12:17 $)
 */
public class DatabaseException extends RuntimeException {
	
	DatabaseException() {
		super();
	}

	DatabaseException(String message) {
		super(message);
	}

	DatabaseException(Throwable cause) {
		super(cause);
	}

	DatabaseException(String message, Throwable cause) {
		super(message, cause);
	}

}
