package org.exist.xquery;

import org.exist.xquery.parser.XQueryAST;

public class XPathException extends Exception {

	private int line = 0;
	private int column = 0;
	
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

	public XPathException(XQueryAST ast, String message) {
		super(message);
		setASTNode(ast);
	}
	
	public XPathException(String message, int line, int column) {
		super(message);
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
		super(message, cause);
	}

	public XPathException(XQueryAST ast, String message, Throwable cause) {
		super(message, cause);
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
	
	/* (non-Javadoc)
	 * @see java.lang.Throwable#getMessage()
	 */
	public String getMessage() {
		if(line == 0)
			return super.getMessage();
		else {
			StringBuffer buf = new StringBuffer();
			buf.append(super.getMessage());
			buf.append(" [at line ");
			buf.append(getLine());
			buf.append(", column ");
			buf.append(getColumn());
			buf.append("]");
			return buf.toString();
		}
	}
}
