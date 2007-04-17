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
package org.exist.xquery.modules.html;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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
			
			//xml'ise the HTML
			return ModuleUtils.htmlToXHtml(context, path, new InputSource(con.getInputStream()));
		}
		catch(MalformedURLException e)
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
	}
}
