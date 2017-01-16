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

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.indexing.StreamListener.ReindexMode;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
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
                new FunctionParameterSequenceType("documentPath", Type.STRING, Cardinality.ONE,
                "URI path of document in database.")
            },
            new FunctionReturnSequenceType(Type.EMPTY, Cardinality.ZERO, ""));
	
	public RemoveIndex(XQueryContext context) {
		super(context, signature);
	}

	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		DocumentImpl doc = null;
        try {
            // Get first parameter, this is the document
            String path = args[0].itemAt(0).getStringValue();

            // Retrieve document from database
            doc = context.getBroker().getXMLResource(XmldbURI.xmldbUriFor(path), LockMode.READ_LOCK);

            // Verify the document actually exists
            if (doc == null) {
                throw new XPathException("Document " + path + " does not exist.");
            }

            // Retrieve Lucene
            LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker()
                    .getIndexController().getWorkerByIndexId(LuceneIndex.ID);

            // Note: code order is important here,
            index.setDocument(doc, ReindexMode.REMOVE_BINARY);

            index.flush();

        } catch (Exception ex) { // PermissionDeniedException
            throw new XPathException(ex);

        } finally {
            if (doc != null) {
                doc.getUpdateLock().release(LockMode.READ_LOCK);
            }
        }

        // Return nothing [status would be nice]
        return Sequence.EMPTY_SEQUENCE;
	}

}
