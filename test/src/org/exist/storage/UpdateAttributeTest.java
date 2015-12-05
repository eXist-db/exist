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
import org.exist.collections.IndexInfo;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xquery.XPathException;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.junit.Test;
import static org.junit.Assert.assertNotNull;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;

public class UpdateAttributeTest extends AbstractUpdateTest {
    
    @Test
    public void update() throws EXistException, DatabaseConfigurationException, LockException, SAXException, PermissionDeniedException, IOException, ParserConfigurationException, XPathException {
        BrokerPool.FORCE_CORRUPTION = true;
        final BrokerPool pool = startDB();
        final TransactionManager mgr = pool.getTransactionManager();

        try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {
            final IndexInfo info = init(broker, mgr);
            assertNotNull(info);
            final MutableDocumentSet docs = new DefaultDocumentSet();
            docs.add(info.getDocument());
            final XUpdateProcessor proc = new XUpdateProcessor(broker, docs, AccessContext.TEST);
            assertNotNull(proc);
            
            try(final Txn transaction = mgr.beginTransaction()) {

                // append some new element to records
                for (int i = 1; i <= 200; i++) {
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

                mgr.commit(transaction);
            }

            // the following transaction will not be committed and thus undone during recovery
            final Txn transaction = mgr.beginTransaction();
            assertNotNull(transaction);

            // update attributes
            for (int i = 1; i <= 200; i++) {
                final String xupdate =
                    "<xu:modifications version=\"1.0\" xmlns:xu=\"http://www.xmldb.org/xupdate\">" +
                    "   <xu:update select=\"/products/product[" + i + "]/@id\">" + i + "u</xu:update>" +
                    "</xu:modifications>";
                proc.setBroker(broker);
                proc.setDocumentSet(docs);
                final Modification modifications[] = proc.parse(new InputSource(new StringReader(xupdate)));
                assertNotNull(modifications);
                modifications[0].process(transaction);
                proc.reset();
            }
           
//DO NOT COMMIT TRANSACTION
            pool.getTransactionManager().getJournal().flushToLog(true);
        }
    }
}
