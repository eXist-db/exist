/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-08 The eXist Project
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
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Global cache module. Get function
 * 
 * @author Evgeny Gazdovsky <gazdovsky@gmail.com>
 * @version 1.0
 */
public class CacheFunction extends BasicFunction {

    private final static Logger logger = LogManager.getLogger(CacheFunction.class);

    public final static FunctionSignature signatures[] = { 
		new FunctionSignature(
				new QName("cache", CacheModule.NAMESPACE_URI, CacheModule.PREFIX),
				"Get/create a cache using the specified name.",
				new SequenceType[] { 
					new FunctionParameterSequenceType("name", Type.STRING, Cardinality.ONE, "The name of the cache to get/create") 
				}, 
		        new FunctionParameterSequenceType("java-object", Type.JAVA_OBJECT, Cardinality.ONE, "the Java cache object with the given name.")
			) 
	};

	public CacheFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
		String name = args[0].itemAt(0).getStringValue();
		if( logger.isDebugEnabled() ) {
			logger.debug("Get/create cache [" + name + "]");
		}
		
		return new JavaObjectValue(Cache.getInstance(name));
	}
}