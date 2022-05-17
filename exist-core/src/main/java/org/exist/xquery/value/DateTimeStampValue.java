package org.exist.xquery.value;

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

import javax.xml.XMLConstants;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

public class DateTimeStampValue extends DateTimeValue {

    private static final QName XML_SCHEMA_TYPE = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI, "dateTimeStamp");

    public DateTimeStampValue(XMLGregorianCalendar calendar) throws XPathException {
        super(calendar);
        checkValidTimezone();
    }

    public DateTimeStampValue(String dateTime) throws XPathException {
        super(dateTime);
        checkValidTimezone();
    }

    private void checkValidTimezone() throws XPathException {
        if(calendar.getTimezone() == DatatypeConstants.FIELD_UNDEFINED) {
            throw new XPathException(ErrorCodes.ERROR, "Unable to create xs:dateTimeStamp, timezone missing.");
        }
    }

    @Override
    public AtomicValue convertTo(int requiredType) throws XPathException {
        switch (requiredType) {
            case Type.DATE_TIME_STAMP:
                return this;
            case Type.DATE_TIME:
                return new DateTimeValue(calendar);
            default: return
                    super.convertTo(requiredType);
        }
    }

    @Override
    public int getType() {
        return Type.DATE_TIME_STAMP;
    }

    @Override
    protected QName getXMLSchemaType() {
        return XML_SCHEMA_TYPE;
    }
}
