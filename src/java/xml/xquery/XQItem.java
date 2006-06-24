package javax.xml.xquery;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public interface XQItem extends XQItemAccessor {

    void close() throws XQException;

    boolean isClosed();
}
