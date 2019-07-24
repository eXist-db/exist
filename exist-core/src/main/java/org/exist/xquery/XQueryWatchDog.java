/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2004-2009 The eXist Project
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
package org.exist.xquery;

import java.text.NumberFormat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.xquery.util.ExpressionDumper;


/**
 * @author wolf
 */
@ConfigurationClass("watchdog")
public class XQueryWatchDog {

    private static final Logger LOG = LogManager.getLogger(XQueryWatchDog.class);
    
    public static final String CONFIGURATION_ELEMENT_NAME = "watchdog";
    
    public final static String PROPERTY_QUERY_TIMEOUT = "db-connection.watchdog.query-timeout";
    public final static String PROPERTY_OUTPUT_SIZE_LIMIT = "db-connection.watchdog.output-size-limit";

    private final XQueryContext context;
    
    @ConfigurationFieldAsAttribute("query-timeout")
    private long timeout = Long.MAX_VALUE;
    
    @ConfigurationFieldAsAttribute("output-size-limit")
    private int maxNodesLimit = Integer.MAX_VALUE;
    
    private long startTime;
    
    private boolean terminate = false;

    private String runningThread = null;

    public XQueryWatchDog(XQueryContext context) {
        this.context = context;
        configureDefaults();
        reset();
    }

    /**
     * Track the name of the thread currently running this query.
     * Used for JMX stats.
     *
     * @param name name of the thread
     */
    public void setRunningThread(String name) {
        this.runningThread = name;
    }

    /**
     * Get the name of last thread which has been running this query.
     *
     * @return name of the last thread
     */
    public String getRunningThread() {
        return runningThread;
    }

    private void configureDefaults() {
    	final DBBroker broker = context.getBroker();
        final Configuration conf = broker.getBrokerPool().getConfiguration();
        Object option = conf.getProperty(PROPERTY_QUERY_TIMEOUT);
        if(option != null)
            {timeout = ((Long)option).longValue();}
        if(timeout <= 0)
            {timeout = Long.MAX_VALUE;}
        option = conf.getProperty(PROPERTY_OUTPUT_SIZE_LIMIT);
        if(option != null)
            {maxNodesLimit = ((Integer)option).intValue();}
    }
    
    public void setTimeoutFromOption(Option option) throws XPathException {
    	final String[] contents = option.tokenizeContents();
    	if(contents.length != 1)
    		{throw new XPathException("Option 'timeout' should have exactly one parameter: the timeout value.");}
		try {
			timeout = Long.parseLong(contents[0]);
		} catch (final NumberFormatException e) {
			throw new XPathException("Error parsing timeout value in option " + option.getQName().getStringValue());
		}
		if (LOG.isDebugEnabled()) {
			final NumberFormat nf = NumberFormat.getNumberInstance();
			LOG.debug("timeout set from option: " + nf.format(timeout) + " ms.");
		}
    }

    public void setTimeout(long time) {
        timeout = time;
    }

    public void setMaxNodes(int maxNodes) {
    	maxNodesLimit = maxNodes;
    }
    
    public void setMaxNodesFromOption(Option option) throws XPathException {
    	final String[] contents = option.tokenizeContents();
    	if(contents.length != 1)
    		{throw new XPathException("Option 'output-size-limit' should have exactly one parameter: the output-size-limit value.");}
		try {
			setMaxNodes(Integer.parseInt(contents[0]));
		} catch (final NumberFormatException e) {
			throw new XPathException("Error parsing output-size-limit value in option " + option.getQName().getStringValue());
		}
		if (LOG.isDebugEnabled()) {
			final NumberFormat nf = NumberFormat.getNumberInstance();
			LOG.debug("output-size-limit set from option: " + nf.format(maxNodesLimit));
		}
    }
    
    public void proceed(Expression expr) throws TerminatedException {
    	if(terminate) {
    		if(expr == null)
    			{expr = context.getRootExpression();}
    		cleanUp();
    		throw new TerminatedException(expr.getLine(), expr.getColumn(),
    				"The query has been killed by the server.");
    	}
        if (timeout != Long.MAX_VALUE) {
            final long elapsed = System.currentTimeMillis() - startTime;
            if(elapsed > timeout) {
                if(expr == null)
                    {expr = context.getRootExpression();}
                final NumberFormat nf = NumberFormat.getNumberInstance();
                LOG.warn("Query exceeded predefined timeout (" + nf.format(elapsed) + " ms.): " +
                        ExpressionDumper.dump(expr));
                cleanUp();
                throw new TerminatedException.TimeoutException(expr.getLine(), expr.getColumn(),
                        "The query exceeded the predefined timeout and has been killed.");
            }
        }
    }
    
    public void proceed(Expression expr, MemTreeBuilder builder) throws TerminatedException {
        proceed(expr);
        if(maxNodesLimit > 0 && builder.getSize() > maxNodesLimit) {
            if(expr == null)
                {expr = context.getRootExpression();}
            final NumberFormat nf = NumberFormat.getNumberInstance();
            LOG.warn("Query exceeded predefined output-size-limit (" +  nf.format(maxNodesLimit) + ") for document fragments: " + 
                    ExpressionDumper.dump(expr));
            cleanUp();
            throw new TerminatedException.SizeLimitException(expr.getLine(), expr.getColumn(),
                    "The constructed document fragment exceeded the predefined output-size-limit (current: " +
                    nf.format(builder.getSize()) + "; allowed: " + nf.format(maxNodesLimit) +
                    "). The query has been killed.");
        }
    }
    
    public void cleanUp() {
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
    
    public boolean isTerminating()
    {
    	return( terminate );
    }
}
