package javax.xml.xquery;

import java.io.OutputStream;
import java.io.Writer;
import java.net.URI;
import java.util.Properties;

import org.w3c.dom.Node;
import org.xml.sax.ContentHandler;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public interface XQItemAccessor {

    String getAtomicValue() throws XQException;

    boolean getBoolean() throws XQException;

    byte getByte() throws XQException;

    double getDouble() throws XQException;

    float getFloat() throws XQException;

    int getInt() throws XQException;

    //XMLStreamReader getItemAsStream() throws XQException;

    String getItemAsString() throws XQException;

    XQItemType getItemType() throws XQException;

    long getLong() throws XQException;

    Node getNode() throws XQException;

    URI getNodeUri() throws XQException;

    Object getObject() throws XQException;

    Object getObject(XQCommonHandler handler) throws XQException;

    short getShort() throws XQException;

    boolean instanceOf(XQItemType type) throws XQException;

    void writeItem(OutputStream os, Properties props) throws XQException;

    void writeItem(Writer ow, Properties props) throws XQException;

    void writeItemToSAX(ContentHandler saxHandler) throws XQException;
}
