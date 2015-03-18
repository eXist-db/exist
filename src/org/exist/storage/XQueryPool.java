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
import java.util.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsAttribute;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.util.Configuration;
import org.exist.util.hashtable.Object2ObjectHashMap;
import org.exist.xquery.*;

/**
 * Global pool for pre-compiled XQuery expressions. Expressions are stored and
 * retrieved from the pool by comparing the {@link org.exist.source.Source}
 * objects from which they were created. For each XQuery, a maximum of
 * {@link #MAX_STACK_SIZE} compiled expressions are kept in the pool. An XQuery
 * expression will be removed from the pool if it has not been used for a
 * pre-defined timeout. These settings can be configured in conf.xml.
 * 
 * @author wolf
 */
@ConfigurationClass("query-pool")
public class XQueryPool extends Object2ObjectHashMap {

	public final static int MAX_POOL_SIZE = 128;

	public final static int MAX_STACK_SIZE = 5;

	public final static long TIMEOUT = 120000L;

	public final static long TIMEOUT_CHECK_INTERVAL = 30000L;

	private final static Logger LOG = LogManager.getLogger(XQueryPool.class);

	private long lastTimeOutCheck;
	private long lastTimeOfCleanup;

	@ConfigurationFieldAsAttribute("size")
	private int maxPoolSize;

	@ConfigurationFieldAsAttribute("max-stack-size")
	private int maxStackSize;

	@ConfigurationFieldAsAttribute("timeout")
	private long timeout;

	@ConfigurationFieldAsAttribute("timeout-check-interval")
	private long timeoutCheckInterval;

	public static final String CONFIGURATION_ELEMENT_NAME = "query-pool";
	public static final String MAX_STACK_SIZE_ATTRIBUTE = "max-stack-size";
	public static final String POOL_SIZE_ATTTRIBUTE = "size";
	public static final String TIMEOUT_ATTRIBUTE = "timeout";
	public static final String TIMEOUT_CHECK_INTERVAL_ATTRIBUTE = "timeout-check-interval";

	public static final String PROPERTY_MAX_STACK_SIZE = "db-connection.query-pool.max-stack-size";
	public static final String PROPERTY_POOL_SIZE = "db-connection.query-pool.size";
	public static final String PROPERTY_TIMEOUT = "db-connection.query-pool.timeout";
	public static final String PROPERTY_TIMEOUT_CHECK_INTERVAL = "db-connection.query-pool.timeout-check-interval";

	/**
	 * @param conf
	 */
	public XQueryPool(Configuration conf) {
		super(27);
		lastTimeOutCheck = lastTimeOfCleanup = System.currentTimeMillis();

		final Integer maxStSz = (Integer) conf.getProperty(PROPERTY_MAX_STACK_SIZE);
		final Integer maxPoolSz = (Integer) conf.getProperty(PROPERTY_POOL_SIZE);
		final Long t = (Long) conf.getProperty(PROPERTY_TIMEOUT);
		final Long tci = (Long) conf.getProperty(PROPERTY_TIMEOUT_CHECK_INTERVAL);
		final NumberFormat nf = NumberFormat.getNumberInstance();

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

		if (tci != null)
			timeoutCheckInterval = tci.longValue();
		else
			timeoutCheckInterval = TIMEOUT_CHECK_INTERVAL;

		LOG.info("QueryPool: " +
			"size = " + nf.format(maxPoolSize) + "; " +
			"maxStackSize = " + nf.format(maxStackSize) + "; " +
			"timeout = " + nf.format(timeout) + "; " +
			"timeoutCheckInterval = " + nf.format(timeoutCheckInterval));
	}

	public void returnCompiledXQuery(Source source, CompiledXQuery xquery) {
		returnObject(source, xquery);
	}

	private synchronized void returnObject(Source source, Object o) {
		long ts = source.getCacheTimestamp();
		if (ts == 0 || ts > lastTimeOfCleanup) {
			if (size() >= maxPoolSize)
				timeoutCheck();

			if (size() < maxPoolSize) {
				Stack stack = (Stack) get(source);
				if (stack == null) {
					stack = new Stack();
					source.setCacheTimestamp(System.currentTimeMillis());
					put(source, stack);
				}
				if (stack.size() < maxStackSize) {
					for (int i = 0; i < stack.size(); i++) {
						if (stack.get(i) == o)
							// query already in pool. may happen for modules.
							// don't add it a second time.
							return;
					}
					stack.push(o);
				}
			}
		}
	}

	private Object borrowObject(DBBroker broker, Source source) {

        CompiledXQuery query = null;
        Source key = null;

        synchronized (this) {
            final int idx = getIndex(source);
            if (idx < 0) {
                return null;
            }
            key = (Source) keys[idx];
            int validity = key.isValid(broker);
            if (validity == Source.UNKNOWN)
                validity = key.isValid(source);

            if (validity == Source.INVALID || validity == Source.UNKNOWN) {
                keys[idx] = REMOVED;
                values[idx] = null;
                LOG.debug(source.getKey() + " is invalid");
                return null;
            }
            final Stack stack = (Stack) values[idx];
            if (stack == null || stack.isEmpty())
                return null;

            // now check if the compiled expression is valid
            // it might become invalid if an imported module has changed.
            query = (CompiledXQuery) stack.pop();
            final XQueryContext context = query.getContext();
            //context.setBroker(broker);
        }

        // query.isValid() may open collections which in turn tries to acquire
        // org.exist.storage.lock.ReentrantReadWriteLock. In order to avoid
        // deadlocks with concurrent queries holding that lock while borrowing
        // we must not hold onto the XQueryPool while calling isValid().

		if (!query.isValid()) synchronized (this) {
			// the compiled query is no longer valid: one of the imported
			// modules may have changed
			remove(key);
			return null;
		} else
			return query;
	}

	public synchronized CompiledXQuery borrowCompiledXQuery(DBBroker broker, Source source) throws PermissionDeniedException {
		final CompiledXQuery query = (CompiledXQuery) borrowObject(broker, source);
		if (query == null)
			return null;
		
		//check execution permission
		source.validate(broker.getSubject(), Permission.EXECUTE);
		
		// now check if the compiled expression is valid
		// it might become invalid if an imported module has changed.
		//final XQueryContext context = query.getContext();
		//context.setBroker(broker);
		return query;
	}

    public synchronized void clear() {
    	lastTimeOfCleanup = System.currentTimeMillis();
        
        for (final Iterator i = iterator(); i.hasNext();) {
            final Source next = (Source) i.next();
            remove(next);
        }
    }

	private void timeoutCheck() {
		if (timeoutCheckInterval < 0L)
			return;

		final long currentTime = System.currentTimeMillis();

		if (currentTime - lastTimeOutCheck < timeoutCheckInterval)
			return;

		for (final Iterator i = iterator(); i.hasNext();) {
			final Source next = (Source) i.next();
			if (currentTime - next.getCacheTimestamp() > timeout) {
				remove(next);
			}
		}
		
		lastTimeOutCheck = currentTime;
	}
}
