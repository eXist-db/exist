/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2010 The eXist Project
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
 *  $Id $
 */
package org.exist.xquery.functions.xmldb;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: lcahlander
 * Date: Jul 13, 2010
 * Time: 1:51:11 PM
 * To change this template use File | Settings | File Templates.
 */
public class XMLDBGetCurrentUserAttributeNames extends BasicFunction {
    protected static final Logger logger = Logger.getLogger(XMLDBGetCurrentUserAttribute.class);

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("get-current-user-attribute-names", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
            "Returns the names of the attributes of the current user from the xquery context.",
            null,
            new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE, "the attribute names of the current user")
        );

    public XMLDBGetCurrentUserAttributeNames(XQueryContext context, FunctionSignature signature)
    {
        super(context, signature);
    }

    public Sequence eval(Sequence args[], Sequence contextSequence) throws XPathException
    {
        Set<String> values = context.getUser().getAttributeNames();
        Sequence retval = new ValueSequence();
        for (String value : values)
        {
            retval.add(new StringValue(value));
        }
        return retval;
    }
}
