/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2014 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.indexing.range.conversion;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.exist.indexing.range.RangeIndexConfigElement;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.DateValue;
import org.exist.xquery.value.TimeUtils;

import javax.xml.datatype.XMLGregorianCalendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple normalization of dates: if there is only a year, transform it into a date: yyy-01-01.
 * If full date is given, but with missing digits: fill them in.
 */
public class DateConverter implements TypeConverter {

    protected static final Logger LOG = LogManager.getLogger(DateConverter.class);

    private final static Pattern DATE_REGEX = Pattern.compile("(-?\\d+)-(\\d+)-(\\d+)");

    @Override
    public Field toField(String fieldName, String content) {
        try {
            DateValue dv;
            if (content.indexOf('-') < 1) {
                // just year
                int year = Integer.parseInt(content);
                XMLGregorianCalendar calendar = TimeUtils.getInstance().newXMLGregorianCalendar();
                calendar.setYear(year);
                calendar.setDay(1);
                calendar.setMonth(1);
                dv = new DateValue(calendar);
            } else {
                // try to handle missing digits as in 1980-8-4
                Matcher matcher = DATE_REGEX.matcher(content);
                if (matcher.matches()) {
                    try {
                        int year = Integer.parseInt(matcher.group(1));
                        content = (year < 0 ? "-" : "") + String.format("%04d-%02d-%02d", Math.abs(year), Integer.parseInt(matcher.group(2)), Integer.parseInt(matcher.group(3)));
                    } catch (NumberFormatException e) {
                        // invalid content: ignore
                    }
                }
                dv = new DateValue(content);
            }
            final long dl = RangeIndexConfigElement.dateToLong(dv);
            return new LongField(fieldName, dl, LongField.TYPE_NOT_STORED);
        } catch (XPathException e) {
            // wrong type: ignore
            LOG.debug("Invalid date format: " + content, e);
        } catch (NumberFormatException e) {
            // wrong type: ignore
            LOG.debug("Invalid date format: " + content, e);
        } catch (Exception e) {
            LOG.debug("Invalid date format: " + content, e);
        }
        return null;
    }
}
