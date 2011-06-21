/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 * \$Id\$
 */
package org.exist.xquery.modules.lucene;

import org.apache.log4j.Logger;

import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;

import org.exist.indexing.StreamListener;
import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;

import org.exist.storage.lock.Lock;
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

    private static final Logger logger = Logger.getLogger(Index.class);
    
    public final static FunctionSignature signature =
            new FunctionSignature(
            new QName("index", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "Index an arbitrary chunk of (non-XML) data with lucene. Syntax is inspired by Solar.",
            new SequenceType[]{
                new FunctionParameterSequenceType("documentPath", Type.STRING, Cardinality.ONE,
                "URI path of document in database."),
                new FunctionParameterSequenceType("solrExression", Type.NODE, Cardinality.EXACTLY_ONE,
                "XML syntax expected by Solr' add expression. Element should be called 'doc', e.g."
                + "<doc> <field name=\"field1\">data1</field> "
                + "<field name=\"field2\" boost=\"value\">data2</field> </doc> ")
            },
            new FunctionReturnSequenceType(Type.EMPTY, Cardinality.ZERO, ""));

    /*
     * Constructor
     */
    public Index(XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        DocumentImpl doc = null;
        try {
            // Get first parameter, this is the document
            String path = args[0].itemAt(0).getStringValue();

            // Retrieve document from database
            doc = context.getBroker().getXMLResource(XmldbURI.xmldbUriFor(path), Lock.READ_LOCK);

            // Verify the document actually exists
            if (doc == null) {
                throw new XPathException("Document " + path + " does not exist.");
            }

            // Get 'solr' node from second parameter
            NodeValue descriptor = (NodeValue) args[1].itemAt(0);

            // Retrieve Lucene
            LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker()
                    .getIndexController().getWorkerByIndexId(LuceneIndex.ID);

            // Note: code order is important here,
            index.setDocument(doc, StreamListener.STORE);
            index.setMode(StreamListener.STORE);

            // Pas document and index instructions to indexer
            index.indexNonXML(descriptor);

            // Make sure things are written 
            index.writeNonXML();

        } catch (Exception ex) { // PermissionDeniedException
            logger.error(ex);
            throw new XPathException(ex);

        } finally {
            if (doc != null) {
                doc.getUpdateLock().release(Lock.READ_LOCK);
            }
        }

        // Return nothing [status would be nice]
        return Sequence.EMPTY_SEQUENCE;
    }
}
