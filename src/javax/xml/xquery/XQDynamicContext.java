package javax.xml.xquery;

import java.util.TimeZone;

import javax.xml.namespace.QName;

import org.w3c.dom.Node;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public interface XQDynamicContext {

    void bindAtomicValue(QName varname, String value, XQItemType type) throws XQException;

    void bindBoolean(QName varname, boolean value, XQItemType type) throws XQException;

    void bindByte(QName varName, byte value, XQItemType type) throws XQException;

    void bindContextItem(XQItem contextitem) throws XQException;

    void bindDocument(QName varname, org.xml.sax.InputSource source) throws XQException;

    void bindDouble(QName varName, double value, XQItemType type) throws XQException;

    void bindFloat(QName varName, float value, XQItemType type) throws XQException;

    void bindInt(QName varName, int value, XQItemType type) throws XQException;

    void bindItem(QName varName, XQItem value) throws XQException;

    void bindLong(QName varName, long value, XQItemType type) throws XQException;

    void bindNode(QName varName, Node value, XQItemType type) throws XQException;

    void bindObject(QName varName, Object value, XQItemType type) throws XQException;

    void bindSequence(QName varName, XQSequence value) throws XQException;

    void bindShort(QName varName, short value, XQItemType type) throws XQException;

    TimeZone getImplicitTimeZone() throws XQException;

    void setImplicitTimeZone(TimeZone implicitTimeZone) throws XQException;
}
