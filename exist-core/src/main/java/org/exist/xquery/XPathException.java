/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.evolvedbinary.j8fu.function.SupplierE;
import org.exist.source.Source;
import org.exist.xquery.ErrorCodes.ErrorCode;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

/**
 *  Class for representing a generic XPath exception.
 */
public class XPathException extends Exception implements XPathErrorProvider {

    private static final long serialVersionUID = 212844692232650666L;
    private int line = 0;
    private int column = 0;
    private ErrorCode errorCode = ErrorCodes.ERROR;
    private String message = null;
    private Sequence errorVal;
    private List<FunctionStackElement> callStack = null;

    private Source source = null;

    /**
     * @param message the error message
     * @deprecated Use a constructor with errorCode
     */
    @Deprecated
    public XPathException(final String message) {
        super();
        this.message = message;
    }

    /**
     * @param line line number the error appeared in
     * @param column column the error appeared in
     * @param message the error message
     * @deprecated Use a constructor with errorCode
     */
    @Deprecated
    public XPathException(final int line, final int column, final String message) {
        super();
        this.message = message;
        this.line = line;
        this.column = column;
    }

    public XPathException(final ErrorCode errorCode, final int line, final int column) {
        super();
        this.errorCode = errorCode;
        this.line = line;
        this.column = column;
    }

    public XPathException(final int line, final int column, final ErrorCode errorCode, final String message) {
        super();
        this.errorCode = errorCode;
        this.message = message;
        this.line = line;
        this.column = column;
    }

    /**
     * @param expr XPath expression
     * @param message Exception message
     *
     * @deprecated Use a constructor with errorCode
     */
    @Deprecated
    public XPathException(final Expression expr, final String message) {
        super();
        this.message = message;
        if (expr != null) {
            this.line = expr.getLine();
            this.column = expr.getColumn();
            this.source = expr.getSource();
        }
    }

    @Deprecated
    public XPathException(final Sequence sequence, final String message) {
        this((sequence != null && !sequence.isEmpty() && sequence.itemAt(0) != null) ? sequence.itemAt(0).getExpression() : null, message);
    }

    @Deprecated
    public XPathException(final Item item, final String message) {
        this((item != null) ? item.getExpression() : null, message);
    }

    public XPathException(final Expression expr, final ErrorCode errorCode, final String errorDesc) {
        super();
        this.errorCode = errorCode;
        this.message = errorDesc;
        if (expr != null) {
            this.line = expr.getLine();
            this.column = expr.getColumn();
            this.source = expr.getSource();
        }
    }

    public XPathException(final Sequence sequence, final ErrorCode errorCode, final String message) {
        this((sequence != null && !sequence.isEmpty() && sequence.itemAt(0) != null) ? sequence.itemAt(0).getExpression() : null, errorCode, message);
    }

    public XPathException(final Item item, final ErrorCode errorCode, final String message) {
        this((item != null) ? item.getExpression() : null, errorCode, message);
    }

    public XPathException(final ErrorCode errorCode, final String errorDesc, final Sequence errorVal) {
        this(null, errorCode, errorDesc, errorVal);
    }

    public XPathException(final Expression expr, final ErrorCode errorCode, final String errorDesc, final Sequence errorVal) {
        super();
        this.errorCode = errorCode;
        this.message = errorDesc;
        this.errorVal = errorVal;
        if (expr != null) {
            this.line = expr.getLine();
            this.column = expr.getColumn();
            this.source = expr.getSource();
        }
    }

    public XPathException(final Expression expr, final ErrorCode errorCode, final Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
        this.message = cause.getMessage();
        if (expr != null) {
            this.line = expr.getLine();
            this.column = expr.getColumn();
            this.source = expr.getSource();
        }
    }

    /**
     * @param ast ast representation
     * @param message the error message
     * @deprecated Use a constructor with errorCode
     */
    @Deprecated
    public XPathException(final XQueryAST ast, final String message) {
        super();
        this.message = message;
        if(ast != null) {
            this.line = ast.getLine();
            this.column = ast.getColumn();
        }
    }

    public XPathException(final XQueryAST ast, final ErrorCode errorCode, final String message) {
        super();
        this.errorCode=errorCode;
        this.message = message;
        if(ast != null) {
            this.line = ast.getLine();
            this.column = ast.getColumn();
        }
    }

    /**
     * @param cause the cause throwable
     * @deprecated Use a constructor with errorCode
     */
    @Deprecated
    public XPathException(final Throwable cause) {
        super(cause);
        if (cause != null && cause instanceof XPathErrorProvider) {
            this.errorCode = ((XPathErrorProvider)cause).getErrorCode();
        }
    }

    /**
     * @param cause the cause throwable
     * @param message the error message
     * @deprecated Use a constructor with errorCode
     */
    @Deprecated
    public XPathException(final String message, final Throwable cause) {
        this((Expression) null, message, cause);
    }

    public XPathException(final Expression expr, final String message, final Throwable cause) {
        super(cause);
        this.message = message;
        if (cause != null && cause instanceof XPathErrorProvider) {
            this.errorCode = ((XPathErrorProvider)cause).getErrorCode();
        }

        if (expr != null) {
            this.line = expr.getLine();
            this.column = expr.getColumn();
            this.source = expr.getSource();
        }
    }

    /**
     * @param expr expression causing the error
     * @param cause the cause throwable
     * @deprecated Use a constructor with errorCode
     */
    @Deprecated
    public XPathException(final Expression expr, final Throwable cause) {
        this(expr, cause != null && cause instanceof XPathErrorProvider ? ((XPathErrorProvider)cause).getErrorCode() : ErrorCodes.ERROR, cause == null ? "" : cause.getMessage(), null, cause);
    }

