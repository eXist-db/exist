/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
package org.exist.xmldb.concurrent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.TestUtils;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.XmldbURI;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.modules.XMLResource;

import static org.junit.Assert.fail;

public class DeadlockTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(false, true, true);

    private static final Logger LOG = LogManager.getLogger(DeadlockTest.class);

    public static final String DOCUMENT_CONTENT = "<document>\n"
            + "  <element1>value1</element1>\n"
            + "  <element2>value2</element2>\n"
            + "  <element3>value3</element3>\n"
            + "  <element4>value4</element4>\n" + "</document>\n";

    @Test
    public void deadlock() throws Exception {
        final int threads = 20;
        final int resources = 200;

        final Thread[] writerThreads = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            writerThreads[i] = new WriterThread(resources);
            writerThreads[i].setName("T" + i);
            writerThreads[i].start();
        }
        for (int i = 0; i < threads; i++) {
            writerThreads[i].join();
        }
    }

    public static class WriterThread extends Thread {
        protected int resources = 0;

        public WriterThread(final int resources) {
            this.resources = resources;
        }

        @Override
        public void run() {
            try {
                final Collection collection = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, TestUtils.ADMIN_DB_USER, TestUtils.ADMIN_DB_PWD);
                for (int i = 0; i < resources; i++) {
                    final XMLResource document = (XMLResource) collection.createResource(Thread.currentThread().getName() + "_" + i, "XMLResource");
                    document.setContent(DOCUMENT_CONTENT);
                    LOG.debug("Storing document " + document.getId());
                    collection.storeResource(document);
                }
            } catch (final Exception e) {
                LOG.error(e.getMessage(), e);
                fail(e.getMessage());
            }
        }
    }
}