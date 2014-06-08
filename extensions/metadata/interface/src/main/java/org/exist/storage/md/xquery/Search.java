/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2014 The eXist Project
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
package org.exist.storage.md.xquery;

import java.util.ArrayList;
import java.util.List;
import org.apache.log4j.Logger;

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;

import org.exist.memtree.NodeImpl;
import org.exist.storage.md.MetaData;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import static org.exist.storage.md.MDStorageManager.*;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class Search extends BasicFunction {

    private static final Logger logger = Logger.getLogger(Search.class);
    
    /**
     * Function signatures
     */
    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("search", NAMESPACE_URI, PREFIX),
            "Search for metadata with lucene",
            new SequenceType[]{
                new FunctionParameterSequenceType("path", Type.STRING, Cardinality.ZERO_OR_MORE,
                "URI paths of documents or collections in database. Collection URIs should end on a '/'."),
                new FunctionParameterSequenceType("query", Type.STRING, Cardinality.EXACTLY_ONE,
                "query string")
            },
	        new FunctionReturnSequenceType(Type.NODE, Cardinality.EXACTLY_ONE,
	    		"All documents that are match by the query")),
		new FunctionSignature(
			new QName("search", NAMESPACE_URI, PREFIX),
			"Search for metadata with lucene",
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
            List<String> toBeMatchedURIs = new ArrayList<String>();

            Sequence pathSeq = getArgumentCount() == 2 ? args[0] : contextSequence;
            if (pathSeq == null)
            	return Sequence.EMPTY_SEQUENCE;
            
            // Get first agument, these are the documents / collections to search in
            for (SequenceIterator i = pathSeq.iterate(); i.hasNext();) {
            	String path;
                Item item = i.nextItem();
                if (Type.subTypeOf(item.getType(), Type.NODE)) {
                	if (((NodeValue)item).isPersistentSet()) {
                		path = ((NodeProxy)item).getDocument().getURI().toString();
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
            
            //XXX: report = MetaData.get().search(query, toBeMatchedURIs);

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