    /**
     *  Constructor.
     *
     * @param errorCode Xquery errorcode
     * @param errorDesc Error code. When Null the ErrorCode text will be used.
     *
     */
    public XPathException(final ErrorCode errorCode, final String errorDesc) {
        this.errorCode = errorCode;

        if(errorDesc == null){
            if (errorCode != null) {
                this.message = errorCode.toString();
            }
        } else {
            this.message = errorDesc;
        }
    }

    public XPathException(final ErrorCode errorCode, final String errorDesc, final Sequence errorVal, final Throwable cause) {
        this(errorCode, errorDesc, cause);
        this.errorVal = errorVal;
    }

    public XPathException(final ErrorCode errorCode, final String errorDesc, final Throwable cause) {
        super(cause);
        this.errorCode = errorCode;

        if(errorDesc == null){
            if (errorCode != null) {
                this.message = errorCode.toString();
            }
        } else {
            this.message = errorDesc;
        }
    }

    public XPathException(final Expression expr, final ErrorCode errorCode, final String errorDesc, final Sequence errorVal, final Throwable cause) {
        this(expr, errorCode, errorDesc, cause);
        this.errorVal = errorVal;
    }

    public XPathException(final Expression expr, final ErrorCode errorCode, final String errorDesc, final Throwable cause) {
        super(cause);
        this.errorCode = errorCode;

        if(errorDesc == null){
            if (errorCode != null) {
                this.message = errorCode.toString();
            }
        } else {
            this.message = errorDesc;
        }

        if (expr != null) {
            this.line = expr.getLine();
            this.column = expr.getColumn();
            this.source = expr.getSource();
        }
    }

    /**
     * @param line line number the error appeared in
     * @param column column the error appeared in
     * @param message the error message
     * @param cause the cause throwable
     * @deprecated Use a constructor with errorCode
     */
    @Deprecated
    protected XPathException(final int line, final int column, final String message, final Throwable cause) {
        super(cause);
        this.message = message;
        this.line = line;
        this.column = column;
        if (cause != null && cause instanceof XPathErrorProvider) {
            this.errorCode = ((XPathErrorProvider)cause).getErrorCode();
        }
    }

    /**
     * @param line line number the error appeared in
     * @param column column the error appeared in
     * @param cause the cause throwable
     *
     * @deprecated Use a constructor with errorCode
     */
    @Deprecated
    public XPathException(final int line, final int column, final Throwable cause) {
        super(cause);
        this.line = line;
        this.column = column;
        if(cause instanceof XPathErrorProvider) {
            this.errorCode = ((XPathErrorProvider)cause).getErrorCode();
        }
    }

    public void setLocation(int line, int column) {
        this.line = line;
        this.column = column;
    }

    public void setLocation(int line, int column, Source source) {
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

    /**
     * Get the xquery error code. Use getErroCode instead.
     *
     * @return The error code or ErrorCode#Error when not available.
     *
     * @deprecated Use {@link #getErrorCode()}
     */
    @Deprecated
    public ErrorCode getCode() {
        return errorCode;
    }

    public Source getSource() {
        return source;
    }

    public void addFunctionCall(UserDefinedFunction def, Expression call) {
        if(callStack == null) {
           callStack = new ArrayList<>();
        }
        callStack.add(new FunctionStackElement(def, def.getSource().pathOrShortIdentifier(), call.getLine(), call.getColumn()));
    }

    public List<FunctionStackElement> getCallStack() {
        return callStack;
    }

    public void prependMessage(String msg) {
        message = msg + message;
    }

    public void prependMessage(ErrorCode errorCode, String msg) {
        this.errorCode = errorCode;
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

        // Append location details
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
                buf.append("source: ").append(source.pathOrShortIdentifier());
            }
            buf.append("]");
        }

        // Append function stack trace
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
        if (message == null) {
            message = "";
        }
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

            for (final FunctionStackElement e : callStack) {
                buf.append("<tr><td class=\"func\">").append(e.function).append("</td>");
                buf.append("<td class=\"lineinfo\">").append(e.line).append(':').append(e.column).append("</td>");
                buf.append("</tr>");
            }
            buf.append("</table>");
        }
        return buf.toString();
    }

    @Override
    public ErrorCode getErrorCode() {
        return errorCode;
    }

    /**
     *  Get the xquery error value.
     *
     * @return Error value as sequence
     */
    public Sequence getErrorVal() {
        return errorVal;
    }

    public static class FunctionStackElement {

        private final String function;
        private final String file;
        private final int line;
        private final int column;

        public FunctionStackElement(UserDefinedFunction func, String file, int line, int column) {
            this.function = func.toString();
            this.file = file;
            this.line = line;
            this.column = column;
        }

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

        @Override
        public String toString() {
            return function + " [" + line + ":" + column + ":" + file + ']';
        }
    }

    /**
     * Executes the function, and if the function raises an XPathException
     * and the error information is missing from the exception, it will be added
     * from the calling expression.
     *
     * @param <T> the return type of the function.
     *
     * @param callingExpression the calling expression.
     * @param function the function execute.
     *
     * @return the result of the calling function
     * @throws XPathException if the function throws an XPathException
     */
    public static <T> T execAndAddErrorIfMissing(final Expression callingExpression, final SupplierE<T, XPathException> function) throws XPathException {
        try {
            return function.get();
        } catch (final XPathException e) {
            if (e.getLine() == 0) {
                if (callingExpression.getSource() != null) {
                    e.setLocation(callingExpression.getLine(), callingExpression.getColumn(), callingExpression.getSource());
                } else {
                    e.setLocation(callingExpression.getLine(), callingExpression.getColumn());
                }
            }
            throw e;
        }
    }
}
