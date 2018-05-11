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

import java.io.IOException;

import org.exist.xquery.XPathException;
import org.exist.xquery.value.Sequence;

import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.xquery.value.BinaryValue;

/**
 * Simple container for the results of a query. Used to cache
 * query results that may be retrieved later by the client.
 *
 * @author wolf
 * @author jmfernandez
 */
public class QueryResult extends AbstractCachedResult {

    private final static Logger LOG = LogManager.getLogger(QueryResult.class);
    protected Sequence result;
    protected Properties serialization = null;
    // set upon failure
    protected XPathException exception = null;

    public QueryResult(final Sequence result, final Properties outputProperties) {
        this(result, outputProperties, 0);
    }

    public QueryResult(final Sequence result, final Properties outputProperties, final long queryTime) {
        super(queryTime);
        this.serialization = outputProperties;
        this.result = result;
    }

    public QueryResult(final XPathException e) {
        exception = e;
    }

    public boolean hasErrors() {
        return exception != null;
    }

    public XPathException getException() {
        return exception;
    }

    /**
     * @return Returns the result.
     */
    @Override
    public Sequence getResult() {
        return result;
    }

    @Override
    protected void doClose() {
        if (result != null) {

            //cleanup any binary values
            if (result instanceof BinaryValue) {
                try {
                    ((BinaryValue) result).close();
                } catch (final IOException ioe) {
                    LOG.warn("Unable to cleanup BinaryValue: " + result.hashCode(), ioe);
                }
            }

            result = null;
        }
    }
}
