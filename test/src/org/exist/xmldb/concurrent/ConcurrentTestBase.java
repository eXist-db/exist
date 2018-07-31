/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2000-04,  Wolfgang M. Meier (wolfgang@exist-db.org)
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Library General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Library General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 *  $Id$
 */
package org.exist.xmldb.concurrent;

import java.util.Collections;
import java.util.List;

import org.exist.TestUtils;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.concurrent.action.Action;
import org.exist.xmldb.IndexQueryService;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * Abstract base class for concurrent tests.
 * 
 * @author wolf
 */
public abstract class ConcurrentTestBase {

    private static String COLLECTION_CONFIG =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" +
    	"	<index>" +
        "       <lucene>" +
        "           <text match=\"/*\"/>" +
        "       </lucene>" +
        "	</index>" +
    	"</collection>";

    protected Collection testCol;
    protected volatile boolean failed = false;

    @ClassRule
    public static final ExistXmldbEmbeddedServer existXmldbEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Before
    public final void startupDb() throws Exception {
        final Collection rootCol = existXmldbEmbeddedServer.getRoot();
        assertNotNull(rootCol);
        final IndexQueryService idxConf = (IndexQueryService) rootCol.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(COLLECTION_CONFIG);
        testCol = rootCol.getChildCollection(getTestCollectionName());
        if (testCol != null) {
            CollectionManagementService mgr = DBUtils.getCollectionManagementService(rootCol);
            mgr.removeCollection(getTestCollectionName());
        }
        testCol = DBUtils.addCollection(rootCol, getTestCollectionName());
        assertNotNull(testCol);
        DBUtils.addXMLResource(rootCol, "biblio.rdf", TestUtils.resolveSample("biblio.rdf"));
    }

    @After
    public final void tearDownDb() throws XMLDBException {
        final Collection rootCol = existXmldbEmbeddedServer.getRoot();
        final Resource res = rootCol.getResource("biblio.rdf");
        assertNotNull(res);
        rootCol.removeResource(res);
        DBUtils.removeCollection(rootCol, getTestCollectionName());

        testCol = null;
    }

    /**
     * Get the name of the test collection.
     *
     * @return the name of the test collection.
     */
    public abstract String getTestCollectionName();

    /**
     * Get the runners for the test
     *
     * @return the runners for the test.
     */
    public abstract List<Runner> getRunners();

    public Collection getTestCollection() {
        return testCol;
    }

    @Test
    public void concurrent() throws XMLDBException {

        // make a copy of the actions
        final List<Runner> runners = Collections.unmodifiableList(getRunners());

        // start all threads
        for (final Thread t : runners) {
            t.start();
        }

        // wait for threads to finish
        try {
            for (final Thread t : runners) {
                t.join();
            }
        } catch (Exception e) {
            e.printStackTrace();
            failed = true;
        }

        assertFalse(failed);
        assertAdditional();
    }

    /**
     * Override this if you need to make
     * additional assertions after the {@link #concurrent()}
     * test has completed.
     */
    protected void assertAdditional() throws XMLDBException {
        // no-op
    }

    /**
     * Runs the specified Action a number of times.
     * 
     * @author wolf
     */
    class Runner extends Thread {
        private final Action action;
        private final int repeat;
        private final long delayBeforeStart;
        private final long delay;

        public Runner(final Action action, final int repeat, final long delayBeforeStart, final long delay) {
            super();
            this.action = action;
            this.repeat = repeat;
            this.delay = delay;
            this.delayBeforeStart = delayBeforeStart;
        }

        @Override
        public void run() {
            if (delayBeforeStart > 0) {
                synchronized (this) {
                    try {
                        wait(delayBeforeStart);
                    } catch (InterruptedException e) {
                        System.err.println("Action failed in Thread " + getName() + ": "
                                + e.getMessage());
                        e.printStackTrace();
                        failed = true;
                    }
                }
            }
            try {
                for (int i = 0; i < repeat; i++) {
                    if (failed) {
                        break;
                    }

                    failed = action.execute();
                    if (delay > 0)
                        synchronized (this) {
                            wait(delay);
                        }
                }
            } catch (Exception e) {
                System.err.println("Action failed in Thread " + getName() + ": " + e.getMessage());
                e.printStackTrace();
                failed = true;
            }
        }
    }
}
