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

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.xmldb.concurrent.action.Action;
import org.exist.xmldb.IndexQueryService;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

import static org.junit.Assert.*;

/**
 * Abstract base class for concurrent tests.
 * 
 * @author wolf
 * @author aretter
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
    }

    @After
    public final void tearDownDb() throws XMLDBException {
        final Collection rootCol = existXmldbEmbeddedServer.getRoot();
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
    public void concurrent() throws Exception {

        // make a copy of the actions
        final List<Runner> runners = Collections.unmodifiableList(getRunners());

        // start all threads
        final ExecutorService executorService = Executors.newFixedThreadPool(runners.size());
        final List<Future<Boolean>> futures = new ArrayList<>();
        for (final Runner runner : runners) {
            futures.add(executorService.submit(runner));
        }

        // await first error, or all results
        boolean failed = false;
        Exception failedException = null;
        while (true) {

            if (futures.isEmpty()) {
                break;
            }

            Future<Boolean> completedFuture = null;
            for (final Future<Boolean> future : futures) {
                if (future.isDone()) {
                    completedFuture = future;
                    break;  // exit for-loop
                }
            }

            if (completedFuture != null) {
                // remove the completed future from the list of futures
                futures.remove(completedFuture);

                try {
                    final boolean success = completedFuture.get();
                    if (!success) {
                        failed = true;
                        break;  // exit while-loop
                    }
                } catch (final InterruptedException | ExecutionException e) {
                    if (e instanceof InterruptedException) {
                        // Restore the interrupted status
                        Thread.currentThread().interrupt();
                    }
                    failed = true;
                    failedException = e;
                    break;  // exit while-loop
                }
            } else {
                // sleep, repeat...
                try {
                    Thread.sleep(50);
                } catch (final InterruptedException e) {
                    failed = true;
                    failedException = e;
                    break;  // exit while-loop
                }
            }
        }  // repeat while-loop


        if (failed) {
            executorService.shutdownNow();

            if (failedException != null) {
                throw failedException;
            } else {
                assertFalse(failed);
            }
        }

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
    class Runner implements Callable<Boolean> {
        private final Action action;
        private final int repeat;
        private final long delayBeforeStart;
        private final long delay;

        public Runner(final Action action, final int repeat, final long delayBeforeStart, final long delay) {
            super();
            this.action = action;
            this.repeat = repeat;
            this.delayBeforeStart = delayBeforeStart;
            this.delay = delay;
        }

        /**
         * Returns true if execution completes.
         *
         * @return true if execution completes, false otherwise
         */
        @Override
        public Boolean call() throws XMLDBException, IOException {
            if (delayBeforeStart > 0) {
                if (!sleep(delayBeforeStart)) {
                    return false;
                }
            }

            for (int i = 0; i < repeat; i++) {

                if (!action.execute()) {
                    return false;
                }

                if (delay > 0) {
                    if (!sleep(delay)) {
                        return false;
                    }
                }
            }

            return true;
        }

        /**
         * Sleeps the current thread for a period of time.
         *
         * @param period the period to sleep for.
         *
         * @return true if the thread slept for the period and was not interrupted
         */
        private boolean sleep(final long period) {
            try {
                Thread.sleep(period);
                return true;
            } catch (final InterruptedException e) {
                // Restore the interrupted status
                Thread.currentThread().interrupt();
                return false;
            }
        }
    }
}
