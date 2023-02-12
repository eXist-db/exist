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
package org.exist.xquery.modules.lucene;

import org.exist.dom.QName;
import org.exist.dom.persistent.LockedDocument;
import org.exist.indexing.StreamListener.ReindexMode;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class RemoveIndex extends BasicFunction {

	public final static FunctionSignature signature =
            new FunctionSignature(
            new QName("remove-index", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "Remove any (non-XML) Lucene index associated with the document identified by the " +
            "path parameter. This function will only remove indexes which were manually created by " +
            "the user via the ft:index function. Indexes defined in collection.xconf will NOT be " +
            "removed. They are maintained automatically by the database. Please note that non-XML indexes " +
            "will also be removed automatically if the associated document is deleted.",
            new SequenceType[]{
                new FunctionParameterSequenceType("documentPath", Type.STRING, Cardinality.EXACTLY_ONE,
                "URI path of document in database.")
            },
            new FunctionReturnSequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE, ""));
	
	public RemoveIndex(XQueryContext context) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
        // Get first parameter, this is the document
        final String path = args[0].itemAt(0).getStringValue();

        // Retrieve document from database
        try(final LockedDocument lockedDoc = context.getBroker().getXMLResource(XmldbURI.xmldbUriFor(path), LockMode.READ_LOCK)) {
            // Verify the document actually exists
            if (lockedDoc == null) {
                throw new XPathException((Expression) null, "Document " + path + " does not exist.");
            }

            // Retrieve Lucene
            LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker()
                    .getIndexController().getWorkerByIndexId(LuceneIndex.ID);

            // Note: code order is important here,
            index.setDocument(lockedDoc.getDocument(), ReindexMode.REMOVE_BINARY);

            index.flush();

        } catch (Exception ex) { // PermissionDeniedException
            throw new XPathException((Expression) null, ex);
        }

        // Return nothing [status would be nice]
        return Sequence.EMPTY_SEQUENCE;
	}

}
