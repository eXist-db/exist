/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.http;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.lock.Lock;
import org.exist.storage.lock.LockManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.servlet.http.HttpSession;
import javax.servlet.http.HttpSessionEvent;

import java.io.IOException;
import java.util.Optional;

import static org.easymock.EasyMock.*;
import static org.junit.Assert.assertFalse;

@RunWith(ParallelRunner.class)
public class AuditTrailSessionListenerTest {

    @ClassRule
    public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

    private static final XmldbURI TEST_COLLECTION = XmldbURI.create("/db/test");
    private static final String CREATE_SCRIPT = "session-create.xq";
    private static final String DESTROYED_SCRIPT = "session-destroyed.xq";
    private static final String CREATE_SCRIPT_PATH = "/db/test/" + CREATE_SCRIPT;
    private static final String DESTROYED_SCRIPT_PATH = "/db/test/" + DESTROYED_SCRIPT;

    /**
     * Ensures that AuditTrailSessionListener releases any locks
     * on the XQuery document when creating a session
     */
    @Test
    public void sessionCreated() throws EXistException, PermissionDeniedException {
        final HttpSessionEvent httpSessionEvent = createMock(HttpSessionEvent.class);
        final HttpSession httpSession = createMock(HttpSession.class);
        expect(httpSessionEvent.getSession()).andReturn(httpSession);
        expect(httpSession.getId()).andReturn("mock-session");

        replay(httpSessionEvent, httpSession);

        final AuditTrailSessionListener listener = new AuditTrailSessionListener();
        listener.sessionCreated(httpSessionEvent);

        verify(httpSessionEvent, httpSession);

        final XmldbURI docUri = XmldbURI.create(CREATE_SCRIPT_PATH);
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().getBroker();
                final LockedDocument lockedResource = broker.getXMLResource(docUri, Lock.LockMode.NO_LOCK)) {

            // ensure that AuditTrailSessionListener released the lock
            final LockManager lockManager = broker.getBrokerPool().getLockManager();
            assertFalse(lockManager.isDocumentLockedForRead(docUri));
            assertFalse(lockManager.isDocumentLockedForWrite(docUri));
        }
    }

    /**
     * Ensures that AuditTrailSessionListener releases any locks
     * on the XQuery document when destroying a session
     */
    @Test
    public void sessionDestroyed() throws EXistException, PermissionDeniedException {
        final HttpSessionEvent httpSessionEvent = createMock(HttpSessionEvent.class);
        final HttpSession httpSession = createMock(HttpSession.class);
        expect(httpSessionEvent.getSession()).andReturn(httpSession);
        expect(httpSession.getId()).andReturn("mock-session");

        replay(httpSessionEvent, httpSession);

        final AuditTrailSessionListener listener = new AuditTrailSessionListener();
        listener.sessionDestroyed(httpSessionEvent);

        verify(httpSessionEvent, httpSession);

        final XmldbURI docUri = XmldbURI.create(DESTROYED_SCRIPT_PATH);
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().getBroker();
                final LockedDocument lockedResource = broker.getXMLResource(docUri, Lock.LockMode.NO_LOCK)) {

            // ensure that AuditTrailSessionListener released the lock
            final LockManager lockManager = broker.getBrokerPool().getLockManager();
            assertFalse(lockManager.isDocumentLockedForRead(docUri));
            assertFalse(lockManager.isDocumentLockedForWrite(docUri));
        }
    }

    @BeforeClass
    public static void setup() throws EXistException, LockException, TriggerException, PermissionDeniedException, IOException {
        storeScripts();
        System.setProperty(AuditTrailSessionListener.REGISTER_CREATE_XQUERY_SCRIPT_PROPERTY, CREATE_SCRIPT_PATH);
        System.setProperty(AuditTrailSessionListener.REGISTER_DESTROY_XQUERY_SCRIPT_PROPERTY, DESTROYED_SCRIPT_PATH);
    }

    private static void storeScripts() throws EXistException, PermissionDeniedException, IOException, TriggerException, LockException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()));
                final Txn transaction = existEmbeddedServer.getBrokerPool().getTransactionManager().beginTransaction()) {

            final Collection testCollection = broker.getOrCreateCollection(transaction, TEST_COLLECTION);
            testCollection.addBinaryResource(transaction, broker, XmldbURI.create(CREATE_SCRIPT), "<create/>".getBytes(), "application/xquery");
            testCollection.addBinaryResource(transaction, broker, XmldbURI.create(DESTROYED_SCRIPT), "</destroyed>".getBytes(), "application/xquery");

            transaction.commit();
        }
    }

    @AfterClass
    public static void teardown() throws TriggerException, PermissionDeniedException, EXistException, IOException {
        System.clearProperty(AuditTrailSessionListener.REGISTER_CREATE_XQUERY_SCRIPT_PROPERTY);
        System.clearProperty(AuditTrailSessionListener.REGISTER_DESTROY_XQUERY_SCRIPT_PROPERTY);
        removeScripts();
    }

    private static void removeScripts() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        try(final DBBroker broker = existEmbeddedServer.getBrokerPool().get(Optional.of(existEmbeddedServer.getBrokerPool().getSecurityManager().getSystemSubject()));
                final Txn transaction = existEmbeddedServer.getBrokerPool().getTransactionManager().beginTransaction()) {
            final Collection testCollection = broker.getCollection(TEST_COLLECTION);
            if(testCollection != null) {
                broker.removeCollection(transaction, testCollection);
            }
            transaction.commit();
        }
    }
}
