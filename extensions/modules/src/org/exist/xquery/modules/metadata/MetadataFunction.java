/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-03 Wolfgang M. Meier
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
package org.exist.xquery.modules.metadata;

import org.exist.collections.Collection;
import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeSet;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.util.Iterator;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class MetadataFunction extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("metadata", MetadataModule.NAMESPACE_URI, MetadataModule.PREFIX),
			"Retrieves metadata for the dynamic context." +
			"If the context item is undefined an error is raised.",
			null,
			new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE));

	public MetadataFunction(XQueryContext context) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		//must be a context to act on
		if(contextSequence == null)
		{
			throw new XPathException(getASTNode(), "FONC0001: undefined context item");
		}
		
		//iterate through the source documents
		DocumentSet sourceDocuments = contextSequence.getDocumentSet();
		Iterator itSourceDocuments = sourceDocuments.getDocumentIterator();
		NodeSet metadataDocuments = new ExtArrayNodeSet(sourceDocuments.getDocumentCount(), 1);
		Collection metadataCollection = null;
		XmldbURI lastMetadataCollectionURI = null;
		
		while(itSourceDocuments.hasNext())
		{
			//get the source document
			DocumentImpl sourceDoc = (DocumentImpl)itSourceDocuments.next();
			
			//get the uri for the corresponding metadata document 
			XmldbURI metadataDocURI = XmldbURI.METADATA_COLLECTION_URI.append(sourceDoc.getURI());
			
			//get the uri for the corresponding metadata collection
			String tmpMetadataCollectionURI = metadataDocURI.getCollectionPath();
			tmpMetadataCollectionURI = tmpMetadataCollectionURI.substring(0, tmpMetadataCollectionURI.lastIndexOf('/')); 
			XmldbURI metadataCollectionURI = XmldbURI.create(tmpMetadataCollectionURI);
			
			//get the metadata document corresponding to the source document
			DocumentImpl metadataDoc = null;
			
			//TODO: not sure that this collection fetch avoidance code is working correctly?
			//only refetch the collection if different uri
			if(!metadataCollectionURI.equals(lastMetadataCollectionURI))
			{
				metadataCollection = context.getBroker().getCollection(metadataCollectionURI);
				
				//remeber the metadata collection uri
				lastMetadataCollectionURI = metadataCollectionURI;
			}
				
			//is there a corresponding metadata collection?
			if(metadataCollection != null)
			{
				//is there a corresponding metadata document?
				if(metadataCollection.hasDocument(metadataDocURI.lastSegment()))
				{
					//get the metadata document
					metadataDoc = metadataCollection.getDocument(context.getBroker(), metadataDocURI.lastSegment());
				}
			}
			
			//if we find a metadata document, add it to the result set
			if(metadataDoc != null)
			{
				metadataDocuments.add(new NodeProxy(metadataDoc));
			}
		}
		
		return metadataDocuments;
	}
}
