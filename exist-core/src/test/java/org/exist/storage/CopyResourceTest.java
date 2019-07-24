/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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
package org.exist.storage;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.*;
import org.exist.security.SecurityManager;
import org.exist.security.internal.aider.GroupAider;
import org.exist.security.internal.aider.UserAider;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.hamcrest.Matcher;
import org.junit.*;
import org.xml.sax.SAXException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import java.io.IOException;
import java.util.Optional;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.TestUtils.ADMIN_DB_USER;
import static org.exist.TestUtils.ADMIN_DB_PWD;
import static org.exist.security.SecurityManager.DBA_GROUP;
import static org.exist.storage.DBBroker.PreserveType.*;
import static org.exist.test.TestConstants.TEST_COLLECTION_URI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.allOf;

/**
 * Tests to ensure that document content and attributes
 * are correctly copied under various circumstances.
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class CopyResourceTest {

    private static final String USER1_NAME = "user1";
    private static final String USER1_PWD = USER1_NAME;
    private static final String USER2_NAME = "user2";
    private static final String USER2_PWD = USER2_NAME;
    private static final String GROUP1_NAME = "group1";

    private static final XmldbURI USER1_DOC1 = XmldbURI.create("u1d1.xml");
    private static final XmldbURI USER1_DOC2 = XmldbURI.create("u1d2.xml");
    private static final XmldbURI USER1_DOC3 = XmldbURI.create("u1d3.xml");
    private static final XmldbURI USER1_NEW_DOC = XmldbURI.create("u1nx.xml");
    private static final XmldbURI USER1_BIN_DOC1 = XmldbURI.create("u1d1.bin");
    private static final XmldbURI USER1_BIN_DOC2 = XmldbURI.create("u1d2.bin");
    private static final XmldbURI USER1_BIN_DOC3 = XmldbURI.create("u1d3.bin");
    private static final XmldbURI USER1_NEW_BIN_DOC = XmldbURI.create("u1nx.bin");

    private static final XmldbURI USER2_DOC2 = XmldbURI.create("u2d2.xml");
    private static final XmldbURI USER2_DOC3 = XmldbURI.create("u2d3.xml");
    private static final XmldbURI USER2_NEW_DOC = XmldbURI.create("u2nx.xml");
    private static final XmldbURI USER2_BIN_DOC2 = XmldbURI.create("u2d2.bin");
    private static final XmldbURI USER2_BIN_DOC3 = XmldbURI.create("u2d3.bin");
    private static final XmldbURI USER2_NEW_BIN_DOC = XmldbURI.create("u2nx.bin");

    private static final int USER1_DOC1_MODE = 0444;  // r--r--r--
    private static final int USER1_DOC2_MODE = 0644;  // rw-r--r--
    private static final int USER1_DOC3_MODE = 0664;  // rw-rw--r--
    private static final int USER1_BIN_DOC1_MODE = 0444;  // r--r--r--
    private static final int USER1_BIN_DOC2_MODE = 0644;  // rw-r--r--
    private static final int USER1_BIN_DOC3_MODE = 0664;  // rw-rw--r--

    private static final int USER2_DOC2_MODE = 0644;  // rw-r--r--
    private static final int USER2_DOC3_MODE = 0664;  // rw-rw--r--
    private static final int USER2_BIN_DOC2_MODE = 0644;  // rw-r--r--
    private static final int USER2_BIN_DOC3_MODE = 0664;  // rw-rw--r--

    @ClassRule
    public static final ExistEmbeddedServer existWebServer = new ExistEmbeddedServer(true, true);

    /**
     * As the owner copy {@link #USER1_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER1_NEW_DOC}.
     */
    @Test
    public void copyXmlToNonExistentAsSelf() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        copyDoc(user1, NO_PRESERVE, USER1_DOC1, USER1_NEW_DOC);
        checkAttributes(USER1_NEW_DOC, USER1_NAME, USER1_NAME, USER1_DOC1_MODE, not(getCreated(USER1_DOC1)), not(getLastModified(USER1_DOC1)));
    }

    /**
     * As the owner copy {@link #USER1_BIN_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER1_NEW_BIN_DOC}.
     */
    @Test
    public void copyBinaryToNonExistentAsSelf() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        copyDoc(user1, NO_PRESERVE, USER1_BIN_DOC1, USER1_NEW_BIN_DOC);
        checkAttributes(USER1_NEW_BIN_DOC, USER1_NAME, USER1_NAME, USER1_BIN_DOC1_MODE, not(getCreated(USER1_BIN_DOC1)), not(getLastModified(USER1_BIN_DOC1)));
    }

    /**
     * As the owner copy {@link #USER1_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} already existing {@link #USER1_DOC2}.
     */
    @Test
    public void copyXmlToExistentAsSelf() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException, InterruptedException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        final long originalDoc2LastModified = getLastModified(USER1_DOC2);
        Thread.sleep(5);
        copyDoc(user1, NO_PRESERVE, USER1_DOC1, USER1_DOC2);
        checkAttributes(USER1_DOC2, USER1_NAME, USER1_NAME, USER1_DOC2_MODE, equalTo(getCreated(USER1_DOC2)), allOf(not(getLastModified(USER1_DOC1)), not(originalDoc2LastModified)));
    }

    /**
     * As the owner copy {@link #USER1_BIN_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} already existing {@link #USER1_BIN_DOC2}.
     */
    @Test
    public void copyBinaryToExistentAsSelf() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException, InterruptedException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        final long originalBinDoc2LastModified = getLastModified(USER1_BIN_DOC2);
        Thread.sleep(5);
        copyDoc(user1, NO_PRESERVE, USER1_BIN_DOC1, USER1_BIN_DOC2);
        checkAttributes(USER1_BIN_DOC2, USER1_NAME, USER1_NAME, USER1_BIN_DOC2_MODE, equalTo(getCreated(USER1_BIN_DOC2)), allOf(not(getLastModified(USER1_BIN_DOC1)), not(originalBinDoc2LastModified)));
    }

    /**
     * As a DBA copy {@link #USER1_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER1_NEW_DOC}.
     */
    @Test
    public void copyXmlToNonExistentAsDBA() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject adminUser = existWebServer.getBrokerPool().getSecurityManager().authenticate(ADMIN_DB_USER, ADMIN_DB_PWD);
        copyDoc(adminUser, NO_PRESERVE, USER1_DOC1, USER1_NEW_DOC);
        checkAttributes(USER1_NEW_DOC, ADMIN_DB_USER, DBA_GROUP, USER1_DOC1_MODE, not(getCreated(USER1_DOC1)), not(getLastModified(USER1_DOC1)));
    }

    /**
     * As a DBA copy {@link #USER1_BIN_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER1_NEW_BIN_DOC}.
     */
    @Test
    public void copyBinaryToNonExistentAsDBA() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject adminUser = existWebServer.getBrokerPool().getSecurityManager().authenticate(ADMIN_DB_USER, ADMIN_DB_PWD);
        copyDoc(adminUser, NO_PRESERVE, USER1_BIN_DOC1, USER1_NEW_BIN_DOC);
        checkAttributes(USER1_NEW_BIN_DOC, ADMIN_DB_USER, DBA_GROUP, USER1_BIN_DOC1_MODE, not(getCreated(USER1_BIN_DOC1)), not(getLastModified(USER1_BIN_DOC1)));
    }

    /**
     * As a DBA copy {@link #USER1_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} already existing {@link #USER1_DOC2}.
     */
    @Test
    public void copyXmlToExistentAsDBA() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException, InterruptedException {
        final Subject adminUser = existWebServer.getBrokerPool().getSecurityManager().authenticate(ADMIN_DB_USER, ADMIN_DB_PWD);
        final long originalDoc2LastModified = getLastModified(USER1_DOC2);
        Thread.sleep(5);
        copyDoc(adminUser, NO_PRESERVE, USER1_DOC1, USER1_DOC2);
        checkAttributes(USER1_DOC2, USER1_NAME, USER1_NAME, USER1_DOC2_MODE, equalTo(getCreated(USER1_DOC2)), allOf(not(getLastModified(USER1_DOC1)), not(originalDoc2LastModified)));
    }

    /**
     * As a DBA copy {@link #USER1_BIN_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER1_NEW_BIN_DOC}.
     */
    @Test
    public void copyBinaryToExistentAsDBA() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException, InterruptedException {
        final Subject adminUser = existWebServer.getBrokerPool().getSecurityManager().authenticate(ADMIN_DB_USER, ADMIN_DB_PWD);
        final long originalBinDoc2LastModified = getLastModified(USER1_BIN_DOC2);
        Thread.sleep(5);
        copyDoc(adminUser, NO_PRESERVE, USER1_BIN_DOC1, USER1_BIN_DOC2);
        checkAttributes(USER1_BIN_DOC2, USER1_NAME, USER1_NAME, USER1_BIN_DOC2_MODE, equalTo(getCreated(USER1_BIN_DOC2)), allOf(not(getLastModified(USER1_BIN_DOC1)), not(originalBinDoc2LastModified)));
    }

    /**
     * As some other (non-owner) user copy {@link #USER1_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER2_NEW_DOC}.
     */
    @Test
    public void copyXmlToNonExistentAsOther() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        copyDoc(user2, NO_PRESERVE, USER1_DOC1, USER2_NEW_DOC);
        checkAttributes(USER2_NEW_DOC, USER2_NAME, USER2_NAME, USER1_DOC1_MODE, not(getCreated(USER1_DOC1)), not(getLastModified(USER1_DOC1)));
    }

    /**
     * As some other (non-owner) user copy {@link #USER1_BIN_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER2_NEW_BIN_DOC}.
     */
    @Test
    public void copyBinaryToNonExistentAsOther() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        copyDoc(user2, NO_PRESERVE, USER1_BIN_DOC1, USER2_NEW_BIN_DOC);
        checkAttributes(USER2_NEW_BIN_DOC, USER2_NAME, USER2_NAME, USER1_BIN_DOC1_MODE, not(getCreated(USER1_BIN_DOC1)), not(getLastModified(USER1_BIN_DOC1)));
    }

    /**
     * As some other (non-owner) user copy {@link #USER1_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} already existing {@link #USER2_DOC2}.
     */
    @Test
    public void copyXmlToExistentAsOther() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException, InterruptedException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        final long originalDoc2LastModified = getLastModified(USER2_DOC2);
        Thread.sleep(5);
        copyDoc(user2, NO_PRESERVE, USER1_DOC1, USER2_DOC2);
        checkAttributes(USER2_DOC2, USER2_NAME, USER2_NAME, USER2_DOC2_MODE, equalTo(getCreated(USER2_DOC2)), allOf(not(getLastModified(USER1_DOC1)), not(originalDoc2LastModified)));
    }

    /**
     * As owner user copy {@link #USER1_DOC3} from {@link TestConstants#TEST_COLLECTION_URI} to already existing {@link #USER2_DOC3} owned by someone else.
     */
    @Test
    public void copyXmlToExistentAsOwner() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException, InterruptedException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        final long originalDoc3LastModified = getLastModified(USER2_DOC3);
        Thread.sleep(5);
        copyDoc(user1, NO_PRESERVE, USER1_DOC3, USER2_DOC3);
        checkAttributes(USER2_DOC3, USER2_NAME, GROUP1_NAME, USER2_DOC3_MODE, equalTo(getCreated(USER2_DOC3)), allOf(not(getLastModified(USER1_DOC3)), not(originalDoc3LastModified)));
    }

    /**
     * As owner user copy {@link #USER1_BIN_DOC3} from {@link TestConstants#TEST_COLLECTION_URI} to already existing {@link #USER2_BIN_DOC3} owned by someone else.
     */
    @Test
    public void copyBinaryToExistentAsOwner() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException, InterruptedException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        final long originalDoc3LastModified = getLastModified(USER2_DOC3);
        Thread.sleep(5);
        copyDoc(user1, NO_PRESERVE, USER1_BIN_DOC3, USER2_BIN_DOC3);
        checkAttributes(USER2_BIN_DOC3, USER2_NAME, GROUP1_NAME, USER2_BIN_DOC3_MODE, equalTo(getCreated(USER2_BIN_DOC3)), allOf(not(getLastModified(USER1_BIN_DOC3)), not(originalDoc3LastModified)));
    }

    /**
     * As some other (non-owner) user copy {@link #USER1_BIN_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} already existing {@link #USER2_BIN_DOC2}.
     */
    @Test
    public void copyBinaryToExistentAsOther() throws AuthenticationException, EXistException, PermissionDeniedException, LockException, IOException, TriggerException, InterruptedException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        final long originalBinDoc2LastModified = getLastModified(USER2_BIN_DOC2);
        Thread.sleep(5);
        copyDoc(user2, NO_PRESERVE, USER1_BIN_DOC1, USER2_BIN_DOC2);
        checkAttributes(USER2_BIN_DOC2, USER2_NAME, USER2_NAME, USER2_BIN_DOC2_MODE, equalTo(getCreated(USER2_BIN_DOC2)), allOf(not(getLastModified(USER1_BIN_DOC1)), not(originalBinDoc2LastModified)));
    }

    /**
     * Whilst preserving attributes,
     * as the owner copy {@link #USER1_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER1_NEW_DOC}.
     */
    @Test
    public void copyPreserveXmlToNonExistentAsSelf() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        final long doc1LastModified = getLastModified(USER1_DOC1);
        copyDoc(user1, PRESERVE, USER1_DOC1, USER1_NEW_DOC);
        checkAttributes(USER1_NEW_DOC, USER1_NAME, USER1_NAME, USER1_DOC1_MODE, equalTo(doc1LastModified), equalTo(doc1LastModified));
    }

    /**
     * Whilst preserving attributes,
     * as the owner copy {@link #USER1_BIN_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER1_NEW_BIN_DOC}.
     */
    @Test
    public void copyPreserveBinaryToNonExistentAsSelf() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        final long binDoc1LastModified = getLastModified(USER1_BIN_DOC1);
        copyDoc(user1, PRESERVE, USER1_BIN_DOC1, USER1_NEW_BIN_DOC);
        checkAttributes(USER1_NEW_BIN_DOC, USER1_NAME, USER1_NAME, USER1_BIN_DOC1_MODE, equalTo(binDoc1LastModified), equalTo(binDoc1LastModified));
    }

    /**
     * Whilst preserving attributes,
     * as the owner copy {@link #USER1_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} already existing {@link #USER1_DOC2}.
     */
    @Test
    public void copyPreserveXmlToExistentAsSelf() throws AuthenticationException, EXistException, PermissionDeniedException, LockException, IOException, TriggerException, InterruptedException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        final long originalDoc2Created = getCreated(USER1_DOC2);
        Thread.sleep(5);
        copyDoc(user1, PRESERVE, USER1_DOC1, USER1_DOC2);
        checkAttributes(USER1_DOC2, USER1_NAME, USER1_NAME, USER1_DOC1_MODE, equalTo(originalDoc2Created), equalTo(getLastModified(USER1_DOC1)));
    }

    /**
     * Whilst preserving attributes,
     * as the owner copy {@link #USER1_BIN_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} already existing {@link #USER1_BIN_DOC2}.
     */
    @Test
    public void copyPreserveBinaryToExistentAsSelf() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException, InterruptedException {
        final Subject user1 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        final long originalBinDoc2Created = getCreated(USER1_BIN_DOC2);
        Thread.sleep(5);
        copyDoc(user1, PRESERVE, USER1_BIN_DOC1, USER1_BIN_DOC2);
        checkAttributes(USER1_BIN_DOC2, USER1_NAME, USER1_NAME, USER1_BIN_DOC1_MODE, equalTo(originalBinDoc2Created), equalTo(getLastModified(USER1_BIN_DOC1)));
    }

    /**
     * Whilst preserving attributes,
     * as a DBA copy {@link #USER1_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER1_NEW_DOC}.
     */
    @Test
    public void copyPreserveXmlToNonExistentAsDBA() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject adminUser = existWebServer.getBrokerPool().getSecurityManager().authenticate(ADMIN_DB_USER, ADMIN_DB_PWD);
        final long doc1LastModified = getLastModified(USER1_DOC1);
        copyDoc(adminUser, PRESERVE, USER1_DOC1, USER1_NEW_DOC);
        checkAttributes(USER1_NEW_DOC, USER1_NAME, USER1_NAME, USER1_DOC1_MODE, equalTo(doc1LastModified), equalTo(doc1LastModified));
    }

    /**
     * Whilst preserving attributes,
     * as a DBA copy {@link #USER1_BIN_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER1_NEW_BIN_DOC}.
     */
    @Test
    public void copyPreserveBinaryToNonExistentAsDBA() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject adminUser = existWebServer.getBrokerPool().getSecurityManager().authenticate(ADMIN_DB_USER, ADMIN_DB_PWD);
        final long binDoc1LastModified = getLastModified(USER1_BIN_DOC1);
        copyDoc(adminUser, PRESERVE, USER1_BIN_DOC1, USER1_NEW_BIN_DOC);
        checkAttributes(USER1_NEW_BIN_DOC, USER1_NAME, USER1_NAME, USER1_BIN_DOC1_MODE, equalTo(binDoc1LastModified), equalTo(binDoc1LastModified));
    }

    /**
     * Whilst preserving attributes,
     * as a DBA copy {@link #USER1_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} already existing {@link #USER1_DOC2}.
     */
    @Test
    public void copyPreserveXmlToExistentAsDBA() throws AuthenticationException, EXistException, PermissionDeniedException, LockException, IOException, TriggerException, InterruptedException {
        final Subject adminUser = existWebServer.getBrokerPool().getSecurityManager().authenticate(ADMIN_DB_USER, ADMIN_DB_PWD);
        final long originalDoc2Created = getCreated(USER1_DOC2);
        Thread.sleep(5);
        copyDoc(adminUser, PRESERVE, USER1_DOC1, USER1_DOC2);
        checkAttributes(USER1_DOC2, USER1_NAME, USER1_NAME, USER1_DOC1_MODE, equalTo(originalDoc2Created), equalTo(getLastModified(USER1_DOC1)));
    }

    /**
     * Whilst preserving attributes,
     * as a DBA copy {@link #USER1_BIN_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} already existing {@link #USER1_BIN_DOC2}.
     */
    @Test
    public void copyPreserveBinaryToExistentAsDBA() throws AuthenticationException, EXistException, PermissionDeniedException, LockException, IOException, TriggerException, InterruptedException {
        final Subject adminUser = existWebServer.getBrokerPool().getSecurityManager().authenticate(ADMIN_DB_USER, ADMIN_DB_PWD);
        final long originalBinDoc2Created = getCreated(USER1_BIN_DOC2);
        Thread.sleep(5);
        copyDoc(adminUser, PRESERVE, USER1_BIN_DOC1, USER1_BIN_DOC2);
        checkAttributes(USER1_BIN_DOC2, USER1_NAME, USER1_NAME, USER1_BIN_DOC1_MODE, equalTo(originalBinDoc2Created), equalTo(getLastModified(USER1_BIN_DOC1)));
    }

    /**
     * Whilst preserving attributes,
     * as some other (non-owner) user copy {@link #USER1_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER2_NEW_DOC}.
     */
    @Test
    public void copyPreserveXmlToNonExistentAsOther() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        final long doc1LastModified = getLastModified(USER1_DOC1);
        copyDoc(user2, PRESERVE, USER1_DOC1, USER2_NEW_DOC);
        checkAttributes(USER2_NEW_DOC, USER2_NAME, USER2_NAME, USER1_DOC1_MODE, equalTo(doc1LastModified), equalTo(doc1LastModified));
    }

    /**
     * Whilst preserving attributes,
     * some other (non-owner) user copy {@link #USER1_BIN_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} to non-existent {@link #USER1_NEW_BIN_DOC}.
     */
    @Test
    public void copyPreserveBinaryToNonExistentAsOther() throws AuthenticationException, LockException, PermissionDeniedException, EXistException, IOException, TriggerException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        final long binDoc1LastModified = getLastModified(USER1_BIN_DOC1);
        copyDoc(user2, PRESERVE, USER1_BIN_DOC1, USER2_NEW_BIN_DOC);
        checkAttributes(USER2_NEW_BIN_DOC, USER2_NAME, USER2_NAME, USER1_BIN_DOC1_MODE, equalTo(binDoc1LastModified), equalTo(binDoc1LastModified));
    }

    /**
     * Whilst preserving attributes,
     * as some other (non-owner) user copy {@link #USER1_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} already existing {@link #USER2_DOC2}.
     */
    @Test
    public void copyPreserveXmlToExistentAsOther() throws AuthenticationException, EXistException, PermissionDeniedException, LockException, IOException, TriggerException, InterruptedException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        final long originalDoc2Created = getCreated(USER2_DOC2);
        Thread.sleep(5);
        copyDoc(user2, PRESERVE, USER1_DOC1, USER2_DOC2);
        checkAttributes(USER2_DOC2, USER2_NAME, USER2_NAME, USER1_DOC1_MODE, equalTo(originalDoc2Created), equalTo(getLastModified(USER1_DOC1)));
    }

    /**
     * Whilst preserving attributes,
     * as some other (non-owner) user copy {@link #USER1_BIN_DOC1} from {@link TestConstants#TEST_COLLECTION_URI} already existing {@link #USER2_BIN_DOC2}.
     */
    @Test
    public void copyPreserveBinaryToExistentAsOther() throws AuthenticationException, EXistException, PermissionDeniedException, LockException, IOException, TriggerException, InterruptedException {
        final Subject user2 = existWebServer.getBrokerPool().getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        final long originalBinDoc2Created = getCreated(USER2_BIN_DOC2);
        Thread.sleep(5);
        copyDoc(user2, PRESERVE, USER1_BIN_DOC1, USER2_BIN_DOC2);
        checkAttributes(USER2_BIN_DOC2, USER2_NAME, USER2_NAME, USER1_BIN_DOC1_MODE, equalTo(originalBinDoc2Created), equalTo(getLastModified(USER1_BIN_DOC1)));
    }

    private void copyDoc(final Subject execAsUser, final DBBroker.PreserveType preserve, final XmldbURI srcDocName, final XmldbURI destDocName) throws EXistException, PermissionDeniedException, LockException, IOException, TriggerException {
        final XmldbURI src = TEST_COLLECTION_URI.append(srcDocName);
        final XmldbURI dest = TEST_COLLECTION_URI.append(destDocName);

        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(execAsUser));
                final Txn transaction = pool.getTransactionManager().beginTransaction();
                final LockedDocument lockedSrcDoc = broker.getXMLResource(src, LockMode.READ_LOCK);
                final Collection destCol = broker.openCollection(dest.removeLastSegment(), LockMode.WRITE_LOCK)) {

            broker.copyResource(transaction, lockedSrcDoc.getDocument(), destCol, dest.lastSegment(), preserve);

            transaction.commit();
        }

        // check the copy of the document is the same as the original
        try (final DBBroker broker = pool.get(Optional.of(execAsUser));
                final LockedDocument lockedOriginal = broker.getXMLResource(src, LockMode.READ_LOCK);
                final LockedDocument lockedCopy = broker.getXMLResource(dest, LockMode.READ_LOCK)) {


            final Diff diff = DiffBuilder
                    .compare(Input.fromDocument(lockedOriginal.getDocument()))
                    .withTest(Input.fromDocument(lockedCopy.getDocument()))
                    .build();

            assertFalse(diff.toString(), diff.hasDifferences());
        }
    }

    private long getCreated(final XmldbURI docName) throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final LockedDocument lockedDoc = broker.getXMLResource(TEST_COLLECTION_URI.append(docName), LockMode.READ_LOCK)) {

            return lockedDoc.getDocument().getMetadata().getCreated();
        }
    }

    private long getLastModified(final XmldbURI docName) throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final LockedDocument lockedDoc = broker.getXMLResource(TEST_COLLECTION_URI.append(docName), LockMode.READ_LOCK)) {

            return lockedDoc.getDocument().getMetadata().getLastModified();
        }
    }

    private void checkAttributes(final XmldbURI docName, final String expectedOwner, final String expectedGroup, final int expectedMode, final Matcher<Long> expectedCreated, final Matcher<Long> expectedLastModified) throws EXistException, PermissionDeniedException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
                final LockedDocument lockedDoc = broker.getXMLResource(TEST_COLLECTION_URI.append(docName), LockMode.READ_LOCK)) {

            final DocumentImpl doc = lockedDoc.getDocument();
            final Permission permission = doc.getPermissions();
            assertEquals("Owner value was not expected", expectedOwner, permission.getOwner().getName());
            assertEquals("Group value was not expected", expectedGroup, permission.getGroup().getName());
            assertEquals("Mode value was not expected", expectedMode, permission.getMode());

            assertThat("Created value is not correct", doc.getMetadata().getCreated(), expectedCreated);
            assertThat("LastModified value is not correct", doc.getMetadata().getLastModified(), expectedLastModified);
        }
    }

    @BeforeClass
    public static void prepareDb() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        final SecurityManager sm = pool.getSecurityManager();
        try (final DBBroker broker = pool.get(Optional.of(sm.getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {
            final Collection collection = broker.getOrCreateCollection(transaction, TEST_COLLECTION_URI);
            PermissionFactory.chmod(broker, collection, Optional.of(511), Optional.empty());
            broker.saveCollection(transaction, collection);

            createGroup(broker, sm, GROUP1_NAME);
            createUser(broker, sm, USER1_NAME, USER1_PWD, GROUP1_NAME);
            createUser(broker, sm, USER2_NAME, USER2_PWD, GROUP1_NAME);

            transaction.commit();
        }
    }

    @Before
    public void setup() throws EXistException, PermissionDeniedException, LockException, SAXException, IOException, AuthenticationException {
        final BrokerPool pool = existWebServer.getBrokerPool();

        // create user1 resources
        final Subject user1 = pool.getSecurityManager().authenticate(USER1_NAME, USER1_PWD);
        try (final DBBroker broker = pool.get(Optional.of(user1));
                final Txn transaction = pool.getTransactionManager().beginTransaction();
                final Collection collection = broker.openCollection(TEST_COLLECTION_URI, LockMode.WRITE_LOCK)) {

            final String u1d1xml = "<empty1/>";
            final IndexInfo u1d1ii = collection.validateXMLResource(transaction, broker, USER1_DOC1, u1d1xml);
            collection.store(transaction, broker, u1d1ii, u1d1xml);
            chmod(broker, transaction, TEST_COLLECTION_URI.append(USER1_DOC1), USER1_DOC1_MODE);

            final String u1d2xml = "<empty2/>";
            final IndexInfo u1d2ii = collection.validateXMLResource(transaction, broker, USER1_DOC2, u1d2xml);
            collection.store(transaction, broker, u1d2ii, u1d2xml);
            chmod(broker, transaction, TEST_COLLECTION_URI.append(USER1_DOC2), USER1_DOC2_MODE);

            final String u1d3xml = "<empty3/>";
            final IndexInfo u1d3ii = collection.validateXMLResource(transaction, broker, USER1_DOC3, u1d3xml);
            collection.store(transaction, broker, u1d3ii, u1d3xml);
            chmod(broker, transaction, TEST_COLLECTION_URI.append(USER1_DOC3), USER1_DOC3_MODE);
            chgrp(broker, transaction, TEST_COLLECTION_URI.append(USER1_DOC3), GROUP1_NAME);

            final String u1d1bin = "bin1";
            collection.addBinaryResource(transaction, broker, USER1_BIN_DOC1, u1d1bin.getBytes(UTF_8), "text/plain");
            chmod(broker, transaction, TEST_COLLECTION_URI.append(USER1_BIN_DOC1), USER1_BIN_DOC1_MODE);

            final String u1d2bin = "bin2";
            collection.addBinaryResource(transaction, broker, USER1_BIN_DOC2, u1d2bin.getBytes(UTF_8), "text/plain");
            chmod(broker, transaction, TEST_COLLECTION_URI.append(USER1_BIN_DOC2), USER1_BIN_DOC2_MODE);

            final String u1d3bin = "bin3";
            collection.addBinaryResource(transaction, broker, USER1_BIN_DOC3, u1d3bin.getBytes(UTF_8), "text/plain");
            chmod(broker, transaction, TEST_COLLECTION_URI.append(USER1_BIN_DOC3), USER1_BIN_DOC3_MODE);
            chgrp(broker, transaction, TEST_COLLECTION_URI.append(USER1_BIN_DOC3), GROUP1_NAME);

            transaction.commit();
        }

        // create user2 resources
        final Subject user2 = pool.getSecurityManager().authenticate(USER2_NAME, USER2_PWD);
        try (final DBBroker broker = pool.get(Optional.of(user2));
                final Txn transaction = pool.getTransactionManager().beginTransaction();
                final Collection collection = broker.openCollection(TEST_COLLECTION_URI, LockMode.WRITE_LOCK)) {

            final String u2d2xml = "<empty2/>";
            final IndexInfo u2d2ii = collection.validateXMLResource(transaction, broker, USER2_DOC2, u2d2xml);
            collection.store(transaction, broker, u2d2ii, u2d2xml);
            chmod(broker, transaction, TEST_COLLECTION_URI.append(USER2_DOC2), USER2_DOC2_MODE);

            final String u2d3xml = "<empty3/>";
            final IndexInfo u2d3ii = collection.validateXMLResource(transaction, broker, USER2_DOC3, u2d3xml);
            collection.store(transaction, broker, u2d3ii, u2d3xml);
            chmod(broker, transaction, TEST_COLLECTION_URI.append(USER2_DOC3), USER2_DOC3_MODE);
            chgrp(broker, transaction, TEST_COLLECTION_URI.append(USER2_DOC3), GROUP1_NAME);

            final String u2d2bin = "bin2";
            collection.addBinaryResource(transaction, broker, USER2_BIN_DOC2, u2d2bin.getBytes(UTF_8), "text/plain");
            chmod(broker, transaction, TEST_COLLECTION_URI.append(USER2_BIN_DOC2), USER2_BIN_DOC2_MODE);

            final String u2d3bin = "bin3";
            collection.addBinaryResource(transaction, broker, USER2_BIN_DOC3, u2d3bin.getBytes(UTF_8), "text/plain");
            chmod(broker, transaction, TEST_COLLECTION_URI.append(USER2_BIN_DOC3), USER2_BIN_DOC3_MODE);
            chgrp(broker, transaction, TEST_COLLECTION_URI.append(USER2_BIN_DOC3), GROUP1_NAME);

            transaction.commit();
        }
    }

    @After
    public void teardown() throws EXistException, LockException, TriggerException, PermissionDeniedException, IOException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        try (final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            removeDocument(broker, transaction, TEST_COLLECTION_URI.append(USER1_DOC1));
            removeDocument(broker, transaction, TEST_COLLECTION_URI.append(USER1_DOC2));
            removeDocument(broker, transaction, TEST_COLLECTION_URI.append(USER1_DOC3));
            removeDocument(broker, transaction, TEST_COLLECTION_URI.append(USER1_NEW_DOC));

            removeDocument(broker, transaction, TEST_COLLECTION_URI.append(USER1_BIN_DOC1));
            removeDocument(broker, transaction, TEST_COLLECTION_URI.append(USER1_BIN_DOC2));
            removeDocument(broker, transaction, TEST_COLLECTION_URI.append(USER1_BIN_DOC3));
            removeDocument(broker, transaction, TEST_COLLECTION_URI.append(USER1_NEW_BIN_DOC));

            removeDocument(broker, transaction, TEST_COLLECTION_URI.append(USER2_DOC2));
            removeDocument(broker, transaction, TEST_COLLECTION_URI.append(USER2_DOC3));
            removeDocument(broker, transaction, TEST_COLLECTION_URI.append(USER2_NEW_DOC));

            removeDocument(broker, transaction, TEST_COLLECTION_URI.append(USER2_BIN_DOC2));
            removeDocument(broker, transaction, TEST_COLLECTION_URI.append(USER2_BIN_DOC3));
            removeDocument(broker, transaction, TEST_COLLECTION_URI.append(USER2_NEW_BIN_DOC));

            transaction.commit();
        }
    }

    @AfterClass
    public static void cleanupDb() throws EXistException, PermissionDeniedException, IOException, TriggerException {
        final BrokerPool pool = existWebServer.getBrokerPool();
        final SecurityManager sm = pool.getSecurityManager();
        try (final DBBroker broker = pool.get(Optional.of(sm.getSystemSubject()));
             final Txn transaction = pool.getTransactionManager().beginTransaction()) {

            removeUser(sm, USER2_NAME);
            removeUser(sm, USER1_NAME);
            removeGroup(sm, GROUP1_NAME);

            removeCollection(broker, transaction, TEST_COLLECTION_URI);

            transaction.commit();
        }
    }

    private static void createUser(final DBBroker broker, final SecurityManager sm, final String username, final String password, final String... supplementalGroups) throws PermissionDeniedException, EXistException {
        Group userGroup = new GroupAider(username);
        sm.addGroup(broker, userGroup);
        final Account user = new UserAider(username);
        user.setPassword(password);
        user.setPrimaryGroup(userGroup);
        sm.addAccount(user);

        userGroup = sm.getGroup(username);
        userGroup.addManager(sm.getAccount(username));
        sm.updateGroup(userGroup);

        for (final String supplementalGroup : supplementalGroups) {
            userGroup = sm.getGroup(supplementalGroup);
            user.addGroup(userGroup);
        }
        sm.updateAccount(user);
    }

    private static void createGroup(final DBBroker broker, final SecurityManager sm, final String group) throws PermissionDeniedException, EXistException {
        Group userGroup = new GroupAider(group);
        sm.addGroup(broker, userGroup);
    }

    private static void chmod(final DBBroker broker, final Txn transaction, final XmldbURI pathUri, final int mode) throws PermissionDeniedException {
        PermissionFactory.chmod(broker, transaction, pathUri, Optional.of(mode), Optional.empty());
    }

    private static void chgrp(final DBBroker broker, final Txn transaction, final XmldbURI pathUri, final String group) throws PermissionDeniedException {
        PermissionFactory.chown(broker, transaction, pathUri, Optional.empty(), Optional.of(group));
    }

    private static void removeUser(final SecurityManager sm, final String username) throws PermissionDeniedException, EXistException {
        sm.deleteAccount(username);
        sm.deleteGroup(username);
    }

    private static void removeGroup(final SecurityManager sm, final String group) throws PermissionDeniedException, EXistException {
        sm.deleteGroup(group);
    }

    private static void removeDocument(final DBBroker broker, final Txn transaction, final XmldbURI documentUri) throws PermissionDeniedException, LockException, IOException, TriggerException {
        try (final Collection collection = broker.openCollection(documentUri.removeLastSegment(), LockMode.WRITE_LOCK)) {

            final DocumentImpl doc = collection.getDocument(broker, documentUri.lastSegment());
            if (doc != null) {
                collection.removeResource(transaction, broker, doc);
            }
        }
    }

    private static void removeCollection(final DBBroker broker, final Txn transaction, final XmldbURI collectionUri) throws PermissionDeniedException, IOException, TriggerException {
        try (final Collection collection = broker.openCollection(collectionUri, LockMode.WRITE_LOCK)) {

            if (collection != null) {
                broker.removeCollection(transaction, collection);
            }
        }
    }
}

