/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2006-2014 The eXist Project
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
package org.exist.xquery.functions.xmldb;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.securitymanager.IdFunction;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 *
 */
@Deprecated
public class XMLDBGetCurrentUser extends BasicFunction {
    
    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("get-current-user", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Returns the user-id of the current user from the xquery context.",
            null,
            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the user-id of the current user"),
            IdFunction.FNS_ID
        );

    public XMLDBGetCurrentUser(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence args[], final Sequence contextSequence) throws XPathException {
        return new StringValue(context.getEffectiveUser().getName());
    }
}
