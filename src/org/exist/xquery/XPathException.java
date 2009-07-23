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

	public XPathException(int line, int column, String message) {
		super();
		this.message = message;
		this.line = line;
        this.column = column;
	}

    public XPathException(Expression expr, String message) {
        super();
        this.message = message;
        this.line = expr.getLine();
        this.column = expr.getColumn();
    }

    public XPathException(XQueryAST ast, String message) {
        super();
        this.message = message;
        if (ast != null) {
            this.line = ast.getLine();
            this.column = ast.getColumn();
        }
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

    public XPathException(Expression expr, String message, Throwable cause) {
        this(expr.getLine(), expr.getColumn(), message, cause);
    }

	public XPathException(int line, int column, String message, Throwable cause) {
		super(cause);
		this.message = message;
        this.line = line;
        this.column = column;
	}
    
	public XPathException(int line, int column, Throwable cause) {
		super(cause);
        this.line = line;
        this.column = column;
	}

    public void setLocation(int line, int column) {
        this.line = line;
        this.column = column;
    }
    
	public int getLine() {
		return line;
	}
	
	public int getColumn() {
		return column;
	}
	
    public void addFunctionCall(UserDefinedFunction def, Expression call) {
        if (callStack == null)
            callStack = new ArrayList();
        callStack.add(new FunctionStackElement(def, call.getLine(), call.getColumn()));
    }
	
    public void prependMessage(String msg) {
        message = msg + message;
    }
    
	/* (non-Javadoc)
	 * @see java.lang.Throwable#getMessage()
	 */
	public String getMessage() {
        StringBuilder buf = new StringBuilder();
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
    
    /**
     * Returns just the error message, not including
     * line numbers or the call stack.
     * 
     * @return error message
     */
    public String getDetailMessage() {
        return message;
    }
    
    public String getMessageAsHTML() {
        StringBuilder buf = new StringBuilder();
        if(message == null)
            message = "";
		message = message.replaceAll("\r?\n", "<br/>");
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
            buf.append("<table id=\"xquerytrace\">");
            buf.append("<caption>XQuery Stack Trace</caption>");
            FunctionStackElement e;
            for (Iterator i = callStack.iterator(); i.hasNext(); ) {
                e = (FunctionStackElement) i.next();
                buf.append("<tr><td class=\"func\">").append(e.function).append("</td>");
                buf.append("<td class=\"lineinfo\">").append(e.line).append(':').append(e.column).append("</td>");
                buf.append("</tr>");
            }
            buf.append("</table>");
        }
        return buf.toString();
    }
    
    private static class FunctionStackElement {
        String function;
        int line;
        int column;

        FunctionStackElement(UserDefinedFunction func, int line, int column) {
            this.function = func.toString();
            this.line = line;
            this.column = column;
        }
        
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append(function).append(" [");
            buf.append(line).append(":");
            buf.append(column).append(']');
            return buf.toString();
        }
    }
}