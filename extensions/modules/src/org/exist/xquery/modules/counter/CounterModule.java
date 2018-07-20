/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2009 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *
 *  $Id$
 */
package org.exist.xquery.modules.counter;

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;

import java.util.Arrays;
import java.util.List;
import java.util.Map;


/**
 * Module function definitions for counters module.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class CounterModule extends AbstractInternalModule {

	public final static String NAMESPACE_URI = "http://exist-db.org/xquery/counter";

	public final static String PREFIX = "counter";
    public final static String INCLUSION_DATE = "2009-10-27";
    public final static String RELEASED_IN_VERSION = "eXist-1.4";

	public final static FunctionDef[] functions = {
	
        new FunctionDef(CounterFunctions.createCounter, CounterFunctions.class),
        new FunctionDef(CounterFunctions.createCounterAndInit, CounterFunctions.class),
        new FunctionDef(CounterFunctions.nextValue, CounterFunctions.class),
        new FunctionDef(CounterFunctions.destroyCounter, CounterFunctions.class),

    };

    static {
        Arrays.sort(functions, new FunctionComparator());
    }

    public final static QName EXCEPTION_QNAME =
	    new QName("exception", CounterModule.NAMESPACE_URI, CounterModule.PREFIX);

    public final static QName EXCEPTION_MESSAGE_QNAME =
        new QName("exception-message", CounterModule.NAMESPACE_URI, CounterModule.PREFIX);

	public CounterModule(Map<String, List<?>> parameters) throws XPathException {
		super(functions, parameters, true);
	}

	@Override
	public void prepare(final XQueryContext context) throws XPathException {
		declareVariable(EXCEPTION_QNAME, null);
		declareVariable(EXCEPTION_MESSAGE_QNAME, null);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDescription()
	 */
	public String getDescription() {
		return "A module for persistent counters.";
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getNamespaceURI()
	 */
	public String getNamespaceURI() {
		return NAMESPACE_URI;
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.Module#getDefaultPrefix()
	 */
	public String getDefaultPrefix() {
		return PREFIX;
	}

    public String getReleaseVersion() {
        return RELEASED_IN_VERSION;
    }
}

