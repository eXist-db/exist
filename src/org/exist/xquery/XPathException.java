package org.exist.xquery;

import org.exist.xquery.parser.XQueryAST;

public class XPathException extends Exception {

	private XQueryAST astNode = null;
	
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
		this.astNode = ast;
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
		this.astNode = ast;
	}
	
	public void setASTNode(XQueryAST ast) {
		this.astNode = ast;
	}
	
	public XQueryAST getASTNode() {
		return astNode;
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Throwable#getMessage()
	 */
	public String getMessage() {
		if(astNode == null)
			return super.getMessage();
		else {
			StringBuffer buf = new StringBuffer();
			buf.append(super.getMessage());
			buf.append(" [at line ");
			buf.append(astNode.getLine());
			buf.append(", column ");
			buf.append(astNode.getColumn());
			buf.append("]");
			return buf.toString();
		}
	}
}
