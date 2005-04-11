package org.exist.xquery;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.exist.xquery.parser.XQueryAST;

public class XPathException extends Exception {

	private int line = 0;
	private int column = 0;
	private String message = null;
	private List callStack = null;
    
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
	
    public void addFunctionCall(UserDefinedFunction def, XQueryAST ast) {
        if (callStack == null)
            callStack = new ArrayList();
        StringBuffer msg = new StringBuffer();
        msg.append(def.toString());
        msg.append(" [");
        msg.append(ast.getLine());
        msg.append(", ");
        msg.append(ast.getColumn());
        msg.append("]");
        callStack.add(msg.toString());
    }
	
    public void prependMessage(String msg) {
        message = msg + message;
    }
    
	/* (non-Javadoc)
	 * @see java.lang.Throwable#getMessage()
	 */
	public String getMessage() {
        StringBuffer buf = new StringBuffer();
		if(message == null)
			message = "";
		buf.append(message);
        if (getLine() > 0) {
			buf.append(" [at line ");
			buf.append(getLine());
			buf.append(", column ");
			buf.append(getColumn());
			buf.append("]");
		}
        if (callStack != null) {
            buf.append("\nIn call to function:\n");
            for (Iterator i = callStack.iterator(); i.hasNext(); ) {
                buf.append('\t').append(i.next());
                if (i.hasNext())
                    buf.append('\n');
            }
        }
        return buf.toString();
	}
    
    public String getMessageAsHTML() {
        StringBuffer buf = new StringBuffer();
        if(message == null)
            message = "";
		message = message.replaceAll("\r?\n", "<br/>");
        buf.append("<div class=\"message\">");
        buf.append("<h2>").append(message);
        if (getLine() > 0) {
            buf.append(" [at line ");
            buf.append(getLine());
            buf.append(", column ");
            buf.append(getColumn());
            buf.append("]");
        }
        buf.append("</h2>");
        if (callStack != null) {
            buf.append("<p>In call to function:</p>");
            buf.append("<ul class=\"trace\">");
            for (Iterator i = callStack.iterator(); i.hasNext(); ) {
                buf.append("<li>").append(i.next()).append("</li>");
            }
            buf.append("</ul>");
        }
        buf.append("</div>");
        return buf.toString();
    }
}
