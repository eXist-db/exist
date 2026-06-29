/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import java.text.NumberFormat;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.util.Configuration;
import org.exist.xquery.util.ExpressionDumper;

import javax.annotation.Nullable;


/**
 * @author wolf
 */
@ConfigurationClass("watchdog")
public class XQueryWatchDog {

    private static final Logger LOG = LogManager.getLogger(XQueryWatchDog.class);

    // Shared scheduler for wall-clock kill backstop. A single daemon thread is sufficient:
    // kill() is non-blocking and this executor only fires lightweight Runnables.
    private static final ScheduledThreadPoolExecutor KILL_SCHEDULER;
    static {
        KILL_SCHEDULER = new ScheduledThreadPoolExecutor(1, r -> {
            final Thread t = new Thread(r, "exist-watchdog-kill-scheduler");
            t.setDaemon(true);
            return t;
        });
        KILL_SCHEDULER.setRemoveOnCancelPolicy(true);
    }

    public static final String CONFIGURATION_ELEMENT_NAME = "watchdog";

    public final static String PROPERTY_QUERY_TIMEOUT = "db-connection.watchdog.query-timeout";
    public final static String PROPERTY_OUTPUT_SIZE_LIMIT = "db-connection.watchdog.output-size-limit";

    private final XQueryContext context;

    @ConfigurationFieldAsAttribute("query-timeout")
    private long timeout = Long.MAX_VALUE;

    @ConfigurationFieldAsAttribute("output-size-limit")
    private int maxNodesLimit = Integer.MAX_VALUE;

    private long startTime;

    // volatile: kill() is called from a different thread than proceed(); without visibility
    // guarantee the executing thread may never observe terminate=true (JMM data race).
    private volatile boolean terminate = false;

    // Scheduled future for wall-clock kill; cancelled when the watchdog resets or fires timeout via proceed().
    private volatile ScheduledFuture<?> scheduledKill;

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
        @Nullable final Configuration conf = context.getConfiguration();
        if (conf != null) {
            Object option = conf.getProperty(PROPERTY_QUERY_TIMEOUT);
            if (option != null) {
                timeout = (Long) option;
            }
            if (timeout <= 0) {
                timeout = Long.MAX_VALUE;
            }

            option = conf.getProperty(PROPERTY_OUTPUT_SIZE_LIMIT);
            if (option != null) {
                maxNodesLimit = (Integer) option;
            }
        }
    }

    public void setTimeoutFromOption(Option option) throws XPathException {
    	final String[] contents = option.tokenizeContents();
    	if(contents.length != 1)
    		{throw new XPathException((Expression) null, "Option 'timeout' should have exactly one parameter: the timeout value.");}
		long time;
		try {
			time = Long.parseLong(contents[0]);
		} catch (final NumberFormatException e) {
			throw new XPathException((Expression) null, "Error parsing timeout value in option " + option.getQName().getStringValue());
		}
		if (time <= 0) {
			time = Long.MAX_VALUE;
		}
		setTimeout(time);
		if (LOG.isDebugEnabled()) {
			final NumberFormat nf = NumberFormat.getNumberInstance();
            LOG.debug("timeout set from option: {} ms.", nf.format(timeout));
		}
    }

    public void setTimeout(long time) {
        timeout = time;
        cancelScheduledKill();
        if (time > 0 && time != Long.MAX_VALUE) {
            scheduledKill = KILL_SCHEDULER.schedule(() -> kill(0), time, TimeUnit.MILLISECONDS);
        }
    }

    public void setMaxNodes(int maxNodes) {
    	maxNodesLimit = maxNodes;
    }
    
    public void setMaxNodesFromOption(Option option) throws XPathException {
    	final String[] contents = option.tokenizeContents();
    	if(contents.length != 1)
    		{throw new XPathException((Expression) null, "Option 'output-size-limit' should have exactly one parameter: the output-size-limit value.");}
		try {
			setMaxNodes(Integer.parseInt(contents[0]));
		} catch (final NumberFormatException e) {
			throw new XPathException((Expression) null, "Error parsing output-size-limit value in option " + option.getQName().getStringValue());
		}
		if (LOG.isDebugEnabled()) {
			final NumberFormat nf = NumberFormat.getNumberInstance();
            LOG.debug("output-size-limit set from option: {}", nf.format(maxNodesLimit));
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
                LOG.warn("Query exceeded predefined timeout ({} ms.): {}", nf.format(elapsed), ExpressionDumper.dump(expr));
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
            LOG.warn("Query exceeded predefined output-size-limit ({}) for document fragments: {}", nf.format(maxNodesLimit), ExpressionDumper.dump(expr));
            cleanUp();
            throw new TerminatedException.SizeLimitException(expr.getLine(), expr.getColumn(),
                    "The constructed document fragment exceeded the predefined output-size-limit (current: " +
                    nf.format(builder.getSize()) + "; allowed: " + nf.format(maxNodesLimit) +
                    "). The query has been killed.");
        }
    }
    
    private void cancelScheduledKill() {
        final ScheduledFuture<?> f = scheduledKill;
        if (f != null) {
            f.cancel(false);
            scheduledKill = null;
        }
    }

    public void cleanUp() {
        cancelScheduledKill();
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
        cancelScheduledKill();
        startTime = System.currentTimeMillis();
        terminate = false;
    }
    
    public boolean isTerminating()
    {
    	return( terminate );
    }
}
