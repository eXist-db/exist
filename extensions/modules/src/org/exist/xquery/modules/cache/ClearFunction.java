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
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Global cache module. Clear function
 * 
 * @author Evgeny Gazdovsky <gazdovsky@gmail.com>
 * @version 1.0
 */
public class ClearFunction extends BasicFunction {

    private final static Logger logger = LogManager.getLogger(ClearFunction.class);

    public final static FunctionSignature signatures[] = { 
		new FunctionSignature(
			new QName("clear", CacheModule.NAMESPACE_URI, CacheModule.PREFIX),
			"Clear the entire cache, globally",
			null, 
	        new SequenceType(Type.EMPTY, Cardinality.EMPTY)
		), 
		new FunctionSignature(
			new QName("clear", CacheModule.NAMESPACE_URI, CacheModule.PREFIX),
			"Clear the identified cache",
			new SequenceType[] { 
				new FunctionParameterSequenceType("cache-value", Type.ITEM, Cardinality.ONE, "Either the Java cache object or the name of the cache") 
			}, 
	        new SequenceType(Type.EMPTY, Cardinality.EMPTY)
		) 
	};

	public ClearFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		if (args.length==0){
			logger.info("Clearing all caches");
			Cache.clearGlobal();
		} else {
			Item item = args[0].itemAt(0);
			if (item.getType()==Type.STRING){
				if( logger.isDebugEnabled() ) {
					logger.debug("Clearing cache [" + item.getStringValue() + "]");
				}
				Cache.clear(item.getStringValue());
			} else {
				if( logger.isDebugEnabled() ) {
					logger.debug("Clearing cache [" + item.toJavaObject(Cache.class).toString() + "]");
				}
				((Cache)item.toJavaObject(Cache.class)).clear();
			}
		}
		if( logger.isDebugEnabled() ) {
			logger.debug("Cache cleared");
		}
		
		return Sequence.EMPTY_SEQUENCE;
	}
}