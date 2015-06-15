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

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.exist.xmldb.concurrent.action.Action;
import org.exist.xmldb.IndexQueryService;
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

    protected String rootColURI;

    protected Collection rootCol;

    protected String testColName;

    protected Collection testCol;

    protected List<Runner> actions = new ArrayList<>(5);

    protected volatile boolean failed = false;

    /**
     * @param uri the XMLDB URI of the root collection.
     * @param testCollection the name of the collection that will be created for the test.
     */
    public ConcurrentTestBase(String uri, String testCollection) {
        this.rootColURI = uri;
        this.testColName = testCollection;
    }

    /**
     * Add an {@link Action} to the list of actions that will be processed
     * concurrently. Should be called after {@link #setUp()}.
     * 
     * @param action the action.
     * @param repeat number of times the actions should be repeated.
     */
    public void addAction(Action action, int repeat, long delayBeforeStart, long delay) {
        actions.add(new Runner(action, repeat, delayBeforeStart, delay));
    }

    public Collection getTestCollection() {
        return testCol;
    }

    @Test
    public void concurrent() {
        // start all threads
        for (Thread t : actions) {
            t.start();
        }

        // wait for threads to finish
        try {
            for (Thread t : actions) {
                t.join();
            }
        } catch (Exception e) {
            e.printStackTrace();
            failed = true;
        }

        assertFalse(failed);
    }

    public void setUp() throws Exception {
        rootCol = DBUtils.setupDB(rootColURI);
        assertNotNull(rootCol);
        IndexQueryService idxConf = (IndexQueryService) rootCol.getService("IndexQueryService", "1.0");
        idxConf.configureCollection(COLLECTION_CONFIG);
        testCol = rootCol.getChildCollection(testColName);
        if (testCol != null) {
            CollectionManagementService mgr = DBUtils.getCollectionManagementService(rootCol);
            mgr.removeCollection(testColName);
        }
        testCol = DBUtils.addCollection(rootCol, testColName);
        assertNotNull(testCol);

        String existHome = System.getProperty("exist.home");
        File existDir = existHome==null ? new File(".") : new File(existHome);
        DBUtils.addXMLResource(rootCol, "biblio.rdf", new File(existDir,"samples/biblio.rdf"));
    }

    public void tearDown() throws XMLDBException {
        Resource res = rootCol.getResource("biblio.rdf");
        assertNotNull(res);
        rootCol.removeResource(res);
        DBUtils.removeCollection(rootCol, testColName);
        DBUtils.shutdownDB(rootColURI);

        rootCol = null;
        testCol = null;
    }

    /**
     * Runs the specified Action a number of times.
     * 
     * @author wolf
     */
    class Runner extends Thread {

        private Action action;

        private int repeat;

        private long delay = 0;

        private long delayBeforeStart = 0;

        public Runner(Action action, int repeat, long delayBeforeStart, long delay) {
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
