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

package org.exist.validation;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.io.InputStreamUtil;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.value.Sequence;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;

/**
 *  A set of helper methods for the validation tests.
 *
 * @author Dannes Wessels (dizzzz@exist-db.org)
 */
public class TestTools {

    public final static String VALIDATION_HOME_COLLECTION = "validation";
    public final static String VALIDATION_DTD_COLLECTION = "dtd";
    public final static String VALIDATION_XSD_COLLECTION = "xsd";
    public final static String VALIDATION_TMP_COLLECTION = "tmp";
    
    /**
     *
     * @param document     File to be uploaded
     * @param target  Target URL (e.g. xmldb:exist:///db/collection/document.xml)
     * @throws java.lang.Exception  Oops.....
     */
    public static void insertDocumentToURL(final InputStream document, final String target) throws IOException {
        final URL url = new URL(target);
        final URLConnection connection = url.openConnection();
        try (final OutputStream os = connection.getOutputStream()) {
            InputStreamUtil.copy(document, os);
        }
    }

    public static Sequence executeQuery(final BrokerPool pool, final String query) throws EXistException, PermissionDeniedException, XPathException {
        final XQuery xquery = pool.getXQueryService();
        try(final DBBroker broker = pool.getBroker()) {
            return xquery.execute(broker, query, null);
        }
    }
}
