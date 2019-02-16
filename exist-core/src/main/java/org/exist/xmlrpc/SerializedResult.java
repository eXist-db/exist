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

import org.exist.util.io.TemporaryFileManager;
import org.exist.xquery.XPathException;

import java.nio.file.Path;

/**
 * Simple container for the results of a query. Used to cache
 * query results that may be retrieved later by the client.
 *
 * @author jmfernandez
 */
public class SerializedResult extends AbstractCachedResult {
    protected Path result;

    // set upon failure
    protected XPathException exception = null;

    public SerializedResult(final Path result) {
        this(result, 0);
    }

    public SerializedResult(final Path result, final long queryTime) {
        super(queryTime);
        this.result = result;
    }

    public SerializedResult(final XPathException e) {
        exception = e;
    }

    /**
     * @return Returns the result.
     */
    @Override
    public Path getResult() {
        return result;
    }

    @Override
    protected void doClose() {
        if (result != null) {
            TemporaryFileManager.getInstance().returnTemporaryFile(result);
            result = null;
        }
    }
}
