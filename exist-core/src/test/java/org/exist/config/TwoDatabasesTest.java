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
package org.exist.config;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.security.Subject;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.util.LockException;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.util.MimeType;
import org.exist.util.StringInputSource;
import org.exist.xmldb.XmldbURI;
import org.junit.*;
import org.xml.sax.SAXException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertTrue;

/**
 * @author alex
 */
public class TwoDatabasesTest {

    private static Path config1File;
    private static Path config2File;

    @BeforeClass
    public static void prepare() throws URISyntaxException {
        final String log4j = System.getProperty("log4j.configurationFile");
        if (log4j == null) {
            Path lf = Paths.get("log42j.xml");
            if (Files.isReadable(lf)) {
                System.setProperty("log4j.configurationFile", lf.toUri().toASCIIString());
            }
        }

        final ClassLoader loader = TwoDatabasesTest.class.getClassLoader();
        final char separator = System.getProperty("file.separator").charAt(0);
        final String packagePath = TwoDatabasesTest.class.getPackage().getName().replace('.', separator);

        config1File = Paths.get(loader.getResource(packagePath + separator + "conf1.xml").toURI());
        config2File = Paths.get(loader.getResource(packagePath + separator + "conf2.xml").toURI());
    }

    @Rule
    public final ExistEmbeddedServer existEmbeddedServer1 = new ExistEmbeddedServer("db1", config1File, null, true);

    @Rule
    public final ExistEmbeddedServer existEmbeddedServer2 = new ExistEmbeddedServer("db2", config2File, null, true);

    private Subject user1;
    private Subject user2;

    @Before
    public void setUp() throws Exception {
        final BrokerPool pool1 = existEmbeddedServer1.getBrokerPool();
        user1 = pool1.getSecurityManager().getSystemSubject();
        try(final DBBroker broker1 = pool1.get(Optional.of(user1))) {
            final Collection top1 = broker1.getCollection(XmldbURI.create("xmldb:exist:///"));
            assertTrue(top1 != null);
        }

        final BrokerPool pool2 = existEmbeddedServer2.getBrokerPool();
        user2 = pool2.getSecurityManager().getSystemSubject();
        try(final DBBroker broker2 = pool2.get(Optional.of(user2))) {
            final Collection top2 = broker2.getCollection(XmldbURI.create("xmldb:exist:///"));
            assertTrue(top2 != null);
        }
    }

    @Test
    public void putGet() throws LockException, SAXException, PermissionDeniedException, EXistException, IOException {
        put();
        get();
    }

    private void put() throws EXistException, LockException, SAXException, PermissionDeniedException, IOException {
        final BrokerPool pool1 = existEmbeddedServer1.getBrokerPool();
        try (final DBBroker broker1 = pool1.get(Optional.of(user1));
             final Txn transaction1 = pool1.getTransactionManager().beginTransaction()) {
            try(Collection top1 = storeBin(broker1, transaction1, "1")) {
                pool1.getTransactionManager().commit(transaction1);
            }
        }

        final BrokerPool pool2 = existEmbeddedServer2.getBrokerPool();
        try (final DBBroker broker2 = pool2.get(Optional.of(user1));
             final Txn transaction2 = pool2.getTransactionManager().beginTransaction()) {
            try(Collection top2 = storeBin(broker2, transaction2, "2")) {
                pool2.getTransactionManager().commit(transaction2);
            }
        }
    }

    private void get() throws EXistException, IOException, PermissionDeniedException, LockException {
        final BrokerPool pool1 = existEmbeddedServer1.getBrokerPool();
        try (final DBBroker broker1 = pool1.get(Optional.of(user1))) {
            assertTrue(getBin(broker1, "1"));
        }

        final BrokerPool pool2 = existEmbeddedServer2.getBrokerPool();
        try (final DBBroker broker2 = pool2.get(Optional.of(user2))) {
            assertTrue(getBin(broker2, "2"));
        }
    }

    private static final String bin = "ABCDEFG";

    private Collection storeBin(final DBBroker broker, final Txn txn, String suffix) throws PermissionDeniedException, LockException, SAXException, EXistException, IOException {
        String data = bin + suffix;
        Collection top = broker.openCollection(XmldbURI.create("xmldb:exist:///db"), LockMode.WRITE_LOCK);
        broker.storeDocument(txn, XmldbURI.create("bin"), new StringInputSource(data.getBytes(UTF_8)), MimeType.TEXT_TYPE, top);
        return top;
    }

    private boolean getBin(final DBBroker broker, final String suffix) throws PermissionDeniedException, IOException {
        BinaryDocument binDoc = null;
        try(final Collection top = broker.openCollection(XmldbURI.create("xmldb:exist:///db"), LockMode.READ_LOCK)) {
            binDoc = (BinaryDocument) top.getDocument(broker, XmldbURI.create("bin"));
            assertTrue(binDoc != null);
            try (final UnsynchronizedByteArrayOutputStream os = new UnsynchronizedByteArrayOutputStream((int)binDoc.getContentLength())) {
                broker.readBinaryResource(binDoc, os);
                String comp = os.size() > 0 ? new String(os.toByteArray()) : "";
                return comp.equals(bin + suffix);
            }
        }
    }
}
