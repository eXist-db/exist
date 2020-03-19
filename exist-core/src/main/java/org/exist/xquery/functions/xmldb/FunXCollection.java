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
package org.exist.xquery.functions.xmldb;

import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.fn.ExtCollection;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;

import java.net.URI;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.xmldb.XMLDBModule.functionSignature;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class FunXCollection extends ExtCollection {

	private static final String FS_XCOLLECTION_NAME = "xcollection";
	static final FunctionSignature FS_XCOLLECTION = functionSignature(
			FS_XCOLLECTION_NAME,
			"Returns the document nodes in the collections $collection-uris " +
					"non-recursively, i.e. does not include document nodes found in " +
					"sub-collections. " +
					"C.f. fn:collection().",
			returnsOptMany(Type.ITEM, "The items from the specified collections excluding sub-collections"),
			params(
				manyParam("args", Type.STRING,"The Collection URI")
			)
	);

	public FunXCollection(final XQueryContext context) {
		super(context, FS_XCOLLECTION, false);
	}

	@Override
	public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
		final int len = args[0].getItemCount();
		final URI[] collectionUris = new URI[len];
		for (int i = 0; i < len; i++) {
			collectionUris[i] = asUri(args[0].itemAt(i).getStringValue());
		}

		return getCollectionItems(collectionUris);
	}
}
