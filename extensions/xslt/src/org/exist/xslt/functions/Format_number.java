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
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.NumericValue;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.lang.String;
import java.text.DecimalFormat;

/**
 * format-number($value as numeric?, $picture as xs:string) as xs:string 
 * format-number($value as numeric?, $picture as xs:string, $decimal-format-name as xs:string) as xs:string 
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Format_number extends BasicFunction {

    private static final SequenceType NUMBER_PARAMETER = new FunctionParameterSequenceType("number", Type.NUMBER, Cardinality.ZERO_OR_ONE, "The number to format");
    private static final SequenceType FORMAT_PARAMETER = new FunctionParameterSequenceType("format", Type.STRING, Cardinality.EXACTLY_ONE, "The format pattern string.  Please see the JavaDoc for java.text.DecimalFormat to get the specifics of this format string.");
    private static final SequenceType DECIMAL_FORMAT_PARAMETER = new FunctionParameterSequenceType("decimalformat", Type.STRING, Cardinality.EXACTLY_ONE, "The decimal-format name must be a QName, which is expanded as described in [2.4 Qualified Names]. It is an error if the stylesheet does not contain a declaration of the decimal-format with the specified expanded-name.");
    private static final FunctionReturnSequenceType FUNCTION_RETURN_TYPE = new FunctionReturnSequenceType(Type.STRING, Cardinality.ONE, "the formatted string");
    private static final String FORMAT_NUMBER_DESCRIPTION = "The format-number function converts its first argument to a string using the format pattern" +
            " string specified by the second argument and the decimal-format named by the third argument, or the default decimal-format, if there is no" +
            " third argument. The format pattern string is in the syntax specified by the JDK 1.1 DecimalFormat class. The format pattern string is in a" +
            " localized notation: the decimal-format determines what characters have a special meaning in the pattern (with the exception of the quote character," +
            " which is not localized). The format pattern must not contain the currency sign (#x00A4); support for this feature was added after the initial release" +
            " of JDK 1.1. The decimal-format name must be a QName, which is expanded as described in [2.4 Qualified Names]. It is an error if the stylesheet does" +
            " not contain a declaration of the decimal-format with the specified expanded-name.";
    private static final String FORMAT_DECIMAL_NUMBER_DESCRIPTION = "The format-number function converts its first argument to a string using the format pattern" +
            " string specified by the second argument and the decimal-format named by the third argument, or the default decimal-format, if there is no" +
            " third argument. The format pattern string is in the syntax specified by the JDK 1.1 DecimalFormat class. The format pattern string is in a" +
            " localized notation: the decimal-format determines what characters have a special meaning in the pattern (with the exception of the quote character," +
            " which is not localized). The format pattern must not contain the currency sign (#x00A4); support for this feature was added after the initial release" +
            " of JDK 1.1. The decimal-format name must be a QName, which is expanded as described in [2.4 Qualified Names]. It is an error if the stylesheet does" +
            " not contain a declaration of the decimal-format with the specified expanded-name. NOTE: The decimalformat parameter is currently not implemented and is ignored.";
    public final static FunctionSignature signatures[] = {
		new FunctionSignature(
				new QName("format-number", XSLModule.NAMESPACE_URI, XSLModule.PREFIX),
                FORMAT_NUMBER_DESCRIPTION,
				new SequenceType[] {NUMBER_PARAMETER, FORMAT_PARAMETER},
                FUNCTION_RETURN_TYPE
		),
		new FunctionSignature(
				new QName("format-number", XSLModule.NAMESPACE_URI, XSLModule.PREFIX),
                FORMAT_DECIMAL_NUMBER_DESCRIPTION,
				new SequenceType[] {NUMBER_PARAMETER, FORMAT_PARAMETER, DECIMAL_FORMAT_PARAMETER},
                FUNCTION_RETURN_TYPE
		)
	};
	
	/**
	 * @param context
	 */
	public Format_number(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {

        if (args[0].isEmpty())
            return Sequence.EMPTY_SEQUENCE;
        
        NumericValue numericValue = (NumericValue)args[0].itemAt(0);

        try {
            String value = new DecimalFormat(args[1].getStringValue()).format(numericValue.getDouble());
            return new StringValue(value);
        } catch (java.lang.IllegalArgumentException e) {
            throw new XPathException(e.getMessage());
        }
	}

}
