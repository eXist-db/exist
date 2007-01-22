package org.exist.storage;

import junit.framework.TestCase;

import org.exist.security.xacml.AccessContext;
import org.exist.security.XMLSecurityManager;
import org.exist.source.StringSource;
import org.exist.util.Configuration;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;

/** Currently, tests for the {@link org.exist.storage.XQueryPool}
 * with the {@link StringSource}. 
 * 
 * $Id$ */
public class LowLevelText extends TestCase {

	private static final String TEST_XQUERY_SOURCE = "/test";

	private static final String MY_TEST_INSTANCE = "my test instance";

	public static void main(String[] args) {
		junit.textui.TestRunner.run(LowLevelText.class);
	}

	private DBBroker broker;

	private XQueryPool pool;

	private StringSource stringSource;

	private CompiledXQuery preCompiledXQuery;

	protected void setUp() throws Exception {

		Configuration configuration = new Configuration();
		BrokerPool.configure(MY_TEST_INSTANCE, 1, 1, configuration);
		BrokerPool brokerPool = BrokerPool.getInstance(MY_TEST_INSTANCE);

		broker = brokerPool.get(XMLSecurityManager.SYSTEM_USER);
		pool = new XQueryPool(configuration);
		stringSource = new StringSource(TEST_XQUERY_SOURCE);

		XQuery xquery = broker.getXQueryService();
		XQueryContext context = new XQueryContext(broker, AccessContext.TEST);
		preCompiledXQuery = xquery.compile(context, stringSource);
		System.out.println("pre-compiled XQuery: " + preCompiledXQuery);
	}

//	protected void tearDown() {
//		try {
//			BrokerPool.stopAll(false);
//		} catch (Exception e) {
//			fail(e.getMessage());
//		}
//	}

	/**
	 * Test method for 'org.exist.storage.XQueryPool.borrowCompiledXQuery(DBBroker, Source)'
	 */
	public void testBorrowCompiledXQuery1() {
		// put the preCompiledXQuery in cache - NOTE: returnCompiledXQuery() is not a good name
		pool.returnCompiledXQuery(stringSource, preCompiledXQuery);
		callAndTestBorrowCompiledXQuery(stringSource);
	}

	public void testBorrowCompiledXQuery2() {
		pool.returnCompiledXQuery(stringSource, preCompiledXQuery);

		callAndTestBorrowCompiledXQuery(stringSource);
		callAndTestBorrowCompiledXQuery(stringSource);
	}

	public void testBorrowCompiledXQuery3() {
		pool.returnCompiledXQuery(stringSource, preCompiledXQuery);

		callAndTestBorrowCompiledXQuery(stringSource);
		callAndTestBorrowCompiledXQuery(stringSource);
		callAndTestBorrowCompiledXQuery(stringSource);
	}

	/** test with a new StringSource object having same content */
	public void testBorrowCompiledXQueryNewStringSource() {
		pool.returnCompiledXQuery(stringSource, preCompiledXQuery);
		StringSource localStringSource = new StringSource(TEST_XQUERY_SOURCE);

		callAndTestBorrowCompiledXQuery(localStringSource);
	}

	/** test with a new StringSource object having same content */
	public void testBorrowCompiledXQueryNewStringSource2() {
		pool.returnCompiledXQuery(stringSource, preCompiledXQuery);
		StringSource localStringSource = new StringSource(TEST_XQUERY_SOURCE);

		callAndTestBorrowCompiledXQuery(localStringSource);
		callAndTestBorrowCompiledXQuery(localStringSource);
	}
	
	private void callAndTestBorrowCompiledXQuery(StringSource stringSourceArg) {
		CompiledXQuery compiledXQuery = pool.borrowCompiledXQuery(broker,
				stringSourceArg);
		System.out.println("compiledXQuery: " + compiledXQuery);
		assertNotNull(
				"borrowCompiledXQuery should retrieve something for this stringSource",
				compiledXQuery);
		assertEquals(
				"borrowCompiledXQuery should retrieve the preCompiled XQuery for this stringSource",
				compiledXQuery, preCompiledXQuery);
	}

}
/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001,  Wolfgang M. Meier (meier@ifs.tu-darmstadt.de) and the team.
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
 *  You should have received a copy of the GNU Library General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA. 
 */