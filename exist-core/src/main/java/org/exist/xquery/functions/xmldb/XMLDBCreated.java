/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.xmldb;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;

import org.exist.dom.QName;
import org.exist.xmldb.EXistCollection;
import org.exist.xmldb.EXistResource;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.DateTimeValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 *
 */
public class XMLDBCreated extends XMLDBAbstractCollectionManipulator {
    private static final Logger logger = LogManager.getLogger(XMLDBCreated.class);

	public final static FunctionSignature createdSignatures[] = {
        new FunctionSignature(
			new QName("created", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the creation date of the resource $resource in $collection-uri. " +
            XMLDBModule.COLLECTION_URI,
			new SequenceType[] {
			    new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI"),
			    new FunctionParameterSequenceType("resource", Type.STRING, Cardinality.EXACTLY_ONE, "The resource")
			},
			new FunctionReturnSequenceType(Type.DATE_TIME, Cardinality.EXACTLY_ONE, "the creation date")
        ),
		new FunctionSignature(
			new QName("created", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the creation date of the collection $collection-uri. " + 
            XMLDBModule.COLLECTION_URI,
			new SequenceType[] {
			    new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI")
			},
			new FunctionReturnSequenceType(Type.DATE_TIME, Cardinality.EXACTLY_ONE, "the creation date")
        )
    };
	
	public final static FunctionSignature lastModifiedSignature =
        new FunctionSignature(
			new QName("last-modified", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the last-modification date of resource $resource in " +
			"collection $collection-uri. " +
            XMLDBModule.COLLECTION_URI,
			new SequenceType[] {
			    new FunctionParameterSequenceType("collection-uri", Type.ITEM, Cardinality.EXACTLY_ONE, "The collection URI"),
			    new FunctionParameterSequenceType("resource", Type.STRING, Cardinality.EXACTLY_ONE, "The resource")
			},
			new FunctionReturnSequenceType(Type.DATE_TIME, Cardinality.ZERO_OR_ONE, "the last modification date")
        );
	
	public XMLDBCreated(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
    public Sequence evalWithCollection(Collection collection, Sequence[] args, Sequence contextSequence)
	throws XPathException {

	try {
	    Date date;
	    if(getSignature().getArgumentCount() == 1) {
                date = ((EXistCollection)collection).getCreationTime();
	    } else {
                final Resource resource = collection.getResource(args[1].getStringValue());
                
                if(resource == null) {
                    return Sequence.EMPTY_SEQUENCE;
                }
                
                if(isCalledAs("last-modified"))
		    {date = ((EXistResource)resource).getLastModificationTime();}
                else
		    {date = ((EXistResource)resource).getCreationTime();}
            }

	    return new DateTimeValue(date);

	} catch(final XMLDBException e) {
	    logger.error("Failed to retrieve creation date or modification time of specified resource or creation date of collection");

	    throw new XPathException(this, "Failed to retrieve creation date: " + e.getMessage(), e);
	}
    }

}
