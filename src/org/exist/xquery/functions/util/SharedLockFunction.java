/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;


public class SharedLockFunction extends LockFunction {

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("shared-lock", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Puts a shared lock on the owner documents of all nodes in the first argument $a. " +
			"Then evaluates the expressions in the second argument $b and releases the acquired locks after" +
			"their completion.",
			new SequenceType[] {
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_MORE),
				new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
			},
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE));
    
    public SharedLockFunction(XQueryContext context) {
        super(context, signature, false);
    }
}
