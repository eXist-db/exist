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
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class MemcachedDeleteFunction extends BasicFunction
{
	protected static final Logger logger = LogManager.getLogger(MemcachedDeleteFunction.class);

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName( "delete", MemcachedModule.NAMESPACE_URI, MemcachedModule.PREFIX ),
				"Delete data from Memcached server by key.",
				new SequenceType[]
  				{
				new FunctionParameterSequenceType( "client", Type.LONG, Cardinality.EXACTLY_ONE, 
				"The memcached client handle."),
				new FunctionParameterSequenceType( "key", Type.STRING, Cardinality.EXACTLY_ONE, 
				"The key to delete.")
  				},
  				new SequenceType(Type.EMPTY, Cardinality.EMPTY)
			)
		};

	public MemcachedDeleteFunction( XQueryContext context, FunctionSignature signature )
	{
		super( context, signature );
    }

	public Sequence eval( Sequence[] args, Sequence contextSequence ) throws XPathException
	{
		long clientHandle = ((IntegerValue) args[0].itemAt(0)).getLong();
		MemcachedClient client = MemcachedModule.retrieveClient(clientHandle);

		String key = args[1].itemAt(0).getStringValue();
		
		client.delete(key);
		
		return Sequence.EMPTY_SEQUENCE;
	}
}
