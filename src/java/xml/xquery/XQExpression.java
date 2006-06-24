package javax.xml.xquery;

import java.io.InputStream;
import java.io.Reader;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public interface XQExpression extends XQDynamicContext {

    void cancel() throws XQException;

    void clearWarnings() throws XQException;

    void close();

    void executeCommand(Reader command) throws XQException;

    void executeCommand(String command) throws XQException;

    XQResultSequence executeQuery(InputStream query) throws XQException;

    XQResultSequence executeQuery(Reader query) throws XQException;

    XQResultSequence executeQuery(String query) throws XQException;

    int getQueryLanguageTypeAndVersion() throws XQException;

    int getQueryTimeout() throws XQException;

    XQWarning getWarnings() throws XQException;

    boolean isClosed();

    void setQueryTimeout(int seconds) throws XQException;



}
