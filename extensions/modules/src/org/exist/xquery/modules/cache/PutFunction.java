/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * $Id$
 */
package org.exist.xquery.modules.cache;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.xml.sax.SAXException;

/**
 * Global cache. Put function
 * 
 * @author Evgeny Gazdovsky <gazdovsky@gmail.com>
 * @version 1.0
 */
public class PutFunction extends CacheBasicFunction {

    private final static Logger logger = LogManager.getLogger(PutFunction.class);
    
	public final static FunctionSignature signatures[] = { 
		new FunctionSignature(
				new QName("put", CacheModule.NAMESPACE_URI, CacheModule.PREFIX),
				"Put data with a key into the identified cache. Returns the previous value associated with the key",
				new SequenceType[] { 
					new FunctionParameterSequenceType("cache-value", Type.ITEM, Cardinality.ONE, "Either the Java cache object or the name of the cache"), 
					new FunctionParameterSequenceType("key", Type.ANY_TYPE, Cardinality.ONE_OR_MORE, "The key to the object within the cache"), 
					new FunctionParameterSequenceType("value", Type.ANY_TYPE, Cardinality.ZERO_OR_MORE, "The object to store within the cache") 
				}, 
		        new FunctionParameterSequenceType("previous-value", Type.ANY_TYPE, Cardinality.ZERO_OR_MORE, "the previous value associated with the key")
			) 
	};

	public PutFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		Item item = args[0].itemAt(0);
		Sequence value = args[2];
		try {
			String key = serialize(args[1]);
			if (item.getType()==Type.STRING){
				if( logger.isTraceEnabled() ) {
					logger.trace("putting cache value [" + item.getStringValue() + ", " + key +"]");
				}
				return Cache.put(item.getStringValue(), key, value);
			} else {
				if( logger.isTraceEnabled() ) {
					logger.trace("putting cache value [" + item.toJavaObject(Cache.class).toString() + ", " + key +"]");
				}
				return ((Cache)item.toJavaObject(Cache.class)).put(key, value);
			}
		} catch (SAXException e) {
			logger.error("Error putting cache value", e);
		}
		return Sequence.EMPTY_SEQUENCE;
	}
}