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

import java.util.Properties;

import javax.xml.transform.OutputKeys;

import org.exist.storage.serializers.Serializer;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.xml.sax.SAXException;

/**
 * Global cache module. Get function
 * 
 * @author Evgeny Gazdovsky <gazdovsky@gmail.com>
 * @version 1.0
 */
public abstract class CacheBasicFunction extends BasicFunction {

    public CacheBasicFunction(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	private final static Properties OUTPUT_PROPERTIES = new Properties();
    static {
        OUTPUT_PROPERTIES.setProperty(OutputKeys.INDENT, "no");
        OUTPUT_PROPERTIES.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
    }
    
	protected String serialize(Sequence q) throws SAXException, XPathException {
		String tmp = "";
		Serializer serializer = context.getBroker().getSerializer();
        serializer.reset();
        serializer.setProperties(OUTPUT_PROPERTIES);
        for (SequenceIterator i = q.iterate(); i.hasNext();){
        	Item item = i.nextItem();
        	try {
            	NodeValue node = (NodeValue)item;
            	tmp += serializer.serialize(node);
        	} catch (ClassCastException e){
        		tmp += item.getStringValue();
        	}
        }
        return tmp;
	}
	
}