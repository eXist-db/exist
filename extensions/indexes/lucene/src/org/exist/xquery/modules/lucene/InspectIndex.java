/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.xquery.modules.lucene;

import java.io.IOException;
import java.net.URISyntaxException;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.dom.persistent.LockedDocument;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class InspectIndex extends BasicFunction {

	public final static FunctionSignature[] signatures = {
        new FunctionSignature(
                new QName("has-index", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                "Check if the given document has a lucene index defined on it. This method " +
                "will return true for both, indexes created via collection.xconf or manual index " +
                "fields added to the document with ft:index.",
                new SequenceType[] {
                    new FunctionParameterSequenceType("path", Type.STRING, Cardinality.EXACTLY_ONE, 
                    		"Full path to the resource to check")
                },
                new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.ZERO_OR_MORE, "")
            )
	};
	
	public InspectIndex(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		String path = args[0].itemAt(0).getStringValue();

        try(final LockedDocument lockedDoc = context.getBroker().getXMLResource(XmldbURI.xmldbUriFor(path), LockMode.READ_LOCK)) {
			// Retrieve document from database

			// Verify the document actually exists
			if (lockedDoc == null) {
			    throw new XPathException(this, "Document " + path + " does not exist.");
			}
			
			final LuceneIndexWorker index = (LuceneIndexWorker)
				context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
			return new BooleanValue(index.hasIndex(lockedDoc.getDocument().getDocId()));
		} catch (PermissionDeniedException e) {
			throw new XPathException(this, LuceneModule.EXXQDYFT0001, e.getMessage());
		} catch (URISyntaxException e) {
			throw new XPathException(this, LuceneModule.EXXQDYFT0003, e.getMessage());
		} catch (IOException e) {
			throw new XPathException(this, LuceneModule.EXXQDYFT0002, e.getMessage());
		}
	}

}
