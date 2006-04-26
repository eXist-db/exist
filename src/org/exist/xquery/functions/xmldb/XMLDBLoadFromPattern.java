/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
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
package org.exist.xquery.functions.xmldb;

import java.io.File;

import org.exist.dom.QName;
import org.exist.util.DirectoryScanner;
import org.exist.xmldb.EXistResource;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * @author wolf
 */
public class XMLDBLoadFromPattern extends XMLDBAbstractCollectionManipulator {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("store-files-from-pattern", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Store new resources into the database. Resources are read from the server's " +
			"file system, using the file pattern specified in the second argument. File pattern matching " +
			"is based on code from Apache's Ant, thus following the same conventions. For example: " +
			"*.xml matches any file ending with .xml in the current directory, **/*.xml matches files " +
			"in any directory below the current one. " +
			"The first argument denotes the collection where resources should be stored. " +
			"The collection can be either specified as a simple collection path, " +
			"an XMLDB URI, or a collection object as returned by the collection or " +
			"create-collection functions. The function returns a sequence of all document paths added " +
			"to the db. These can be directly passed to fn:doc() to retrieve the document.",
			new SequenceType[] {
				new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE)
			},
		new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE)),
		new FunctionSignature(
				new QName("store-files-from-pattern", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
				"Store new resources into the database. Resources are read from the server's " +
				"file system, using the file pattern specified in the second argument. File pattern matching " +
				"is based on code from Apache's Ant, thus following the same conventions. For example: " +
				"*.xml matches any file ending with .xml in the current directory, **/*.xml matches files " +
				"in any directory below the current one. " +
				"The first argument denotes the collection where resources should be stored. " +
				"The collection can be either specified as a simple collection path, " +
				"an XMLDB URI, or a collection object as returned by the collection or " +
				"create-collection functions. The function returns a sequence of all document paths added " +
				"to the db. These can be directly passed to fn:doc() to retrieve the document. The final argument $d is used to specify a mime-type.  If the mime-type " +
				"is something other than 'text/xml' or 'application/xml', the resource will be stored as " +
				"a binary resource.",
				new SequenceType[] {
					new SequenceType(Type.ITEM, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE),
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
				},
				new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE))
	};
	
	public XMLDBLoadFromPattern(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.functions.xmldb.XMLDBAbstractCollectionManipulator#evalWithCollection(org.xmldb.api.base.Collection, org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	protected Sequence evalWithCollection(Collection collection, Sequence[] args,
			Sequence contextSequence) throws XPathException {
		File baseDir = new File(args[1].getStringValue());
		LOG.debug("Loading files from directory: " + baseDir);
		Sequence patterns = args[2];
		String resourceType = "XMLResource";
        String mimeType = "text/xml";
		if(getSignature().getArgumentCount() == 4) {
			mimeType = args[3].getStringValue();
			if(!("text/xml".equals(mimeType) || "application/xml".equals(mimeType)))
				resourceType = "BinaryResource";
		}
		 
		ValueSequence stored = new ValueSequence();
		for(SequenceIterator i = patterns.iterate(); i.hasNext(); ) {
			String pattern = i.nextItem().getStringValue();
			File[] files = DirectoryScanner.scanDir(baseDir, pattern);
			for(int j = 0; j < files.length; j++) {
				try {
					//TODO: these probably need to be encoded
					Resource resource =
						collection.createResource(files[j].getName(), resourceType);
					resource.setContent(files[j]);
                    if("BinaryResource".equals(resourceType))
                        ((EXistResource)resource).setMimeType(mimeType);
					collection.storeResource(resource);
                    //TODO : use dedicated function in XmldbURI
					stored.add(new StringValue(collection.getName() + "/" + resource.getId()));
				} catch (XMLDBException e) {
					LOG.warn("Could not store file " + files[j].getAbsolutePath() + 
							": " + e.getMessage(), e);
				}
			}
		}
		return stored;
	}
}
