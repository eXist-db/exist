package javax.xml.xquery;

import java.io.InputStream;
import java.io.Reader;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public interface XQConnection extends XQDataFactory, XQStaticContext {

    void clearWarnings() throws XQException;

    void close() throws XQException;

    void commit() throws XQException;

    XQExpression createExpression() throws XQException;

    int getHoldability() throws XQException;

    XQMetaData getMetaData() throws XQException;

    String getMetaDataProperty(String key) throws XQException;

    int getQueryLanguageTypeAndVersion() throws XQException;

    int getScrollability() throws XQException;

    String[] getSupportedMetaDataPropertyNames() throws XQException;

    int getUpdatability() throws XQException;

    XQWarning getWarnings() throws XQException;

    boolean isClosed();

    XQPreparedExpression prepareExpression(InputStream xquery) throws XQException;

    XQPreparedExpression prepareExpression(InputStream xquery, XQItemType contextItemType) throws XQException;

    XQPreparedExpression prepareExpression(Reader xquery) throws XQException;

    XQPreparedExpression prepareExpression(Reader xquery, XQItemType contextItemType) throws XQException;

    XQPreparedExpression prepareExpression(String xquery) throws XQException;

    XQPreparedExpression prepareExpression(String xquery, XQItemType contextItemType) throws XQException;

    void rollback() throws XQException;

    void setCommonHandler(XQCommonHandler handler) throws XQException;

    void setHoldability(int holdability) throws XQException;

    void setQueryLanguageTypeAndVersion(int langtype) throws XQException;

    void setScrollability(int scrollability) throws XQException;

    void setUpdatability(int updatability) throws XQException;
}
