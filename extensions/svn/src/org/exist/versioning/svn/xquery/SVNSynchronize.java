/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
package org.exist.versioning.svn.xquery;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class SVNSynchronize extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("synchronize", SVNModule.NAMESPACE_URI, SVNModule.PREFIX), "Synchronize Working Copy with repository",
			new SequenceType[] {
                new FunctionParameterSequenceType("WC-uri", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The location of Working Copy")
            },
            new FunctionParameterSequenceType("synchronize", Type.NODE, Cardinality.ZERO_OR_MORE, "a sequence containing the list of changes"));

	public SVNSynchronize(XQueryContext context) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		// TODO Auto-generated method stub
		return null;
	}

}
