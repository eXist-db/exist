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
import org.exist.dom.persistent.DocumentImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.Txn;
import org.exist.util.FileInputSource;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.SAXException;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.transform.Source;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class RecoverXmlTest extends AbstractRecoverTest {

    @Override
    protected Path getTestFile1() throws IOException {
        return resolveTestFile("conf.xml");
    }

    @Override
    protected Path getTestFile2() throws IOException {
        return resolveTestFile("log4j2.xml");
    }

    @Override
    protected void storeAndVerify(final DBBroker broker, final Txn transaction, final Collection collection,
            final Path file, final String dbFilename) throws EXistException, PermissionDeniedException,
            IOException, LockException {
        final XmldbURI docUri = XmldbURI.create(dbFilename);
        try (final FileInputSource inputSource = new FileInputSource(file)) {
            final IndexInfo indexInfo =
                    collection.validateXMLResource(transaction, broker, docUri, inputSource);

            collection.store(transaction, broker, indexInfo, inputSource);

        } catch (final SAXException e) {
            throw new IOException(e);
        }


        final DocumentImpl doc = broker.getResource(collection.getURI().append(docUri), Permission.READ);
        assertNotNull(doc);

        final Source expected = Input.fromFile(file.toFile()).build();
        final Source actual = Input.fromDocument(doc).build();

        final Diff diff = DiffBuilder.compare(expected).withTest(actual)
                .checkForIdentical()
                .build();

        assertFalse("XML identical: " + diff.toString(), diff.hasDifferences());
    }

    @Override
    protected void readAndVerify(final DBBroker broker, final DocumentImpl doc, final Path file,
            final String dbFilename) {

        final Source expected = Input.fromFile(file.toFile()).build();
        final Source actual = Input.fromDocument(doc).build();

        final Diff diff = DiffBuilder.compare(expected).withTest(actual)
                .checkForIdentical()
                .build();

        assertFalse("XML identical: " + diff.toString(), diff.hasDifferences());
    }
}
