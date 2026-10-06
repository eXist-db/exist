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

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
@Execution(ExecutionMode.CONCURRENT)
class PersistentDescendantOrSelfNodeKindTest extends AbstractDescendantOrSelfNodeKindTest {

    private static final String TEST_DOCUMENT_NAME = "PersistentDescendantOrSelfNodeKindTest.xml";

    private String getDbQuery(final String queryPostfix) {
        return "let $doc := doc('/db/" + TEST_DOCUMENT_NAME + "')\n" +
            "return\n" +
            queryPostfix;
    }

    @Override
    protected ResourceSet executeQueryOnDoc(final String docQuery) throws XMLDBException {
        return  existEmbeddedServer.executeQuery(getDbQuery(docQuery));
    }

    @BeforeAll
    static void storeTestDoc() throws XMLDBException {
        final Collection root =  existEmbeddedServer.getRoot();
        final XMLResource res = root.createResource(TEST_DOCUMENT_NAME, XMLResource.class);
        res.setContent(TEST_DOCUMENT);
        root.storeResource(res);
    }

    @AfterAll
    static void removeTestDoc() throws XMLDBException {
        final Collection root =  existEmbeddedServer.getRoot();
        final Resource res = root.getResource(TEST_DOCUMENT_NAME);
        root.removeResource(res);
    }
}
