/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2006-2009 The eXist Project
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

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.dom.persistent.LockedDocument;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author <a href="mailto:adam.retter@devon.gov.uk">Adam Retter</a>
 */
public class XMLDBGetMimeType extends BasicFunction {
	protected static final Logger logger = LogManager.getLogger(XMLDBGetMimeType.class);
	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-mime-type", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the MIME type if available of the resource $resource-uri, otherwise the empty sequence. " +
            XMLDBModule.ANY_URI,
			new SequenceType[] {
                new FunctionParameterSequenceType("resource-uri", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The resource URI")
			},
			new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the mime-type if available, otherwise the empty sequence")
		);
	
	public XMLDBGetMimeType(XQueryContext context, FunctionSignature signature)
	{
		super(context, signature);
	}
	
	public Sequence eval(Sequence args[], Sequence contextSequence)
        throws XPathException {

		final String path = new AnyURIValue(args[0].itemAt(0).getStringValue()).toString();
		
		if(path.matches("^[a-z]+://.*")) {
			//external
			final MimeTable mimeTable = MimeTable.getInstance();
			final MimeType mimeType = mimeTable.getContentTypeFor(path);
			if(mimeType != null) {
				return new StringValue(mimeType.getName());
            }
		} else {
			//database
			try {
				XmldbURI pathUri = XmldbURI.xmldbUriFor(path);
				// relative collection Path: add the current base URI
				pathUri = context.getBaseURI().toXmldbURI().resolveCollectionPath(pathUri);
				// try to open the document and acquire a lock
				try(final LockedDocument lockedDoc = context.getBroker().getXMLResource(pathUri, LockMode.READ_LOCK)) {
					if (lockedDoc != null) {
						return new StringValue(lockedDoc.getDocument().getMetadata().getMimeType());
					}
				}
			} catch(final Exception e) {
                logger.error(e.getMessage());
				throw new XPathException(this, e);
			}
		}
		return Sequence.EMPTY_SEQUENCE;
	}
}
