package javax.xml.xquery;

import javax.xml.namespace.QName;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public interface XQPreparedExpression extends XQDynamicContext {

    void cancel() throws XQException;

    void clearWarnings() throws XQException;
    void close() throws XQException;

    XQResultSequence executeQuery() throws XQException;

    QName[] getAllExternalVariables() throws XQException;

    QName[] getUnboundExternalVariables() throws XQException;

    int getQueryTimeout() throws XQException;

    XQSequenceType getStaticResultType() throws XQException;

    XQSequenceType getStaticVariableType(QName name) throws XQException;

    XQWarning getWarnings() throws XQException;

    boolean isClosed();

    void setQueryTimeout(int seconds) throws XQException;






}
