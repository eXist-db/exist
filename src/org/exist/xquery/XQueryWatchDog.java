/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 * 
 *  $Id$
 */
package org.exist.xquery;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.memtree.MemTreeBuilder;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;


/**
 * @author wolf
 */
public class XQueryWatchDog {
    /**
     * Log4J Logger for this class
     */
    private static final Logger LOG = Logger.getLogger(XQueryWatchDog.class);

    private XQueryContext context;
    
    private long timeout = Long.MAX_VALUE;
    private long startTime;
    private int maxNodesLimit = Integer.MAX_VALUE;
    
    /**
     * 
     */
    public XQueryWatchDog(XQueryContext context) {
        this.context = context;
        configureDefaults();
        startTime = System.currentTimeMillis();
    }
    
    private void configureDefaults() {
        try {
            Configuration conf = BrokerPool.getInstance().getConfiguration();
            Object option = conf.getProperty("db-connection.watchdog.query-timeout");
            if(option != null)
                timeout = ((Long)option).longValue();
            if(timeout <= 0)
                timeout = Long.MAX_VALUE;
            option = conf.getProperty("db-connection.watchdog.output-size-limit");
            if(option != null)
                maxNodesLimit = ((Integer)option).intValue();
        } catch (EXistException e) {
            LOG.warn("configure() - Watchdog configuration failed");
        }
    }
    
    public void setTimeoutFromPragma(Pragma pragma) throws XPathException {
    	String[] contents = pragma.tokenizeContents();
    	if(contents.length != 1)
    		throw new XPathException("Pragma 'timeout' should have exactly one parameter: the timeout value.");
		try {
			timeout = Long.parseLong(contents[0]);
		} catch (NumberFormatException e) {
			throw new XPathException("Error parsing timeout value in pragma " + pragma.getQName().toString());
		}
		LOG.debug("timeout set from pragma: " + timeout + "ms.");
    }
    
    public void setMaxNodesFromPragma(Pragma pragma) throws XPathException {
    	String[] contents = pragma.tokenizeContents();
    	if(contents.length != 1)
    		throw new XPathException("Pragma 'output-size-limit' should have exactly one parameter: the timeout value.");
		try {
			maxNodesLimit = Integer.parseInt(contents[0]);
		} catch (NumberFormatException e) {
			throw new XPathException("Error parsing size-limit value in pragma " + pragma.getQName().toString());
		}
		LOG.debug("output-size-limit set from pragma: " + maxNodesLimit);
    }
    
    public void proceed(Expression expr) throws TerminatedException {
        final long elapsed = System.currentTimeMillis() - startTime;
        if(elapsed > timeout) {
            if(expr == null)
                expr = context.getRootExpression();
            LOG.warn("Query exceeded predefined timeout (" + elapsed + "ms.): " + expr.pprint());
            throw new TerminatedException.TimeoutException(expr.getASTNode(),
                    "The query exceeded the predefined timeout and has been killed.");
        }
    }
    
    public void proceed(Expression expr, MemTreeBuilder builder) throws TerminatedException {
        proceed(expr);
        if(builder.getSize() > maxNodesLimit) {
            if(expr == null)
                expr = context.getRootExpression();
            LOG.warn("Query exceeded predefined limit for document fragments: " + expr.pprint());
            throw new TerminatedException.SizeLimitException(expr.getASTNode(),
                    "The constructed document fragment exceeded the predefined size limit (current: " +
                    builder.getSize() + "; allowed: " + maxNodesLimit +
                    "). The query has been killed.");
        }
    }
    
    public void reset() {
        startTime = System.currentTimeMillis();
    }
}
