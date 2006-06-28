package javax.xml.xquery;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public class XQQueryException extends XQException {

    private String errorCode;
    private String expr;
    private XQItem errorItem;
    private int lineNumber;
    private int position;
    private XQStackTraceElement[] trace;


    XQQueryException(java.lang.String message, java.lang.Throwable cause, java.lang.String vendorCode,
                     XQException nextException, java.lang.String errorCode, java.lang.String expr,
                     XQItem errorItem, int lineNumber, int position, XQStackTraceElement[] trace) {
        super(message, cause, vendorCode, nextException);
        this.errorCode = errorCode;
        this.expr = expr;
        this.errorItem = errorItem;
        this.lineNumber = lineNumber;
        this.position = position;
        this.trace = trace;

    }

    java.lang.String getErrorCode() {
        return errorCode;
    }

    XQItem getErrorItem() {
        return errorItem;
    }

    java.lang.String getExpression() {
        return expr;
    }

    int getLineNumber() {
        return lineNumber;
    }

    int getPosition() {
        return position;
    }

    XQStackTraceElement[] getXQStackTrace() {
        return trace;
    }
}
