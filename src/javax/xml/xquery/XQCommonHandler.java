package javax.xml.xquery;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public interface XQCommonHandler {

    public XQItem fromObject(Object obj) throws XQException;

    public Object toObject(XQItemAccessor item) throws XQException;
}
