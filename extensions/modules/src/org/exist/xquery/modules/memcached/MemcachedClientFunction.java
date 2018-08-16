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
 *  $Id: MailSessionFunctions.java 9745 2009-08-09 21:37:29Z ixitar $
 */

package org.exist.xquery.modules.memcached;


import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import net.spy.memcached.BinaryConnectionFactory;
import net.spy.memcached.MemcachedClient;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.exist.dom.QName;
import org.exist.util.ParametersExtractor;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class MemcachedClientFunction extends BasicFunction
{
	protected static final Logger logger = LogManager.getLogger(MemcachedClientFunction.class);

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "create-client", MemcachedModule.NAMESPACE_URI, MemcachedModule.PREFIX ),
			"Create a Memcached client.",
			new SequenceType[]
			{
				new FunctionParameterSequenceType( "properties", Type.ELEMENT, Cardinality.ONE_OR_MORE, 
						"A client properties in the form <properties><property name=\"\" value=\"\"/></properties>. " +
						"Properties names are \"host\" and optional \"port\" (default port is 11211)."),
				new FunctionParameterSequenceType( "type", Type.BOOLEAN, Cardinality.ZERO_OR_ONE, 
						"A type of memcached protocol, if true then binary version of protocol " +
						"will be used. Default value is false.")
			},
			new FunctionReturnSequenceType( Type.LONG, Cardinality.EXACTLY_ONE, "an xs:long representing the client handle." )
			)
		};

	public MemcachedClientFunction( XQueryContext context, FunctionSignature signature )
	{
		super( context, signature );
    }

	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		List <InetSocketAddress> ialist = new ArrayList <InetSocketAddress>(); 
		SequenceIterator i = args[0].iterate();
		do {
			Properties props = ParametersExtractor.parseProperties( ((NodeValue) i.nextItem()).getNode() );
			String host = props.getProperty("host");
			String tmp	= props.getProperty("port");
			int    port	= tmp == null ? 11211 : new Integer(tmp).intValue();   
			InetSocketAddress ia = new InetSocketAddress(host, port);
			ialist.add(ia);
		} while (i.hasNext());
		
		final boolean isbinary = args[1].isEmpty() ? false : Boolean.valueOf(args[1].itemAt(0).getStringValue());
		
		MemcachedClient client;
		try {
			client = isbinary ? new MemcachedClient(new BinaryConnectionFactory(), ialist) : new MemcachedClient(ialist);
		} catch (IOException e) {
			throw new XPathException("Can't connect to memcahed server(s)");
		}
		
		// store the client and return the handle of the one.
		IntegerValue integerValue = new IntegerValue( MemcachedModule.storeClient(client));
		return integerValue;
	}
}
