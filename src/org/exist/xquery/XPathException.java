package org.exist.xquery;

import org.exist.xquery.parser.XQueryAST;

public class XPathException extends Exception {

	private int line = 0;
	private int column = 0;
	private String message = null;
	
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
		super();
		this.message = message;
	}

	public XPathException(XQueryAST ast, String message) {
		super();
		this.message = message;
		setASTNode(ast);
	}
	
	public XPathException(String message, int line, int column) {
		super();
		this.message = message;
		this.line = line;
		this.column = column;
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
		super(cause);
		this.message = message;
	}

	public XPathException(XQueryAST ast, String message, Throwable cause) {
		super(cause);
		this.message = message;
		setASTNode(ast);
	}
	
	public int getLine() {
		return line;
	}
	
	public int getColumn() {
		return column;
	}
	
	public void setASTNode(XQueryAST ast) {
		if(ast != null) {
			this.line = ast.getLine();
			this.column = ast.getColumn();
		}
	}
	
	public void prependMessage(String msg) {
		StringBuffer buf = new StringBuffer();
		buf.append(msg);
		if(message != null && message.length() > 0)
			buf.append(": \n");
		buf.append(message);
		message = buf.toString();
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Throwable#getMessage()
	 */
	public String getMessage() {
		if(message == null)
			message = "";
		if(line == 0)
			return message;
		else {
			StringBuffer buf = new StringBuffer();
			buf.append(message);
			buf.append(" [at line ");
			buf.append(getLine());
			buf.append(", column ");
			buf.append(getColumn());
			buf.append("]");
			return buf.toString();
		}
	}
}
