/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.util;

import org.apache.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * Converts the supplied number in given base to other base (currently a base-10 integer).
 *
 * @author ljo
 */
public class BaseConverter extends BasicFunction {
	protected static final Logger logger = Logger.getLogger(BaseConverter.class);
	
	private static final FunctionParameterSequenceType number_param = new FunctionParameterSequenceType("number", Type.ITEM, Cardinality.EXACTLY_ONE, "The number to convert");
	private static final FunctionParameterSequenceType base_param = new FunctionParameterSequenceType("base", Type.STRING, Cardinality.EXACTLY_ONE, "The base of $number");
	private static final FunctionReturnSequenceType int_result = new FunctionReturnSequenceType(Type.INT, Cardinality.EXACTLY_ONE, "the xs:integer representation of $number in base $base");
	
    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("base-to-integer", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Converts the number $number from base $base to xs:integer.",
                    new SequenceType[]{ number_param, base_param },
                    int_result)
    };

    public BaseConverter(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /* (non-Javadoc)
      * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
      */
    public Sequence eval(Sequence[] args, Sequence contextSequence)
        throws XPathException {
    	int intValue;
        String number = args[0].itemAt(0).getStringValue();
        String base = args[1].itemAt(0).getStringValue();
        
        intValue = Integer.parseInt(number, Integer.parseInt(base));

        return new IntegerValue(intValue);
    }

}
