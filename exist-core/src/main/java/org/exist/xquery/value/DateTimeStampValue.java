/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.value;

import org.exist.xquery.ErrorCodes;
import org.exist.xquery.XPathException;

import javax.xml.XMLConstants;

import javax.xml.datatype.DatatypeConstants;
import javax.xml.datatype.XMLGregorianCalendar;
import javax.xml.namespace.QName;

public class DateTimeStampValue extends DateTimeValue {

    private static final QName XML_SCHEMA_TYPE = new QName(XMLConstants.W3C_XML_SCHEMA_NS_URI, "dateTimeStamp");

    public DateTimeStampValue(final XMLGregorianCalendar calendar) throws XPathException {
        super(calendar);
        checkValidTimezone();
    }

    public DateTimeStampValue(final String dateTime) throws XPathException {
        super(dateTime);
        checkValidTimezone();
    }

    private void checkValidTimezone() throws XPathException {
        if(calendar.getTimezone() == DatatypeConstants.FIELD_UNDEFINED) {
            throw new XPathException(ErrorCodes.ERROR, "Unable to create xs:dateTimeStamp, timezone missing.");
        }
    }

    @Override
    public AtomicValue convertTo(final int requiredType) throws XPathException {
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
    protected AbstractDateTimeValue createSameKind(final XMLGregorianCalendar cal) throws XPathException {
        return new DateTimeStampValue(cal);
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
