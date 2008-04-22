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
package org.exist.storage;

import java.text.NumberFormat;
import java.util.Iterator;
import java.util.Stack;

import org.apache.log4j.Logger;
import org.exist.source.Source;
import org.exist.util.Configuration;
import org.exist.util.hashtable.Object2ObjectHashMap;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQueryContext;

/**
 * Global pool for pre-compiled XQuery expressions. Expressions are
 * stored and retrieved from the pool by comparing the {@link org.exist.source.Source}
 * objects from which they were created. For each XQuery, a maximum of 
 * {@link #MAX_STACK_SIZE} compiled expressions are kept in the pool.
 * An XQuery expression will be removed from the pool if it has not been
 * used for a pre-defined timeout. These settings can be configured in conf.xml.
 * 
 * @author wolf
 */
public class XQueryPool extends Object2ObjectHashMap {

    public final static int MAX_POOL_SIZE = 128;
    
    public final static int MAX_STACK_SIZE = 5;

    public final static long TIMEOUT = 120000;

    public final static long TIMEOUT_CHECK_INTERVAL = 30000;

    private final static Logger LOG = Logger.getLogger(XQueryPool.class);

    private long lastTimeOutCheck;

    private int maxPoolSize;
    
    private int maxStackSize;

    private long timeout;

    private long timeoutCheckInterval;
    
    public static final String CONFIGURATION_ELEMENT_NAME = "query-pool";
    public static final String MAX_STACK_SIZE_ATTRIBUTE = "max-stack-size";
    public static final String POOL_SIZE_ATTTRIBUTE = "size";
    public static final String TIMEOUT_ATTRIBUTE = "timeout";
    public static final String TIMEOUT_CHECK_INTERVAL_ATTRIBUTE = "timeout-check-interval";
    
    public static final String PROPERTY_MAX_STACK_SIZE = "db-connection.query-pool.max-stack-size";
    public static final String PROPERTY_POOL_SIZE = "db-connection.query-pool.size";
    public static final String PROPERTY_TIMEOUT = "db-connection.query-pool.timeout";
    public static final String PROPERTY_TIMEOUT_CHECK_INTERVAL = 
    	"db-connection.query-pool.timeout-check-interval";

    /**
     * @param conf
     */
    public XQueryPool(Configuration conf) {
        super(27);
        lastTimeOutCheck = System.currentTimeMillis();

        Integer maxStSz = (Integer) conf.getProperty(PROPERTY_MAX_STACK_SIZE);
        Integer maxPoolSz = (Integer) conf.getProperty(PROPERTY_POOL_SIZE);
        Long t = (Long) conf.getProperty(PROPERTY_TIMEOUT);
        Long tci = (Long) conf.getProperty(PROPERTY_TIMEOUT_CHECK_INTERVAL);
        NumberFormat nf = NumberFormat.getNumberInstance();

        if (maxPoolSz != null)
        	maxPoolSize = maxPoolSz.intValue();
        else
        	maxPoolSize = MAX_POOL_SIZE;
        
        if (maxStSz != null)
            maxStackSize = maxStSz.intValue();
        else
            maxStackSize = MAX_STACK_SIZE;

        if (t != null)
            timeout = t.longValue();
        else
            timeout = TIMEOUT;

        //TODO : check that it is inferior to t
        if (tci != null)
            timeoutCheckInterval = tci.longValue();
        else
            timeoutCheckInterval = TIMEOUT_CHECK_INTERVAL;

        LOG.info("QueryPool: size = " + nf.format(maxPoolSize) + "; maxStackSize = " + nf.format(maxStackSize) + 
        		"; timeout = " + nf.format(timeout) + 
        		"; timeoutCheckInterval = " + nf.format(timeoutCheckInterval));

    }

    public synchronized void returnCompiledXQuery(Source source,
            CompiledXQuery xquery) {
        if (size() < maxPoolSize) {
            Stack stack = (Stack) get(source);
            if (stack == null) {
                stack = new Stack();
                source.setCacheTimestamp(System.currentTimeMillis());
                put(source, stack);
            }
            if (stack.size() < maxStackSize) {
                stack.push(xquery);
            }
        }
        timeoutCheck();
    }

    public synchronized CompiledXQuery borrowCompiledXQuery(DBBroker broker, Source source) {
        int idx = getIndex(source);
        if (idx < 0) {
        	return null;
        }
        Source key = (Source) keys[idx];
        int validity = key.isValid(broker);
        if (validity == Source.UNKNOWN)
            validity = key.isValid(source);
        if (validity == Source.INVALID || validity == Source.UNKNOWN) {
            keys[idx] = REMOVED;
            values[idx] = null;
            LOG.debug(source.getKey() + " is invalid");
            return null;
        }
        Stack stack = (Stack) values[idx];
        if (stack != null && !stack.isEmpty()) {
            // now check if the compiled expression is valid
            // it might become invalid if an imported module has changed.
            CompiledXQuery query = (CompiledXQuery) stack.pop();
            XQueryContext context = query.getContext();
            context.setBroker(broker);
            if (!query.isValid()) {
                // the compiled query is no longer valid: one of the imported
                // modules may have changed
                remove(key);
                return null;
            } else
                return query;
        }
        return null;
    }

    private void timeoutCheck() {
        final long currentTime = System.currentTimeMillis();
        
        if (timeoutCheckInterval < 0)
            return; 
        
        if (currentTime - lastTimeOutCheck < timeoutCheckInterval)
            return;

        for (Iterator i = iterator(); i.hasNext();) {
            Source next = (Source) i.next();
            if (currentTime - next.getCacheTimestamp() > timeout) {
                remove(next);
            }
        }
    }
}

