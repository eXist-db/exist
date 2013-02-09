package org.exist.xquery;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.exist.security.xacml.XACMLSource;
import org.exist.xquery.ErrorCodes.ErrorCode;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.value.Sequence;

public class XPathException extends Exception {

    private static final long serialVersionUID = 212844692232650666L;
    private int line = 0;
    private int column = 0;
    private ErrorCode errorCode = ErrorCodes.ERROR;
    private String message = null;
    private Sequence errorVal;
    private List<FunctionStackElement> callStack = null;

    private XACMLSource source = null;

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
    
    public XPathException(ErrorCode errorCode, int line, int column) {
        super();
        this.errorCode = errorCode;
        this.line = line;
        this.column = column;
    }

    public XPathException(int line, int column, ErrorCode errorCode, String message) {
        super();
        this.errorCode = errorCode;
        this.message = message;
        this.line = line;
        this.column = column;
    }

    /**
     * Use constructor with errorCode and errorVal
     */
    public XPathException(Expression expr, String message) {
        super();
        this.message = message;
        this.line = expr.getLine();
        this.column = expr.getColumn();
        this.source = expr.getSource();
    }
    
    public XPathException(Expression expr, ErrorCode errorCode, String errorDesc) {
        super();
        this.errorCode = errorCode;
        this.message = errorDesc;
        this.line = expr.getLine();
        this.column = expr.getColumn();
        this.source = expr.getSource();
    }

    public XPathException(Expression expr, ErrorCode errorCode, String errorDesc, Sequence errorVal) {
        super();
        this.errorCode = errorCode;
        this.message = errorDesc;
        this.errorVal = errorVal;
        this.line = expr.getLine();
        this.column = expr.getColumn();
        this.source = expr.getSource();
    }

    public XPathException(Expression expr, ErrorCode errorCode, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
        this.message = cause.getMessage();
        this.line = expr.getLine();
        this.column = expr.getColumn();
        this.source = expr.getSource();
    }

    /**
     * Use constructor with errorCode and errorVal
     */
    @Deprecated
    public XPathException(XQueryAST ast, String message) {
        super();
        this.message = message;
        if(ast != null) {
            this.line = ast.getLine();
            this.column = ast.getColumn();
        }
    }

    public XPathException(XQueryAST ast, ErrorCode errorCode, String message) {
        super();
        this.message = message;
        if(ast != null) {
            this.line = ast.getLine();
            this.column = ast.getColumn();
        }
    }

    @Deprecated
    public XPathException(Throwable cause) {
        super(cause);
    }

    @Deprecated
    public XPathException(String message, Throwable cause) {
        super(cause);
        this.message = message;
    }


    public XPathException(Expression expr, Throwable cause) {
        this(expr, ErrorCodes.ERROR, cause.getMessage(), null, cause);
    }

    /**
     * Use constructor with errorCode and errorVal
     */
    public XPathException(Expression expr, String message, Throwable cause) {
        this(expr, ErrorCodes.ERROR, message, null, cause);
    }

    public XPathException(Expression expr, ErrorCode errorCode, String errorDesc, Sequence errorVal, Throwable cause) {
        this(expr.getLine(), expr.getColumn(), errorDesc, cause);
        this.errorCode = errorCode;
        this.errorVal = errorVal;
    }
    
    public XPathException(ErrorCode errorCode, String errorDesc, Sequence errorVal) {
        this(errorCode, errorDesc);
        this.errorVal = errorVal;
    }

    //useful at static analysis time
    public XPathException(ErrorCode errorCode, String errorDesc) {
        this.errorCode = errorCode;

        if(errorDesc == null){
            this.message = errorCode.toString();
        } else {
            this.message = errorDesc;
        }
    }
    
    public XPathException(ErrorCode errorCode, String errorDesc, Sequence errorVal, Throwable cause) {
        this(errorCode, errorDesc, cause);
        this.errorVal = errorVal;
    }
    
    public XPathException(ErrorCode errorCode, String errorDesc, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;

        if(errorDesc == null){
            this.message = errorCode.toString();
        } else {
            this.message = errorDesc;
        }
    }

