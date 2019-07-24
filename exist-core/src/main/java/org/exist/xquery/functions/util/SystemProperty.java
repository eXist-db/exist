/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2004-09 The eXist Project
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

import org.exist.SystemProperties;
import org.exist.dom.QName;
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
 * Libary function to retrieve the value of a system property.
 * @author Wolfgang Meier
 * @author Loren Cahlander
 */
public class SystemProperty extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
        new QName("system-property", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
        "Returns the value of a system property. Similar to the corresponding XSLT function. " +
        "Predefined properties are: vendor, vendor-url, product-name, product-version, product-build, and all Java " +
        "system properties.",
        new SequenceType[] { 
            new FunctionParameterSequenceType("property-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the system property to retrieve the value of.")
        },
        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the value of the named system property")
    );

    public SystemProperty(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        final String key = args[0].getStringValue();
        String value = SystemProperties.getInstance().getSystemProperty(key, null);
        if(value == null) {
                value = System.getProperty(key);
        }
        return value == null ? Sequence.EMPTY_SEQUENCE : new StringValue(value);
    }
}