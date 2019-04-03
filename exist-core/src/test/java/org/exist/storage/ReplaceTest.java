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
package org.exist.storage;

import org.exist.EXistException;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xquery.XPathException;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;

import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.io.StringReader;

public class ReplaceTest extends AbstractUpdateTest {

    @Override
    protected void doUpdate(final DBBroker broker, final TransactionManager transact, final MutableDocumentSet docs)
            throws ParserConfigurationException, IOException, SAXException, LockException, XPathException,
                PermissionDeniedException, EXistException {

        final XUpdateProcessor proc = new XUpdateProcessor(broker, docs);
        assertNotNull(proc);

        try(final Txn transaction = transact.beginTransaction()) {

            String xupdate =
                    "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                            "   <xu:append select=\"/products\">" +
                            "       <product id=\"1\">" +
                            "           <description>Product 1</description>" +
                            "           <price>24.30</price>" +
                            "           <stock>10</stock>" +
                            "       </product>" +
                            "   </xu:append>" +
                            "</xu:modifications>";
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            Modification modifications[] = proc.parse(new InputSource(new StringReader(xupdate)));
            assertNotNull(modifications);
            modifications[0].process(transaction);
            proc.reset();

            // append some new element to records
            for (int i = 1; i <= 200; i++) {
                xupdate =
                        "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                                "   <xu:insert-before select=\"/products/product[1]\">" +
                                "       <product>" +
                                "           <xu:attribute name=\"id\"><xu:value-of select=\"count(/products/product) + 1\"/></xu:attribute>" +
                                "           <description>Product " + i + "</description>" +
                                "           <price>" + (i * 2.5) + "</price>" +
                                "           <stock>" + (i * 10) + "</stock>" +
                                "       </product>" +
                                "   </xu:insert-before>" +
                                "</xu:modifications>";
                proc.setBroker(broker);
                proc.setDocumentSet(docs);
                modifications = proc.parse(new InputSource(new StringReader(xupdate)));
                assertNotNull(modifications);
                modifications[0].process(transaction);
                proc.reset();
            }

            transact.commit(transaction);
        }

        // the following transaction will not be committed and thus undone during recovery
        final Txn transaction = transact.beginTransaction();
        // replace elements
        for (int i = 1; i <= 100; i++) {
            final String xupdate =
                "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                "   <xu:replace select=\"/products/product[" + i + "]\">" +
                "     <product id=\"" + i + "\">" +
                "         <description>Replaced product</description>" +
                "         <price>" + (i * 0.75) + "</price>" +
                "     </product>" +
                " </xu:replace>" +
                "</xu:modifications>";
            proc.setBroker(broker);
            proc.setDocumentSet(docs);
            final Modification modifications[] = proc.parse(new InputSource(new StringReader(xupdate)));
            modifications[0].process(transaction);
            proc.reset();
        }
            
        //DO NOT COMMIT TRANSACTION
    }
}
