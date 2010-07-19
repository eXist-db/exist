/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *
 *  $Id: Format_date.java 11970 2010-07-15 19:33:11Z shabanovd $
 */
package org.exist.xslt.functions;

import org.exist.xquery.Cardinality;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;

import java.lang.String;

/**
 * format-date( $value  as xs:date?, $picture  as xs:string,
 * 	$language  as xs:string?, $calendar  as xs:string?,
 * 	$country  as xs:string?) as xs:string?
 *
 * format-date($value as xs:date?, $picture as xs:string) as xs:string?
 *
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class FormatFunctionConstants {

    public static final SequenceType DATE_PARAMETER = new FunctionParameterSequenceType("date", Type.DATE, Cardinality.ZERO_OR_ONE, "The date value");
    public static final SequenceType DATE_TIME_PARAMETER = new FunctionParameterSequenceType("date-TIME", Type.DATE_TIME, Cardinality.ZERO_OR_ONE, "The date-time value");
    public static final SequenceType TIME_PARAMETER = new FunctionParameterSequenceType("time", Type.TIME, Cardinality.ZERO_OR_ONE, "The time value");
    public static final SequenceType PICTURE_PARAMETER = new FunctionParameterSequenceType("picture", Type.STRING, Cardinality.ONE, "The picture string");
    public static final SequenceType LANGUAGE_PARAMETER = new FunctionParameterSequenceType("language", Type.STRING, Cardinality.ZERO_OR_ONE, "The language parameter");
    public static final SequenceType CALENDAR_PARAMETER = new FunctionParameterSequenceType("calendar", Type.STRING, Cardinality.ZERO_OR_ONE, "The calendar parameter");
    public static final SequenceType COUNTRY_PARAMETER = new FunctionParameterSequenceType("country", Type.STRING, Cardinality.ZERO_OR_ONE, "The country parameter");
    public static final SequenceType RETURN_TYPE = new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "The formatted string");
    public static final String DATE_FUNCTION_DESCRIPTION = "The functions formats a date as a string using the picture string.";
    public static final String DATE_TIME_FUNCTION_DESCRIPTION = "The functions formats a dateTime as a string using the picture string.";
    public static final String TIME_FUNCTION_DESCRIPTION = "The functions formats a time as a string using the picture string.";

    public static String translate(String input) {
        StringBuffer buffer = new StringBuffer(input);
        for (int index = 0; index < buffer.length(); index++) {
            char testChar = buffer.charAt(index);
            switch (testChar) {
                case 'Y': buffer.setCharAt(index, 'y'); break;
                case 'D': buffer.setCharAt(index, 'd'); break;
                case 'd': buffer.setCharAt(index, 'D'); break;
                case 'F': buffer.setCharAt(index, 'E'); break;
                case 'W': buffer.setCharAt(index, 'w'); break;
                case 'w': buffer.setCharAt(index, 'W'); break;
                case 'P': buffer.setCharAt(index, 'a'); break;
                case 'f': buffer.setCharAt(index, 'S'); break;
                case 'E': buffer.setCharAt(index, 'G'); break;
            }
        }
        return buffer.toString();
    }

}
