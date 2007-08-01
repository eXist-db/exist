package javax.xml.xquery;

import java.io.IOException;
import java.net.URI;

import javax.xml.namespace.QName;

import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public interface XQDataFactory {

    XQItemType createAtomicItemType(int baseType) throws XQException;

    XQItem createItem(XQItem item) throws XQException;

    XQItem createItemFromAtomicValue(String value, XQItemType type) throws XQException;

    XQItem createItemFromBoolean(boolean value, XQItemType type) throws XQException;

    XQItem createItemFromByte(byte value, XQItemType type) throws XQException;

    XQItem createItemFromDocument(InputSource value) throws XQException, IOException;

    XQItem createItemFromDouble(double value, XQItemType type) throws XQException;

    XQItem createItemFromFloat(float value, XQItemType type) throws XQException;

    XQItem createItemFromInt(int value, XQItemType type) throws XQException;

    XQItem createItemFromLong(long value, XQItemType type) throws XQException;

    XQItem createItemFromNode(Node value, XQItemType type) throws XQException;

    XQItem createItemFromObject(Object value, XQItemType type)  throws XQException;

    XQItem createItemFromShort(short value, XQItemType type) throws XQException;

    XQItemType createItemType(int itemkind, int basetype, QName nodename) throws XQException;

    XQItemType createItemType(int itemkind, int basetype, QName nodename,
                              QName typename, URI schemaURI, boolean nillable)  throws XQException;

    XQSequence createSequence(java.util.Iterator i) throws XQException;

    XQSequence createSequence(XQSequence s) throws XQException;

    XQSequenceType createSequenceType(XQItemType item, int occurrence) throws XQException;

}
