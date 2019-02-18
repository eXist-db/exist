package org.exist.xquery;

public class StaticXQueryException extends XPathException
{
	private static final long serialVersionUID = -8229758099980343418L;

	public StaticXQueryException(String message) {
		super(message);
	}

	public StaticXQueryException(int line, int column, String message) {
		super(line, column, message);
	}
	
	public StaticXQueryException(Throwable cause) {
		super(cause);
	}

	public StaticXQueryException(String message, Throwable cause) {
		super(message, cause);
	}

        //TODO add in ErrorCode and ErrorVal
	public StaticXQueryException(int line, int column, String message, Throwable cause) {
		super(line, column, message, cause);
	}
}