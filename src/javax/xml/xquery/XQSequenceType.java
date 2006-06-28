package javax.xml.xquery;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public interface XQSequenceType {

    static int OCC_EXACTLY_ONE = 2;

    static int OCC_ONE_OR_MORE = 4;

    static int OCC_ZERO_OR_MORE = 3;

    static int OCC_ZERO_OR_ONE = 1;

    int getItemOccurrence();

    XQItemType getItemType();

    java.lang.String getString() throws XQException;
}
