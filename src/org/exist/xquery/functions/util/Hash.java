/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xquery.functions.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.security.MessageDigester;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * Generate a massage digest (hashcode) from a string. Typically supported
 * algorithms are MD5 and SHA1.
 *
 * @author dizzzz@exist-db.org
 */
public class Hash extends BasicFunction {
	protected static final Logger logger = LogManager.getLogger(Hash.class);
	
	private static final FunctionParameterSequenceType message = new FunctionParameterSequenceType("message", Type.ITEM, Cardinality.EXACTLY_ONE, "The string to generate the hashcode from");
	private static final FunctionParameterSequenceType algorithm = new FunctionParameterSequenceType("algorithm", Type.STRING, Cardinality.EXACTLY_ONE, "The algorithm used to generate the hashcode");
	private static final FunctionParameterSequenceType base64flag = new FunctionParameterSequenceType("base64flag", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "The flag that specifies whether to return the result as Base64 encoded");
	private static final FunctionReturnSequenceType result = new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the hashcode");
	
    public final static FunctionSignature signatures[] = {
            new FunctionSignature(
                    new QName("hash", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Calculates a hashcode from a string based on a specified algorithm.",
                    new SequenceType[]{ message, algorithm },
                    result),

            new FunctionSignature(
                    new QName("hash", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
                    "Calculates a hashcode from a string based on a specified algorithm.",
                    new SequenceType[]{ message, algorithm, base64flag },
                    result)
    };

    public Hash(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /* (non-Javadoc)
      * @see org.exist.xquery.Expression#eval(org.exist.dom.persistent.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
      */
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
    	
        boolean base64 = false;

        final String message = args[0].itemAt(0).getStringValue();
        final String algorithm = args[1].itemAt(0).getStringValue();

        if (args.length > 2) {
            base64 = args[2].effectiveBooleanValue();
        }

        String md = null;
        try {
            md = MessageDigester.calculate(message, algorithm, base64);

        } catch (final IllegalArgumentException ex) {
            throw new XPathException(ex.getMessage());
        }

        return (new StringValue(md));
    }

}
