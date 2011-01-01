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
package org.exist.xquery.functions.request;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.apache.commons.io.input.CloseShieldInputStream;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.external.org.apache.commons.io.output.ByteArrayOutputStream;
import org.exist.http.servlets.RequestWrapper;
import org.exist.memtree.DocumentBuilderReceiver;
import org.exist.memtree.MemTreeBuilder;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.exist.util.io.CachingFilterInputStream;
import org.exist.util.io.FilterInputStreamCache;
import org.exist.util.io.MemoryMappedFileFilterInputStreamCache;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Variable;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
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

	protected static final Logger logger = Logger.getLogger(GetData.class);

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName(
				"get-data",
				RequestModule.NAMESPACE_URI,
				RequestModule.PREFIX),
			"Returns the content of a POST request.If its a binary document xs:base64Binary is returned or if its an XML document a node() is returned. All other data is returned as an xs:string representaion. Returns an empty sequence if there is no data.",
			null,
			new FunctionReturnSequenceType(Type.ITEM, Cardinality.ZERO_OR_ONE, "the content of a POST request"));
	
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
			throw new XPathException(this, "No request object found in the current XQuery context.");
		
		if(var.getValue().getItemType() != Type.JAVA_OBJECT)
			throw new XPathException(this, "Variable $request is not bound to an Java object.");
		
		JavaObjectValue value = (JavaObjectValue) var.getValue().itemAt(0);
		
		if(value.getObject() instanceof RequestWrapper)
		{
			RequestWrapper request = (RequestWrapper)value.getObject();	
			
			//if the content length is unknown or 0, return
			if (request.getContentLength() == -1 
			 || request.getContentLength() == 0)
			{
				return Sequence.EMPTY_SEQUENCE;
			}

			//first, get the content of the request
                        InputStream is = null;
			try {
				 is = request.getInputStream();
			} catch(IOException ioe) {
				throw new XPathException(this, "An IO exception occurred: " + ioe.getMessage(), ioe);
			}
			
			//was there any POST content
                        FilterInputStreamCache cache = null;
                        try
                        {
                            if(is != null && is.available()>0) {
                                    //determine if exists mime database considers this binary data
                                    String contentType = request.getContentType();
                                    if(contentType != null) {
                                            //strip off any charset encoding info
                                            if(contentType.indexOf(";") > -1) {
                                                    contentType = contentType.substring(0, contentType.indexOf(";"));
                                            }

                                            MimeType mimeType = MimeTable.getInstance().getContentType(contentType);
                                            if(mimeType != null)
                                            {
                                                    if(!mimeType.isXMLType()) {
                                                        //binary data
                                                        return BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), is);
                                                    }
                                            }
                                    }

                                    //we have to cache the input stream, so we can reread it, as we may use it twice (once for xml attempt and once for string attempt)
                                    cache = new MemoryMappedFileFilterInputStreamCache();
                                    is = new CachingFilterInputStream(cache, is);
                                    is.mark(Integer.MAX_VALUE);


                                    //try and parse as an XML documemnt, otherwise fallback to returning the data as a string
                                    context.pushDocumentContext();
                                    try
                                    {
                                            //try and construct xml document from input stream, we use eXist's in-memory DOM implementation
                                            SAXParserFactory factory = SAXParserFactory.newInstance();
                                            factory.setNamespaceAware(true);
                                            //TODO : we should be able to cope with context.getBaseURI()

                                            //we have to use CloseShieldInputStream otherwise the parser closes the stream and we cant later reread
                                            InputSource src = new InputSource(new CloseShieldInputStream(is));

                                            SAXParser parser = factory.newSAXParser();
                                            XMLReader reader = parser.getXMLReader();
                                            MemTreeBuilder builder = context.getDocumentBuilder();
                                            DocumentBuilderReceiver receiver = new DocumentBuilderReceiver(builder, true);
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
                                        is.reset(); //reset as we may need to reuse for string parsing
                                    }

                                    //not a valid XML document, return a string representation of the document
                                    String encoding = request.getCharacterEncoding();
                                    if(encoding == null)
                                    {
                                            encoding = "UTF-8";
                                    }

                                    try {

                                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                                        byte[] buf = new byte[4096];
                                        int l = -1;
                                        while ((l = is.read(buf)) > -1)
                                        {
                                                bos.write(buf, 0, l);
                                        }
                                        String s = new String(bos.toByteArray(), encoding);
                                        return new StringValue(s);

                                    } catch (IOException e) {
                                        throw new XPathException(this, "An IO exception occurred: " + e.getMessage(), e);
                                    }
                            } else {
                                //no post data
                                return Sequence.EMPTY_SEQUENCE;
                            }
                        } catch(IOException ioe) {
                            LOG.error(ioe.getMessage(), ioe);
                        } finally {

                            if(cache != null) {
                                try {
                                    cache.invalidate();
                                } catch(IOException ioe) {
                                    LOG.error(ioe.getMessage(), ioe);
                                }
                            }

                            if(is != null) {
                                try {
                                    is.close();
                                } catch(IOException ioe) {
                                    LOG.error(ioe.getMessage(), ioe);
                                }
                            }
                        }
		}
		else
		{
			throw new XPathException(this, "Variable $request is not bound to a Request object.");
		}

                return Sequence.EMPTY_SEQUENCE;
	}
}
