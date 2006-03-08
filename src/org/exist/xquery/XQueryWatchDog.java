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

import java.util.LinkedList;
import java.util.List;

import org.apache.log4j.Logger;
import org.exist.memtree.MemTreeBuilder;
import org.exist.util.Configuration;
import org.exist.xquery.util.ExpressionDumper;


/**
 * @author wolf
 */
public class XQueryWatchDog {
    /**
     * Log4J Logger for this class
     */
    private static final Logger LOG = Logger.getLogger(XQueryWatchDog.class);

    private final XQueryContext context;
    
    private long timeout = Long.MAX_VALUE;
    private int maxNodesLimit = Integer.MAX_VALUE;
    
    private long startTime;
    
    private boolean terminate = false;
    
    private List tempFragments = null;
    
    /**
     * 
     */
    public XQueryWatchDog(XQueryContext context) {
        this.context = context;
        configureDefaults();
        reset();
    }
    
    private void configureDefaults() {
        Configuration conf = context.broker.getBrokerPool().getConfiguration();
        Object option = conf.getProperty("db-connection.watchdog.query-timeout");
        if(option != null)
            timeout = ((Long)option).longValue();
        if(timeout <= 0)
            timeout = Long.MAX_VALUE;
        option = conf.getProperty("db-connection.watchdog.output-size-limit");
        if(option != null)
            maxNodesLimit = ((Integer)option).intValue();
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
    	if(terminate) {
    		if(expr == null)
    			expr = context.getRootExpression();
    		cleanUp();
    		throw new TerminatedException(expr.getASTNode(),
    				"The query has been killed by the server.");
    	}
        final long elapsed = System.currentTimeMillis() - startTime;
        if(elapsed > timeout) {
            if(expr == null)
                expr = context.getRootExpression();
            LOG.warn("Query exceeded predefined timeout (" + elapsed + "ms.): " + 
                    ExpressionDumper.dump(expr));
            cleanUp();
            throw new TerminatedException.TimeoutException(expr.getASTNode(),
                    "The query exceeded the predefined timeout and has been killed.");
        }
    }
    
    public void proceed(Expression expr, MemTreeBuilder builder) throws TerminatedException {
        proceed(expr);
        if(maxNodesLimit > 0 && builder.getSize() > maxNodesLimit) {
            if(expr == null)
                expr = context.getRootExpression();
            LOG.warn("Query exceeded predefined limit for document fragments: " + 
                    ExpressionDumper.dump(expr));
            cleanUp();
            throw new TerminatedException.SizeLimitException(expr.getASTNode(),
                    "The constructed document fragment exceeded the predefined size limit (current: " +
                    builder.getSize() + "; allowed: " + maxNodesLimit +
                    "). The query has been killed.");
        }
    }
    
    public void addTemporaryFragment(String docName) {
    	if(tempFragments == null)
    		tempFragments = new LinkedList();
    	tempFragments.add(docName);
    }
    
    public void cleanUp() {
    	if(tempFragments == null)
    		return;
    	context.getBroker().cleanUpTempResources(tempFragments);
    	tempFragments = null;
    }
    
    public void kill(long waitTime) {
    	terminate = true;
    }
    
    public XQueryContext getContext() {
    	return context;
    }
	 
	 public long getStartTime() {
		 return startTime;
	 }
    
    public void reset() {
        startTime = System.currentTimeMillis();
        terminate = false;
    }
}
