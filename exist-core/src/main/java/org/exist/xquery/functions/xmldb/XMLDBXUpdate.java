/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2004-2009 The eXist Project
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

import org.exist.dom.QName;
import org.exist.util.serializer.DOMSerializer;
import org.exist.util.serializer.ExtendedDOMSerializer;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XUpdateQueryService;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;
import java.io.StringWriter;
import java.util.Properties;

/**
 * 
 * @author wolf
 *
 */
public class XMLDBXUpdate extends XMLDBAbstractCollectionManipulator
{
	protected static final Logger logger = LogManager.getLogger(XMLDBXUpdate.class);

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("update", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Processes an XUpdate request, $modifications, against a collection $collection-uri. "
            + XMLDBModule.COLLECTION_URI 
            + "The modifications are passed in a "
            + "document conforming to the XUpdate specification. "
            + "http://rx4rdf.liminalzone.org/xupdate-wd.html#N1a32e0"
            + "The function returns the number of modifications caused by the XUpdate.",
			new SequenceType[]{
					new FunctionParameterSequenceType("collection-uri", Type.STRING, Cardinality.EXACTLY_ONE, "The collection URI"),
					new FunctionParameterSequenceType("modifications", Type.NODE, Cardinality.EXACTLY_ONE, "The XUpdate modifications to be processed")},
			new FunctionReturnSequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE, "the number of modifications, as xs:integer, caused by the XUpdate"));

	public XMLDBXUpdate(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence evalWithCollection(Collection c, Sequence[] args, Sequence contextSequence)
        throws XPathException {
		final NodeValue data = (NodeValue) args[1].itemAt(0);
		final StringWriter writer = new StringWriter();
		final Properties properties = new Properties();
		properties.setProperty(OutputKeys.INDENT, "yes");
        final DOMSerializer serializer = new ExtendedDOMSerializer(context.getBroker(), writer, properties);
		try {
			serializer.serialize(data.getNode());
		} catch(final TransformerException e) {
			logger.debug("Exception while serializing XUpdate document", e);
			throw new XPathException(this, "Exception while serializing XUpdate document: " + e.getMessage(), e);
		}
		final String xupdate = writer.toString();

		long modifications = 0;
		try {
			final XUpdateQueryService service = (XUpdateQueryService)c.getService("XUpdateQueryService", "1.0");
			logger.debug("Processing XUpdate request: " + xupdate);
			modifications = service.update(xupdate);
		} catch(final XMLDBException e) {
			throw new XPathException(this, "Exception while processing xupdate: " + e.getMessage(), e);
		}
		
		context.getRootExpression().resetState(false);
		return new IntegerValue(modifications);
	}
}
