package javax.xml.xquery;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public interface XQResultSequence extends XQItemAccessor, XQSequence {

    void clearWarnings();

    XQConnection getConnection();

    XQWarning getWarnings();
}
