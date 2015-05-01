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
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.QName;

import org.exist.indexing.lucene.LuceneIndex;
import org.exist.indexing.lucene.LuceneIndexWorker;

import org.exist.dom.memtree.NodeImpl;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 *  Class implementing the ft:search() method
 * @author Dannes Wessels (dannes@exist-db.org)
 */
public class Search extends BasicFunction {

    private static final Logger logger = LogManager.getLogger(Search.class);
    
    /**
     * Function signatures
     */
    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
                new QName("search", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
                "Search for (non-XML) data with lucene",
                new SequenceType[]{
                        new FunctionParameterSequenceType("path", Type.STRING, Cardinality.ZERO_OR_MORE,
                                "URI paths of documents or collections in database. Collection URIs should end on a '/'."),
                        new FunctionParameterSequenceType("query", Type.STRING, Cardinality.EXACTLY_ONE,
                                "query string"),
                        new FunctionParameterSequenceType("fields", Type.STRING, Cardinality.ZERO_OR_MORE,
                                "Fields to return in search results")
                },
                new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
                        "All documents that are match by the query")),
        new FunctionSignature(
            new QName("search", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
            "Search for (non-XML) data with lucene",
            new SequenceType[]{
                new FunctionParameterSequenceType("path", Type.STRING, Cardinality.ZERO_OR_MORE,
                "URI paths of documents or collections in database. Collection URIs should end on a '/'."),
                new FunctionParameterSequenceType("query", Type.STRING, Cardinality.EXACTLY_ONE,
                "query string")
            },
	        new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
	    		"All documents that are match by the query")),
		new FunctionSignature(
			new QName("search", LuceneModule.NAMESPACE_URI, LuceneModule.PREFIX),
			"Search for (non-XML) data with lucene",
			new SequenceType[]{
					new FunctionParameterSequenceType("query", Type.STRING, Cardinality.EXACTLY_ONE,
							"query string")
			},
			new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
					"All documents that are match by the query"))
    };

    /**
     * Constructor
     */
    public Search(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {

        NodeImpl report = null;
        try {
            // Only match documents that match these URLs 
            List<String> toBeMatchedURIs = new ArrayList<>();

            Sequence pathSeq = getArgumentCount() > 1 ? args[0] : contextSequence;
            if (pathSeq == null)
                return Sequence.EMPTY_SEQUENCE;

            // Get first agument, these are the documents / collections to search in
            for (SequenceIterator i = pathSeq.iterate(); i.hasNext(); ) {
                String path;
                Item item = i.nextItem();
                if (Type.subTypeOf(item.getType(), Type.NODE)) {
                    if (((NodeValue) item).isPersistentSet()) {
                        path = ((NodeProxy) item).getOwnerDocument().getURI().toString();
                    } else {
                        path = item.getStringValue();
                    }
                } else {
                    path = item.getStringValue();
                }
                toBeMatchedURIs.add(path);
            }

            // Get second argument, this is the query
            String query;
            if (getArgumentCount() == 1)
                query = args[0].itemAt(0).getStringValue();
            else
                query = args[1].itemAt(0).getStringValue();

            String[] fields = null;
            if (getArgumentCount() == 3) {
                fields = new String[args[2].getItemCount()];
                int j = 0;
                for (SequenceIterator i = args[2].iterate(); i.hasNext(); ) {
                    fields[j++] = i.nextItem().getStringValue();
                }
            }

            // Get the lucene worker
            LuceneIndexWorker index = (LuceneIndexWorker) context.getBroker()
                    .getIndexController().getWorkerByIndexId(LuceneIndex.ID);

            // Perform search
            report = index.search(context, toBeMatchedURIs, query, fields);
        } catch (IOException e) {
            throw new XPathException(this, e.getMessage(), e);

        } catch (XPathException ex) {
            // Log and rethrow
            logger.error(ex.getMessage(), ex);
            throw ex;
        }

        // Return list of matching files.
        return report;
    }
    
    public int getDependencies() {
    	return Dependency.CONTEXT_SET;
    }
}
