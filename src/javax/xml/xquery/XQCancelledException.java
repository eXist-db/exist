package javax.xml.xquery;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public class XQCancelledException extends XQQueryException {


    XQCancelledException(String message, Throwable cause, String vendorCode,
                     XQException nextException, String errorCode, String expr,
                     XQItem errorItem, int lineNumber, int position, XQStackTraceElement[] trace) {
        super(message, cause, vendorCode, nextException, errorCode, expr, errorItem, lineNumber, position, trace);
    }

}
