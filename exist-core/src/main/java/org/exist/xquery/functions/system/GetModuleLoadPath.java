/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.system;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

public class GetModuleLoadPath extends BasicFunction {

    protected final static Logger logger = LogManager.getLogger(GetModuleLoadPath.class);

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-module-load-path", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			"Returns the module load path from the current query context. The module load path " +
            "corresponds to the source location from where this module is loaded." +
            "The module load path is also used to resolve relative XInclude paths.",
			FunctionSignature.NO_ARGS,
			new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the module load path"));

    public GetModuleLoadPath(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        return new StringValue(this, context.getModuleLoadPath());
    }
}
