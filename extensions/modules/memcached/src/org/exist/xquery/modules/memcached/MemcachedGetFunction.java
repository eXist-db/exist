/*
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
 *  along with this program; if not, write to the Free Software Foundation
 *  Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 */

package org.exist.xquery.modules.memcached;


import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import net.spy.memcached.MemcachedClient;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.exist.Namespaces;
import org.exist.dom.QName;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

public class MemcachedGetFunction extends BasicFunction
{
	protected static final Logger logger = LogManager.getLogger(MemcachedGetFunction.class);

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "get", MemcachedModule.NAMESPACE_URI, MemcachedModule.PREFIX ),
				"Get data from Memcached server by key.",
				new SequenceType[]
  				{
				new FunctionParameterSequenceType( "client", Type.LONG, Cardinality.EXACTLY_ONE, 
				"The memcached client handle."),
				new FunctionParameterSequenceType( "key", Type.STRING, Cardinality.EXACTLY_ONE, 
				"The key to get.")
  				},
				new FunctionReturnSequenceType(Type.ANY_TYPE, Cardinality.ZERO_OR_ONE, 
					"Returns data by key")
			)
		};

	public MemcachedGetFunction( XQueryContext context, FunctionSignature signature )
	{
		super( context, signature );
    }

	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		long clientHandle = ((IntegerValue) args[0].itemAt(0)).getLong();
		MemcachedClient client = MemcachedModule.retrieveClient(clientHandle);

		String key = args[1].itemAt(0).getStringValue();
		
		Object o = client.get(key);
		
        if (o == null) {
            return Sequence.EMPTY_SEQUENCE;
        }
        
		String data = o.toString();
		
        StringReader reader = new StringReader(data);
        
        try {
        	
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            InputSource src = new InputSource(reader);

            SAXParser parser = factory.newSAXParser();
            XMLReader xr = parser.getXMLReader();

            SAXAdapter adapter = new SAXAdapter(context);
            xr.setContentHandler(adapter);
            xr.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
            xr.parse(src);

            return (DocumentImpl) adapter.getDocument();
            
        } catch (ParserConfigurationException e) {
            throw new XPathException(this, "Error while constructing XML parser: " + e.getMessage(), e);
        } catch (SAXException e) {
            return new StringValue(data);
        } catch (IOException e) {
            return new StringValue(data);
        }
		
	}
}
