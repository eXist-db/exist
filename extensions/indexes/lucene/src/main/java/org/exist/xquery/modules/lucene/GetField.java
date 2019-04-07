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
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class GetField extends BasicFunction {

	public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("get-field", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "Retrieve the stored content of a field.",
            new SequenceType[]{
                new FunctionParameterSequenceType("path", Type.STRING, Cardinality.ZERO_OR_MORE,
                "URI paths of documents or collections in database. Collection URIs should end on a '/'."),
                new FunctionParameterSequenceType("field", Type.STRING, Cardinality.EXACTLY_ONE,
                "query string")
            },
	        new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_MORE,
	    		"All documents that are match by the query"),
			"Use an index definition with nested fields and ft:field instead"
		),
    };
	
	public GetField(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	@Override
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		XmldbURI uri = XmldbURI.createInternal(args[0].getStringValue());
		String field = args[1].getStringValue();

		try(final LockedDocument lockedDoc = context.getBroker().getXMLResource(uri, LockMode.READ_LOCK)) {
			if (lockedDoc == null) {
                return Sequence.EMPTY_SEQUENCE;
            }
			// Get the lucene worker
            final LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker().getIndexController().getWorkerByIndexId(LuceneIndex.ID);
            final String content = index.getFieldContent(lockedDoc.getDocument().getDocId(), field);
            return content == null ? Sequence.EMPTY_SEQUENCE : new org.exist.xquery.value.StringValue(content);
		} catch (PermissionDeniedException e) {
			throw new XPathException(this, LuceneModule.EXXQDYFT0001, "Permission denied to read document " + args[0].getStringValue());
		} catch (IOException e) {
			throw new XPathException(this, LuceneModule.EXXQDYFT0002, "IO error while reading document " + args[0].getStringValue());
		}
	}

}
