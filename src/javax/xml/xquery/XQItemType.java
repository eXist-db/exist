package javax.xml.xquery;

import java.net.URI;

import javax.xml.namespace.QName;

/**
 * XQJ interfaces reconstructed from version 0.5 documentation
 */
public interface XQItemType extends XQSequenceType {

    static int XQBASETYPE_ANYSIMPLETYPE = 4;
    static int XQBASETYPE_ANYTYPE = 3;
    static int XQBASETYPE_ANYURI = 9;
    static int XQBASETYPE_BASE64BINARY = 10;
    static int XQBASETYPE_BOOLEAN = 11;
    static int XQBASETYPE_BYTE = 32;
    static int XQBASETYPE_DATE = 12;
    static int XQBASETYPE_DATETIME = 17;
    static int XQBASETYPE_DECIMAL = 18;
    static int XQBASETYPE_DOUBLE = 19;
    static int XQBASETYPE_DURATION = 20;
    static int XQBASETYPE_ENTITIES = 51;
    static int XQBASETYPE_ENTITY = 49;
    static int XQBASETYPE_FLOAT = 21;
    static int XQBASETYPE_GDAY = 22;
    static int XQBASETYPE_GMONTH = 23;
    static int XQBASETYPE_GMONTHDAY = 24;
    static int XQBASETYPE_GYEAR = 25;
    static int XQBASETYPE_GYEARMONTH = 26;
    static int XQBASETYPE_HEXBINARY = 27;
    static int XQBASETYPE_ID = 47;
    static int XQBASETYPE_IDREF = 48;
    static int XQBASETYPE_IDREFS = 50;
    static int XQBASETYPE_INT = 13;
    static int XQBASETYPE_INTEGER = 14;
    static int XQBASETYPE_LANGUAGE = 43;
    static int XQBASETYPE_LONG = 16;
    static int XQBASETYPE_NAME = 44;
    static int XQBASETYPE_NCNAME = 45;
    static int XQBASETYPE_NEGATIVE_INTEGER = 35;
    static int XQBASETYPE_NMTOKEN = 46;
    static int XQBASETYPE_NMTOKENS = 52;
    static int XQBASETYPE_NONNEGATIVE_INTEGER = 34;
    static int XQBASETYPE_NONPOSITIVE_INTEGER = 33;
    static int XQBASETYPE_NORMALIZED_STRING = 41;
    static int XQBASETYPE_NOTATION = 28;
    static int XQBASETYPE_POSITIVE_INTEGER = 36;
    static int XQBASETYPE_QNAME = 29;
    static int XQBASETYPE_SHORT = 15;
    static int XQBASETYPE_STRING = 30;
    static int XQBASETYPE_TIME = 31;
    static int XQBASETYPE_TOKEN = 42;
    static int XQBASETYPE_UNSIGNED_BYTE = 40;
    static int XQBASETYPE_UNSIGNED_INT = 38;
    static int XQBASETYPE_UNSIGNED_LONG = 37;
    static int XQBASETYPE_UNSIGNED_SHORT = 39;
    static int XQBASETYPE_XDT_ANYATOMICTYPE = 5;
    static int XQBASETYPE_XDT_DAYTIMEDURATION = 8;
    static int XQBASETYPE_XDT_UNTYPED = 1;
    static int XQBASETYPE_XDT_UNTYPEDATOMIC = 6;
    static int XQBASETYPE_XDT_YEARMONTHDURATION = 8;
    static int XQBASETYPE_XQJ_COMPLEX = 2;
    static int XQBASETYPE_XQJ_LISTTYPE = 53;
    static int XQITEMKIND_ATOMIC = 1;
    static int XQITEMKIND_ATTRIBUTE = 2;
    static int XQITEMKIND_COMMENT = 3;
    static int XQITEMKIND_DOCUMENT = 4;
    static int XQITEMKIND_DOCUMENT_ELEMENT = 5;
    static int XQITEMKIND_ELEMENT = 6;    
    static int XQITEMKIND_ITEM = 7;
    static int XQITEMKIND_NODE = 8;
    static int XQITEMKIND_PI = 9;
    static int XQITEMKIND_TEXT = 10;

    int getBaseType();

    int getItemKind();

    int getItemOccurrence();

    QName getNodeName() throws XQException;

    URI getSchemaURI();

    String getString() throws XQException;

    QName getTypeName() throws XQException;

    boolean isAnonymousType();

    boolean isElementNillable();

    boolean isSchemaElement();


}
