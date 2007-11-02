/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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

package org.exist.xquery.functions.util;

import org.exist.dom.QName;
import org.exist.security.UUIDGenerator;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
/**
 *
 * @author wessels
 */
public class UUID extends Function {
    
    public final static FunctionSignature signature =
            new FunctionSignature(
            new QName("uuid", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Generate a Universally Unique Identifier string.",
            FunctionSignature.NO_ARGS,
            new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE));
    
    public UUID(XQueryContext context) {
        super(context, signature);
    }
    
        /* (non-Javadoc)
         * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.xquery.value.Sequence, org.exist.xquery.value.Item)
         */
    public Sequence eval(Sequence contextSequence, Item contextItem)
    throws XPathException {
        
        String uuid = UUIDGenerator.getUUID();
        
        if(uuid==null) {
            throw new XPathException("Could not create UUID.");
        }
        
        return new StringValue(uuid);
    }
    
}
