/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2009-2014 The eXist-db Project
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
 */
package org.exist.xquery.functions.system;

import org.exist.ExistSystemProperties;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * Return the Git commit ID.
 * 
 * @author Dannes Wessels
 * @author Leif-Jöran Olsson
 */
public class GetRevision extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
        new QName("get-revision", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
        "Returns the Git commit ID of the eXist instance running this query.",
        FunctionSignature.NO_ARGS,
        new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the Git commit ID.")
    );

    public GetRevision(XQueryContext context) {
        super(context, signature);
    }

    /* (non-Javadoc)
     * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
     */
    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        return new StringValue(ExistSystemProperties.getInstance().getExistSystemProperty(ExistSystemProperties.PROP_GIT_COMMIT, "unknown Git commit ID"));
    }
}
