/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.util;

import java.net.URISyntaxException;

import org.apache.log4j.Logger;
import org.exist.dom.DocumentImpl;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 *
 */
public class DocumentNameOrId extends BasicFunction {
	
	protected static final Logger logger = Logger.getLogger(DocumentNameOrId.class);

	//TODO: should this return a uri?
	public final static FunctionSignature docNameSignature =
		new FunctionSignature(
			new QName("document-name", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns the name of a document (excluding the collection path). The argument can either be " +
            "a node or a string path pointing to a resource in the database. If the resource does not exist or the node " +
            "does not belong to a stored document, the empty sequence is returned.",
			new SequenceType[] {
					new FunctionParameterSequenceType("node-or-path", Type.ITEM, Cardinality.EXACTLY_ONE, "The node or a string path pointing to a resource in the database.")
			},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the name of the document"));
	
	public final static FunctionSignature docIdSignature =
		new FunctionSignature(
			new QName("document-id", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns the internal integer id of a document. The argument can either be " +
            "a node or a string path pointing to a resource in the database. If the resource does not exist or the node " +
            "does not belong to a stored document, the empty sequence is returned.",
			new SequenceType[] {
				new FunctionParameterSequenceType("node-or-path", Type.ITEM, Cardinality.EXACTLY_ONE, "The node or a string path pointing to a resource in the database.")
			},
			new FunctionReturnSequenceType(Type.INT, Cardinality.ZERO_OR_ONE, "the ID of the document"));
	
	public DocumentNameOrId(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		
        DocumentImpl doc = null;
        if (Type.subTypeOf(args[0].getItemType(), Type.NODE)) {
            NodeValue node = (NodeValue) args[0].itemAt(0);
            if (node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
                NodeProxy proxy = (NodeProxy) node;
                doc = proxy.getDocument();
            }
        } else {
            String path = args[0].getStringValue();
            try {
                doc = context.getBroker().getXMLResource(XmldbURI.xmldbUriFor(path), Lock.READ_LOCK);
            } catch (URISyntaxException e) {
                throw new XPathException(this, "Invalid resource uri: " + path,e);
            } catch (PermissionDeniedException e) {
                throw new XPathException(this, path + ": permission denied to read resource");
            } finally {
                if (doc != null) doc.getUpdateLock().release(Lock.READ_LOCK);
            }
        }
        if (doc != null) {
            if ("document-name".equals(getSignature().getName().getLocalName())) {
                return new StringValue(doc.getFileURI().toString());
            } else {
                return new IntegerValue(doc.getDocId(), Type.INT);
            }
        }
        return Sequence.EMPTY_SEQUENCE;
    }

}
