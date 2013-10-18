/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2013 The eXist Project
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
 *  $Id$
 */
package org.exist.xquery.functions.system;

import org.exist.dom.QName;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Clear XQuery Cache
 * 
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 */
public class ClearXQueryCache extends BasicFunction {

    public final static FunctionSignature signature = new FunctionSignature(
        new QName("clear-xquery-cache", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
        "Clear XQuery cache.",
        FunctionSignature.NO_ARGS,
        new SequenceType(Type.EMPTY, Cardinality.ZERO)
    );

    public ClearXQueryCache(XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
    	
    	if (!context.getSubject().hasDbaRole()) {
    		throw new XPathException(this, "Only DBA can call clear-xquery-cache function.");
    	}
    	
    	final DBBroker broker = context.getBroker();
    	
    	final XQuery xquery = broker.getXQueryService();
		final XQueryPool pool = xquery.getXQueryPool();
		
		pool.clear();
		
        return Sequence.EMPTY_SEQUENCE;
    }
}
