/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2012 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.xquery.xUnit;

import static org.junit.Assert.fail;

import java.util.Iterator;
import java.util.Optional;

import org.exist.Database;
import org.exist.dom.persistent.NodeSet;
import org.exist.source.ClassLoaderSource;
import org.exist.source.Source;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.Sequence;
import org.junit.After;
import org.junit.Before;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class xUnit {
	
	Collection rootCollection;

    public void test(String source) {
        try {
            final Database db = BrokerPool.getInstance();

            try(final DBBroker broker = db.get(Optional.of(db.getSecurityManager().getGuestSubject()))) {

                XQuery xquery = broker.getBrokerPool().getXQueryService();

                XQueryContext context = new XQueryContext(broker.getBrokerPool());
                //context.setModuleLoadPath();

                Source query = new ClassLoaderSource(source);

                CompiledXQuery compiledQuery = xquery.compile(broker, context, query);

                for (Iterator<UserDefinedFunction> i = context.localFunctions(); i.hasNext(); ) {
                    UserDefinedFunction func = i.next();
                    FunctionSignature sig = func.getSignature();

                    for (Annotation ann : sig.getAnnotations()) {
                        if ("http://exist-db.org/xquery/xUnit".equals(ann.getName().getNamespaceURI())) {
    //						System.out.println(ann.getName().getLocalPart());

                            FunctionCall call = new FunctionCall(context, func);

                            final Sequence contextSequence;
                            final ContextItemDeclaration cid = context.getContextItemDeclartion();
                            if(cid != null) {
                                contextSequence = cid.eval(null);
                            } else {
                                contextSequence = Sequence.EMPTY_SEQUENCE;
                            }

                            call.eval(contextSequence);
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
        }
    }

    @Before
    public void setUpBefore() throws Exception {
        // initialize driver
        Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
        org.xmldb.api.base.Database database = (org.xmldb.api.base.Database) cl.newInstance();
        database.setProperty("create-database", "true");
        DatabaseManager.registerDatabase(database);

        rootCollection = 
        		DatabaseManager.getCollection(
        				"xmldb:exist://" + XmldbURI.ROOT_COLLECTION, "admin", "");
    }

    @After
    public void tearDownAfter() {
        if (rootCollection != null) {
            try {
                DatabaseInstanceManager dim =
                        (DatabaseInstanceManager) rootCollection.getService(
                        "DatabaseInstanceManager", "1.0");
                dim.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
                fail(e.getMessage());
            }
        }
        rootCollection = null;
    }

	public static void main(String[] args) throws Exception {
		xUnit tester = new xUnit();
		tester.setUpBefore();
		
		try {
			tester.test("resource:org/exist/xquery/xUnit/test.xql");
			
		} finally {
			tester.tearDownAfter();
		}
	}

}
