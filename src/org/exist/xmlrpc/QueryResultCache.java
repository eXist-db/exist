/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.xmlrpc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Date;

/**
 * Used by {@link XmldbRequestProcessorFactory} to cache query results. Each query result
 * is identified by a unique integer id.
 */
public class QueryResultCache {

    public final static int TIMEOUT = 180000;

    private static final int INITIAL_SIZE = 254;

    private AbstractCachedResult[] results;

    private static final Logger LOG = LogManager.getLogger(QueryResultCache.class);

    public QueryResultCache() {
        results = new AbstractCachedResult[INITIAL_SIZE];
    }

    public int add(final AbstractCachedResult qr) {
        for (int i = 0; i < results.length; i++) {
            if (results[i] == null) {
                results[i] = qr;
                return i;
            }
        }
        // no empty bucket. need to resize.
        final AbstractCachedResult[] temp = new AbstractCachedResult[(results.length * 3) / 2];
        System.arraycopy(results, 0, temp, 0, results.length);
        final int pos = results.length;
        temp[pos] = qr;
        results = temp;
        return pos;
    }

    public AbstractCachedResult get(final int pos) {
        if (pos < 0 || pos >= results.length) {
            return null;
        }
        return results[pos];
    }

    public QueryResult getResult(final int pos) {
        final AbstractCachedResult acr = get(pos);
        return (acr != null && acr instanceof QueryResult) ? (QueryResult) acr : null;
    }

    public SerializedResult getSerializedResult(final int pos) {
        final AbstractCachedResult acr = get(pos);
        return (acr != null && acr instanceof SerializedResult) ? (SerializedResult) acr : null;
    }

    public void remove(final int pos) {
        if (pos > -1 && pos < results.length) {
            // Perhaps we should not free resources here
            // but an explicit remove implies you want
            // to free resources

            if (results[pos] != null) { // Prevent NPE
                results[pos].free();
                results[pos] = null;
            }
        }
    }

    public void remove(final int pos, final int hash) {
        if (pos > -1 && pos < results.length && (results[pos] != null && results[pos].hashCode() == hash)) {
            // Perhaps we should not free resources here
            // but an explicit remove implies you want
            // to free resources
            results[pos].free();
            results[pos] = null;
        }
    }

    public void checkTimestamps() {
        final long now = System.currentTimeMillis();
        for (int i = 0; i < results.length; i++) {
            final AbstractCachedResult result = results[i];
            if (result != null) {
                if (now - result.getTimestamp() > TIMEOUT) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Removing result set " + new Date(result.getTimestamp()).toString());
                    }
                    // Here we should not free resources, because they could be still in use
                    // by other threads, so leave the work to the garbage collector
                    results[i] = null;
                }
            }
        }
    }
}