    protected XPathException(int line, int column, String message, Throwable cause) {
        super(cause);
        this.message = message;
        this.line = line;
        this.column = column;
    }

    @Deprecated
    public XPathException(int line, int column, Throwable cause) {
        super(cause);
        this.line = line;
        this.column = column;
    }

    public void setLocation(int line, int column) {
        this.line = line;
        this.column = column;
    }

    public void setLocation(int line, int column, XACMLSource source) {
        this.line = line;
        this.column = column;
        this.source = source;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public ErrorCode getCode() {
    	return errorCode;
    }
    
    public XACMLSource getXACMLSource() {
        return source;
    }
    
    public void addFunctionCall(UserDefinedFunction def, Expression call) {
        if(callStack == null) {
            callStack = new ArrayList<FunctionStackElement>();
        }
        callStack.add(new FunctionStackElement(def, def.getSource().getKey(), call.getLine(), call.getColumn()));
    }
    
    public List<FunctionStackElement> getCallStack() {
        return callStack;
    }

    public void prependMessage(String msg) {
        message = msg + message;
    }

    public void prependMessage(ErrorCode errorCode, String msg) {
//        if (this.errorCode == ErrorCodes.ERROR) {
            this.errorCode = errorCode;
//        }
        message = msg + message;
    }

    /* (non-Javadoc)
     * @see java.lang.Throwable#getMessage()
     */
    @Override
    public String getMessage() {
        final StringBuilder buf = new StringBuilder();
        if(message == null) {
            message = "";
        }
        if(errorCode != null) {
            buf.append(errorCode.getErrorQName()); //TODO consider also displaying the W3C message by calling errorCode.toString()
            buf.append(" ");
            
            if(message.isEmpty()) {
                message = errorCode.getDescription();
            }
        }
        buf.append(message);
        if(getLine() > 0 || source != null) {
            buf.append(" [");
            if(getLine() > 0) {
                buf.append("at line ");
                buf.append(getLine());
                buf.append(", column ");
                buf.append(getColumn());
                if(source != null) {
                    buf.append(", ");
                }
            }
            if(source != null) {
                buf.append("source: ").append(source.getKey());
            }
            buf.append("]");
        }
        if(callStack != null) {
            buf.append("\nIn function:\n");
            for(final Iterator<FunctionStackElement> i = callStack.iterator(); i.hasNext();) {
                final FunctionStackElement stack = i.next();
                buf.append('\t').append(stack);
                if(i.hasNext()) {
                    buf.append('\n');
                }
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
        final StringBuilder buf = new StringBuilder();
        if(message == null) {
            message = "";
        }
        message = message.replaceAll("\r?\n", "<br/>");
        buf.append("<h2>").append(message);
        if(getLine() > 0) {
            buf.append(" [at line ");
            buf.append(getLine());
            buf.append(", column ");
            buf.append(getColumn());
            buf.append("]");
        }
        buf.append("</h2>");
        if(callStack != null) {
            buf.append("<table id=\"xquerytrace\">");
            buf.append("<caption>XQuery Stack Trace</caption>");

            for(final FunctionStackElement e : callStack) {
                buf.append("<tr><td class=\"func\">").append(e.function).append("</td>");
                buf.append("<td class=\"lineinfo\">").append(e.line).append(':').append(e.column).append("</td>");
                buf.append("</tr>");
            }
            buf.append("</table>");
        }
        return buf.toString();
    }

    public static class FunctionStackElement {

        public int getColumn() {
            return column;
        }

        public String getFile() {
            return file;
        }

        public String getFunction() {
            return function;
        }

        public int getLine() {
            return line;
        }

        String function;
        String file;
        int line;
        int column;

        public FunctionStackElement(UserDefinedFunction func, String file, int line, int column) {
            this.function = func.toString();
            this.file = file;
            this.line = line;
            this.column = column;
        }

        public String toString() {
            final StringBuilder buf = new StringBuilder();
            buf.append(function).append(" [");
            buf.append(line).append(":");
            buf.append(column).append(":").append(file).append(']');
            return buf.toString();
        }
    }

    /** Get xquery errorcode */
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /** Get xquery error value */
    public Sequence getErrorVal() {
        return errorVal;
    }
}
