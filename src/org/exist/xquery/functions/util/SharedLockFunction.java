/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;


public class SharedLockFunction extends LockFunction {

	protected static final Logger logger = LogManager.getLogger(SharedLockFunction.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("shared-lock", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Puts a shared lock on the owner documents of all nodes in the first argument $nodes. " +
			"Then evaluates the expressions in the second argument $expression and releases the acquired locks after" +
			"their completion.",
			new SequenceType[] {
				new FunctionParameterSequenceType("nodes", Type.NODE, Cardinality.ZERO_OR_MORE, "The nodes that the shared lock will be placed on their owning documents."),
				new FunctionParameterSequenceType("expression", Type.ITEM, Cardinality.ZERO_OR_MORE, "The expression to be evaluated before the acquired locks are released.")
			},
			new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE, "the results of the evaluation of the expression(s)"));
    
    public SharedLockFunction(XQueryContext context) {
        super(context, signature, false);
    }
}
