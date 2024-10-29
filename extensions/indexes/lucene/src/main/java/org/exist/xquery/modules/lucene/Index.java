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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;

import org.exist.dom.persistent.LockedDocument;
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
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class Index extends BasicFunction {

    private static final Logger logger = LogManager.getLogger(Index.class);
    
    public final static FunctionSignature signatures[] = {
    	new FunctionSignature(
	            new QName("index", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
	            "Index an arbitrary chunk of (non-XML) data with Lucene. Syntax is inspired by Solr.",
	            new SequenceType[] {
	                new FunctionParameterSequenceType("documentPath", Type.STRING, Cardinality.EXACTLY_ONE,
	                "URI path of document in database."),
	                new FunctionParameterSequenceType("solrExression", Type.NODE, Cardinality.EXACTLY_ONE,
	                "XML syntax expected by Solr's add expression. Element should be called 'doc', e.g."
	                + "<doc> <field name=\"field1\">data1</field> "
	                + "<field name=\"field2\" boost=\"value\">data2</field> </doc> ")
	            },
	            new FunctionReturnSequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE, "")
	        ),
            new FunctionSignature(
	            new QName("index", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
	            "Index an arbitrary chunk of (non-XML) data with Lucene. Syntax is inspired by Solr.",
	            new SequenceType[]{
	                new FunctionParameterSequenceType("documentPath", Type.STRING, Cardinality.EXACTLY_ONE,
	                "URI path of document in database."),
	                new FunctionParameterSequenceType("solrExression", Type.NODE, Cardinality.EXACTLY_ONE,
	                "XML syntax expected by Solr's add expression. Element should be called 'doc', e.g."
	                + "<doc> <field name=\"field1\">data1</field> "
	                + "<field name=\"field2\" boost=\"value\">data2</field> </doc> "),
	                new FunctionParameterSequenceType("close", Type.BOOLEAN, Cardinality.EXACTLY_ONE,
	                "If true, close the Lucene document. Subsequent calls to ft:index will thus add to a " +
	                "new Lucene document. If false, the document remains open and is not flushed to disk. " +
	                "Call the ft:close function to explicitely close and flush the current document.")
	            },
	            new FunctionReturnSequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE, "")
	        ),
    
            new FunctionSignature(
	            new QName("close", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
	            "Close the current Lucene document and flush it to disk. Subsequent calls to " +
	            "ft:index will write to a new Lucene document.",
	            null,
	            new FunctionReturnSequenceType(Type.EMPTY_SEQUENCE, Cardinality.EMPTY_SEQUENCE, ""))
    };

    /*
     * Constructor
     */
    public Index(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {


        try {
        	// Retrieve Lucene
            LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker()
                    .getIndexController().getWorkerByIndexId(LuceneIndex.ID);
            
        	if (isCalledAs("index")) {
	            // Get first parameter, this is the document
	            String path = args[0].itemAt(0).getStringValue();
	
	            // Retrieve document from database
	            try(final LockedDocument lockedDoc = context.getBroker().getXMLResource(XmldbURI.xmldbUriFor(path), LockMode.READ_LOCK)) {
					// Verify the document actually exists
					final DocumentImpl doc = lockedDoc == null ? null : lockedDoc.getDocument();
					if (doc == null) {
						throw new XPathException(this, "Document " + path + " does not exist.");
					}

					boolean flush = args.length == 2 || args[2].effectiveBooleanValue();

					// Note: code order is important here,
					index.setDocument(doc, ReindexMode.STORE);
					index.setMode(ReindexMode.STORE);

					// Get 'solr' node from second parameter
					NodeValue descriptor = (NodeValue) args[1].itemAt(0);

					// Pas document and index instructions to indexer
					index.indexNonXML(descriptor);

					if (flush) {
						// Make sure things are written
						index.writeNonXML();
					}
				}
        	} else {
        		// "close"
        		index.writeNonXML();
        	}

        } catch (Exception ex) { // PermissionDeniedException
            logger.error(ex.getMessage(), ex);
            throw new XPathException(this, ex);
        }

        // Return nothing [status would be nice]
        return Sequence.EMPTY_SEQUENCE;
    }
}
