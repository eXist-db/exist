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
 *  $Id$
 */
package org.exist.xslt.functions;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.StringValue;
import org.exist.xslt.functions.FormatFunctionConstants;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * format-dateTime($value as xs:dateTime?, $picture  as xs:string, 
 * 	$language  as xs:string?, $calendar  as xs:string?, 
 * 	$country  as xs:string?) as xs:string? 
 * 
 * format-dateTime($value  as xs:dateTime?, $picture as xs:string) as xs:string? 
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Format_dateTime extends BasicFunction {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
				new QName("format-dateTime", XSLModule.NAMESPACE_URI, XSLModule.PREFIX),
                FormatFunctionConstants.DATE_TIME_FUNCTION_DESCRIPTION,
				new SequenceType[] { FormatFunctionConstants.DATE_TIME_PARAMETER, FormatFunctionConstants.PICTURE_PARAMETER, FormatFunctionConstants.LANGUAGE_PARAMETER, FormatFunctionConstants.CALENDAR_PARAMETER, FormatFunctionConstants.COUNTRY_PARAMETER},
                FormatFunctionConstants.RETURN_TYPE
		),
		new FunctionSignature(
				new QName("format-dateTime", XSLModule.NAMESPACE_URI, XSLModule.PREFIX),
                FormatFunctionConstants.DATE_TIME_FUNCTION_DESCRIPTION,
				new SequenceType[] { FormatFunctionConstants.DATE_TIME_PARAMETER, FormatFunctionConstants.PICTURE_PARAMETER},
                FormatFunctionConstants.RETURN_TYPE

		)
	};
	
	/**
	 * @param context
	 */
	public Format_dateTime(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {

        if (args[0].isEmpty())
            return Sequence.EMPTY_SEQUENCE;

        try {
            DateTimeValue value = (DateTimeValue)args[0].itemAt(0);
            String picture = FormatFunctionConstants.translate(args[1].itemAt(0).getStringValue());
            String language = (args.length <= 2 || args[2].isEmpty()) ? null : args[2].itemAt(0).getStringValue();
//            String calendar = (args.length <= 2 || args[3].isEmpty()) ? null : args[3].itemAt(0).getStringValue();
            String country = (args.length <= 2 || args[4].isEmpty()) ? null : args[4].itemAt(0).getStringValue();
            SimpleDateFormat format = null;

            if (language != null || country != null) {
                Locale locale = (country == null) ? new Locale(language) : new Locale(language, country);
                format = new SimpleDateFormat(picture, locale);
            } else {
                format = new SimpleDateFormat(picture);
            }
            return new StringValue(format.format(value.toJavaObject(java.util.Date.class)));
        } catch (java.lang.IllegalArgumentException e) {
            throw new XPathException(e.getMessage());
        }
	}

}
