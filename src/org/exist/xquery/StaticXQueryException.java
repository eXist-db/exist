package org.exist.xquery;

import org.exist.xquery.parser.XQueryAST;

public class StaticXQueryException extends XPathException
{
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

	public StaticXQueryException(int line, int column, String message, Throwable cause) {
		super(line, column, message, cause);
	}
}