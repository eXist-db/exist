/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
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
 *  $Id: GetRequestData.java 2436 2006-01-07 21:47:15 +0000 (Sat, 07 Jan 2006) brihaye $
 */
package org.exist.xquery.functions.request;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;


/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class GetData extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName(
				"get-data",
				RequestModule.NAMESPACE_URI,
				RequestModule.PREFIX),
			"Returns the content of a POST request.If its a binary document xs:base64Binary is returned or if its an XML document a node() is returned. All other data is returned as an xs:string representaion. Returns an empty sequence if there is no data.",
			null,
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE));
	
	public final static FunctionSignature deprecated =
		new FunctionSignature(
			new QName(
				"get-request-data",
				RequestModule.NAMESPACE_URI,
				RequestModule.PREFIX),
			"Returns the content of a POST request. If its a binary document xs:base64Binary is returned or if its an XML document a node() is returned. All other data is returned as an xs:string representaion. Returns an empty sequence if there is no data.",
			null,
			new SequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE),
			"Renamed to get-data.");
	
	public GetData(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)throws XPathException
	{
		RequestModule myModule = (RequestModule) context.getModule(RequestModule.NAMESPACE_URI);

		// request object is read from global variable $request
		Variable var = myModule.resolveVariable(RequestModule.REQUEST_VAR);
		
		if(var == null || var.getValue() == null)
			throw new XPathException("No request object found in the current XQuery context.");
		
		if(var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException("Variable $request is not bound to an Java object.");
		
		JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		
		
		if(value.getObject() instanceof RequestWrapper)
		{
			RequestWrapper request = (RequestWrapper)value.getObject();	
			
			//if the content length is unknown, return
			if(request.getContentLength() == -1)
			{
				return Sequence.EMPTY_SEQUENCE;
			}
			
			//first, get the content of the request
			byte[] bufRequestData = null;
			try
			{
				InputStream is = request.getInputStream();
				ByteArrayOutputStream bos = new ByteArrayOutputStream(request.getContentLength());
				byte[] buf = new byte[256];
				int l = 0;
				while ((l = is.read(buf)) > -1)
				{
					bos.write(buf, 0, l);
				}
				bufRequestData = bos.toByteArray();
			}
			catch(IOException ioe)
			{
				throw new XPathException("An IO exception ocurred: " + ioe.getMessage(), ioe);
			}
			
			//was there any POST content
			if(bufRequestData != null)
			{
				//determine if exists mime database considers this binary data
				String contentType = request.getContentType();
				if(contentType != null)
				{
					//strip off any charset encoding info
					if(contentType.indexOf(";") > -1)
						contentType = contentType.substring(0, contentType.indexOf(";"));
					
					MimeType mimeType = MimeTable.getInstance().getContentType(contentType);
					if(mimeType != null)
					{
						if(!mimeType.isXMLType())
						{
							//binary data
							return new Base64Binary(bufRequestData);
						}
					}
				}
				
				//try and parse as an XML documemnt, otherwise fallback to returning the data as a string
				context.pushDocumentContext();
				try
				{ 
					//try and construct xml document from input stream, we use eXist's in-memory DOM implementation
					SAXParserFactory factory = SAXParserFactory.newInstance();
					factory.setNamespaceAware(true);	
					//TODO : we should be able to cope with context.getBaseURI()				
					InputSource src = new InputSource(new ByteArrayInputStream(bufRequestData));
					SAXParser parser = factory.newSAXParser();
					XMLReader reader = parser.getXMLReader();
                    MemTreeBuilder builder = context.getDocumentBuilder();
                    DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder);
					reader.setContentHandler(receiver);
					reader.parse(src);
					Document doc = receiver.getDocument();
					return (NodeValue)doc.getDocumentElement();
				}
				catch(ParserConfigurationException e)
				{				
					//do nothing, we will default to trying to return a string below
				}
				catch(SAXException e)
				{
					//do nothing, we will default to trying to return a string below
				}
				catch(IOException e)
				{
					//do nothing, we will default to trying to return a string below
				}
				finally
				{
                    context.popDocumentContext();
                }
				
				//not a valid XML document, return a string representation of the document
				String encoding = request.getCharacterEncoding();
				if(encoding == null)
				{
					encoding = "UTF-8";
				}
				try
				{
					String s = new String(bufRequestData, encoding);
					return new StringValue(s);
				}
				catch (IOException e)
				{
					throw new XPathException("An IO exception ocurred: " + e.getMessage(), e);
				}
			}
			else
			{
				//no post data
				return Sequence.EMPTY_SEQUENCE;
			}
		}
		else
		{
			throw new XPathException("Variable $request is not bound to a Request object.");
		}
	}
}
