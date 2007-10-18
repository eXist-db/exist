/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.xquery.functions.xmldb;

import java.io.StringWriter;
import java.util.Properties;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerException;

import org.exist.dom.QName;
import org.exist.util.serializer.DOMSerializer;
import org.exist.util.serializer.ExtendedDOMSerializer;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * 
 * @author wolf
 *
 */
public class XMLDBXUpdate extends XMLDBAbstractCollectionManipulator
{

	public final static FunctionSignature signature = new FunctionSignature(
			new QName("update", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Process an XUpdate request against a collection. The first "
					+ "argument specifies the collection as a simple collection "
					+ "path or an XMLDB URI. "
					+ "The second argument specifies the XUpdate "
					+ "modifications to be processed. Modifications are passed in a "
					+ "document conforming to the XUpdate specification. "
					+ "The function returns the number of modifications caused by the XUpdate.",
			new SequenceType[]{
					new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
					new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE)},
			new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE));

	public XMLDBXUpdate(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence evalWithCollection(Collection c, Sequence[] args, Sequence contextSequence) throws XPathException
	{
		NodeValue data = (NodeValue) args[1].itemAt(0);
		StringWriter writer = new StringWriter();
		Properties properties = new Properties();
		properties.setProperty(OutputKeys.INDENT, "yes");
        DOMSerializer serializer = new ExtendedDOMSerializer(context.getBroker(), writer, properties);
		try
		{
			serializer.serialize(data.getNode());
		}
		catch(TransformerException e)
		{
			LOG.debug("Exception while serializing XUpdate document", e);
			throw new XPathException(getASTNode(), "Exception while serializing XUpdate document: " + e.getMessage(), e);
		}
		String xupdate = writer.toString();

		long modifications = 0;
		try
		{
			XUpdateQueryService service = (XUpdateQueryService)c.getService("XUpdateQueryService", "1.0");
			LOG.debug("Processing XUpdate request: " + xupdate);
			modifications = service.update(xupdate);
		}
		catch(XMLDBException e)
		{
			throw new XPathException(getASTNode(), "Exception while processing xupdate: " + e.getMessage(), e);
		}
		
		context.getRootExpression().resetState();
		return new IntegerValue(modifications);
	}
}
