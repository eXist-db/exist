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


import net.spy.memcached.MemcachedClient;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class MemcachedStoreFunction extends BasicFunction
{
	protected static final Logger logger = LogManager.getLogger(MemcachedStoreFunction.class);
	
	private static SequenceType[] storeParam = new SequenceType[]{
			new FunctionParameterSequenceType( "client", Type.LONG, Cardinality.EXACTLY_ONE, 
				"The memcached client handle."),
			new FunctionParameterSequenceType( "key", Type.STRING, Cardinality.EXACTLY_ONE, 
				"The key to store data."),
			new FunctionParameterSequenceType( "data", Type.ANY_TYPE, Cardinality.EXACTLY_ONE, 
				"The data to store. The xs:base64Binary will be stored as binary stream and all other " +
				"types will be stored as strings. Fore storin an XML fragment as text, " +
				"use util:serialize() before to serialize one into string."),
			new FunctionParameterSequenceType( "exp", Type.LONG, Cardinality.EXACTLY_ONE, 
				"The expiried period in seconds.")
	};

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
				new QName( "set", MemcachedModule.NAMESPACE_URI, MemcachedModule.PREFIX ),
					"Store data in Memcached server for given key.",
	  				storeParam,
	  				new SequenceType(Type.EMPTY, Cardinality.EMPTY)
			),
		new FunctionSignature(
				new QName( "add", MemcachedModule.NAMESPACE_URI, MemcachedModule.PREFIX ),
					"Store data in Memcached server, but only if the server doesn't " +
					"already hold data for this key",
					storeParam,
	  				new SequenceType(Type.EMPTY, Cardinality.EMPTY)
			),
		new FunctionSignature(
				new QName( "replace", MemcachedModule.NAMESPACE_URI, MemcachedModule.PREFIX ),
					"Store data in Memcached server, but only if the server doese " +
					"already hold data for this key",
					storeParam,
	  				new SequenceType(Type.EMPTY, Cardinality.EMPTY)
			)
	};

	public MemcachedStoreFunction( XQueryContext context, FunctionSignature signature )
	{
		super( context, signature );
    }

	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		long clientHandle = ((IntegerValue) args[0].itemAt(0)).getLong();
		MemcachedClient client = MemcachedModule.retrieveClient(clientHandle);

		String key = args[1].itemAt(0).getStringValue();
		
		Item data = args[2].itemAt(0);
		
		Object o;
		if (data.getType() == Type.BASE64_BINARY){
			o = ((BinaryValue)data).toJavaObject(byte[].class);
		} else {
			o = data.getStringValue();
		}
		
		int exp = ((IntegerValue) args[3].itemAt(0)).getInt(); 

		if (isCalledAs("set")){
			client.set(key, exp, o);
		} else if (isCalledAs("add")){
			client.add(key, exp, o);
		} else if (isCalledAs("replace")){
			client.replace(key, exp, o);
		}
        
        return Sequence.EMPTY_SEQUENCE;
		
	}
}
