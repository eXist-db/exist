package org.exist.xquery;

import org.exist.xquery.parser.XQueryAST;

public class StaticXQueryException extends XPathException
{
	public StaticXQueryException(String message) {
		super(message);
	}

	public StaticXQueryException(XQueryAST ast, String message) {
		super(ast, message);
	}
	
	public StaticXQueryException(String message, int line, int column) {
		super(message, line, column);
	}
	
	public StaticXQueryException(Throwable cause) {
		super(cause);
	}

	public StaticXQueryException(String message, Throwable cause) {
		super(message, cause);
	}

	public StaticXQueryException(XQueryAST ast, String message, Throwable cause) {
		super(ast, message, cause);
	}
}