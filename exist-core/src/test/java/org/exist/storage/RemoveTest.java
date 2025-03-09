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
package org.exist.storage;

import org.exist.EXistException;
import org.exist.dom.persistent.LockedDocument;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.XPathException;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.StringReader;

public class RemoveTest extends AbstractUpdateTest {

    @Override
    protected void doUpdate(final DBBroker broker, final TransactionManager transact, final MutableDocumentSet docs)
            throws ParserConfigurationException, IOException, SAXException, LockException, XPathException,
                PermissionDeniedException, EXistException {

        final XUpdateProcessor proc = new XUpdateProcessor(broker, docs);
        assertNotNull(proc);

        try(final Txn transaction = transact.beginTransaction()) {

            // append some new element to records
            for (int i = 1; i <= 50; i++) {
                final String xupdate =
                        "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                                "   <xu:append select=\"/products\">" +
                                "       <product>" +
                                "           <xu:attribute name=\"id\"><xu:value-of select=\"count(/products/product) + 1\"/></xu:attribute>" +
                                "           <description>Product " + i + "</description>" +
                                "           <price>" + (i * 2.5) + "</price>" +
                                "           <stock>" + (i * 10) + "</stock>" +
                                "       </product>" +
                                "   </xu:append>" +
                                "</xu:modifications>";
                proc.setBroker(broker);
                proc.setDocumentSet(docs);
                final Modification modifications[] = proc.parse(new InputSource(new StringReader(xupdate)));
                assertNotNull(modifications);
                modifications[0].process(transaction);
                proc.reset();
            }

            transact.commit(transaction);
        }
            
        final Serializer serializer = broker.borrowSerializer();

        try(final LockedDocument lockedDoc = broker.getXMLResource(TestConstants.TEST_COLLECTION_URI2.append(TestConstants.TEST_XML_URI), LockMode.READ_LOCK)) {
            assertNotNull("Document '" + XmldbURI.ROOT_COLLECTION + "/test/test2/test.xml' should not be null", lockedDoc);
            final String data = serializer.serialize(lockedDoc.getDocument());
        } finally {
            broker.returnSerializer(serializer);
        }

        // the following transaction will not be committed and thus undone during recovery
        final Txn transaction = transact.beginTransaction();

        // remove elements
        for (int i = 1; i <= 25; i++) {
            final String xupdate =
                "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                "   <xu:remove select=\"/products/product[last()]\"/>" +
                "</xu:modifications>";
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            final Modification modifications[] = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();
        }

        //DO NOT COMMIT TRANSACTION
    }
}
