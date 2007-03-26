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
 *  $Id: EchoFunction.java 3063 2006-04-05 20:49:44Z brihaye $
 */
package org.exist.xquery.modules.html;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.exist.dom.QName;
import org.exist.memtree.SAXAdapter;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.XMLDBException;

/**
 * @author Adam Retter <adam.retter@devon.gov.uk>
 */
public class DocFunction extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
				new QName("doc", HTMLModule.NAMESPACE_URI),
				"Returns the documents specified in the input sequence. " +  
	            "The arguments standard URLs starting with http://, file://, etc." +
	            "The HTML documents will be `tidied` to make them XML compatible.",
				new SequenceType[] { new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)},
				new SequenceType(Type.NODE, Cardinality.ZERO_OR_ONE));

	public DocFunction(XQueryContext context) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
	{
		//is argument the empty sequence?
		if (args[0].isEmpty())
			return Sequence.EMPTY_SEQUENCE;

		try
		{
			String path = args[0].getStringValue();
			return getHTMLDocument(path);
		}
		catch(Exception e)
		{
			throw new XPathException(getASTNode(), e.getMessage());			
		}
	}

	private Sequence getHTMLDocument(String path) throws PermissionDeniedException, XMLDBException, XPathException
	{
		Sequence document = Sequence.EMPTY_SEQUENCE;
		
		try
		{
			//Basic tests on the URL				
			URL url = new URL(path);
			URLConnection con = url.openConnection();
			if(con instanceof HttpURLConnection)
			{
				HttpURLConnection httpConnection = (HttpURLConnection)con;
				if(httpConnection.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND)
				{
					// Special case: '404'
                    return Sequence.EMPTY_SEQUENCE;
                }
				else if(httpConnection.getResponseCode() != HttpURLConnection.HTTP_OK)
				{
					//TODO : return another type 
                    throw new PermissionDeniedException("Server returned code " + httpConnection.getResponseCode());	
                }
			}
			
			//TODO : process pseudo-protocols URLs more efficiently.
			org.exist.memtree.DocumentImpl memtreeDoc = null;
			// we use eXist's in-memory DOM implementation
			SAXParserFactory factory = SAXParserFactory.newInstance();
			factory.setNamespaceAware(true);				
			//TODO : we should be able to cope with context.getBaseURI()				
			InputSource src = new InputSource(con.getInputStream());
			SAXParser parser = factory.newSAXParser();
			//XMLReader reader = parser.getXMLReader();
			
			//use Neko to parse the HTML content to XML
			XMLReader reader = null;
			try
			{
                LOG.debug("Converting HTML to XML using NekoHTML parser for: " + path);
                Class clazz = Class.forName("org.cyberneko.html.parsers.SAXParser");
                reader = (XMLReader) clazz.newInstance();
            }
			catch(Exception e)
			{
                LOG.error("Error while involing NekoHTML parser. ("+e.getMessage()
                        +"). If you want to parse non-wellformed HTML files, put "
                        +"nekohtml.jar into directory 'lib/optional'.", e);
                throw new XMLDBException(ErrorCodes.VENDOR_ERROR, "NekoHTML parser error", e);
            }
			
			
			SAXAdapter adapter = new SAXAdapter();
			reader.setContentHandler(adapter);
			reader.parse(src);					
			Document doc = adapter.getDocument();
			memtreeDoc = (org.exist.memtree.DocumentImpl)doc;
			memtreeDoc.setContext(context);
			document = memtreeDoc;
		}
		catch(MalformedURLException e)
		{
			throw new XPathException(e.getMessage(), e);					
		}
		catch(ParserConfigurationException e)
		{				
			throw new XPathException(e.getMessage(), e);		
		}
		catch(SAXException e)
		{
			throw new XPathException(e.getMessage(), e);	
		}
		catch(IOException e)
		{
			// Special case: FileNotFoundException
            if(e instanceof FileNotFoundException)
            {
            	return Sequence.EMPTY_SEQUENCE;
            }
            else
            {
            	throw new XPathException(e.getMessage(), e);	
            }
		}
		return document;
	}
}
