/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2013 The eXist-db Project
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
package org.exist.xquery.functions.util;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class BaseConversionFunctions extends BasicFunction {
    
    private final static QName qnIntToOctal = new QName("int-to-octal", UtilModule.NAMESPACE_URI, UtilModule.PREFIX);
    private final static QName qnOctalToInt = new QName("octal-to-int", UtilModule.NAMESPACE_URI, UtilModule.PREFIX);
    
    public final static FunctionSignature FNS_INT_TO_OCTAL = new FunctionSignature(
        qnIntToOctal,
        "Converts an int e.g. 511 to an octal number e.g. 0777.",
        new SequenceType[] {
            new FunctionParameterSequenceType("int", Type.INT, Cardinality.EXACTLY_ONE, "The int to convert to an octal string.")
        },
        new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
    );
    
    public final static FunctionSignature FNS_OCTAL_TO_INT = new FunctionSignature(
        qnOctalToInt,
        "Converts an octal string e.g. '0777' to an int e.g. 511.",
        new SequenceType[] {
            new FunctionParameterSequenceType("octal", Type.STRING, Cardinality.EXACTLY_ONE, "The octal string to convert to an int.")
        },
        new SequenceType(Type.INT, Cardinality.EXACTLY_ONE)
    );
    
    public BaseConversionFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }
	
    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if(isCalledAs(qnIntToOctal.getLocalPart())) {
            final int i = args[0].toJavaObject(Integer.class);
            final String octal = i == 0 ? "0" : "0" + Integer.toOctalString(i);
            return new StringValue(octal);
        } else if(isCalledAs(qnOctalToInt.getLocalPart())) {
            final String octal = args[0].toString();
            return new IntegerValue(Integer.parseInt(octal, 8));
        } else {
            throw new XPathException("Unknown function call: " + getSignature());
        }
    }
}
