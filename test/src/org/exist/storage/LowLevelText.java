package org.exist.storage;

import org.exist.EXistException;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.source.StringSource;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Currently, tests for the {@link org.exist.storage.XQueryPool}
 * with the {@link StringSource}. 
 */
public class LowLevelText {

	private static final String TEST_XQUERY_SOURCE = "/test";

	private static final String MY_TEST_INSTANCE = "exist";

    private BrokerPool brokerPool;
	private DBBroker broker;

	private XQueryPool pool;

	private StringSource stringSource;

	private CompiledXQuery preCompiledXQuery;

	@Before
	public void setUp() throws DatabaseConfigurationException, EXistException, XPathException, PermissionDeniedException, IOException {

		Configuration configuration = new Configuration();
		BrokerPool.configure(MY_TEST_INSTANCE, 1, 1, configuration);
		brokerPool = BrokerPool.getInstance(MY_TEST_INSTANCE);

		//BUG: need to be released!
		broker = brokerPool.get(brokerPool.getSecurityManager().getSystemSubject());
		pool = new XQueryPool(configuration);
		stringSource = new StringSource(TEST_XQUERY_SOURCE);

		XQuery xquery = broker.getXQueryService();
		XQueryContext context = new XQueryContext(broker.getBrokerPool(), AccessContext.TEST);
		preCompiledXQuery = xquery.compile(context, stringSource);
	}

	@After
	public void tearDown() {
        brokerPool.release(broker);
		BrokerPool.stopAll(false);
	}

	@Test
	public void borrowCompiledXQuery1() throws PermissionDeniedException {
		// put the preCompiledXQuery in cache - NOTE: returnCompiledXQuery() is not a good name
		pool.returnCompiledXQuery(stringSource, preCompiledXQuery);
		callAndTestBorrowCompiledXQuery(stringSource);
	}

	@Test
	public void borrowCompiledXQuery2() throws PermissionDeniedException {
		pool.returnCompiledXQuery(stringSource, preCompiledXQuery);

		callAndTestBorrowCompiledXQuery(stringSource);
		callAndTestBorrowCompiledXQuery(stringSource);
	}

	@Test
	public void borrowCompiledXQuery3() throws PermissionDeniedException {
		pool.returnCompiledXQuery(stringSource, preCompiledXQuery);

		callAndTestBorrowCompiledXQuery(stringSource);
		callAndTestBorrowCompiledXQuery(stringSource);
		callAndTestBorrowCompiledXQuery(stringSource);
	}

	/**
	 * test with a new StringSource object having same content
	 */
	@Test
	public void borrowCompiledXQueryNewStringSource() throws PermissionDeniedException {
		pool.returnCompiledXQuery(stringSource, preCompiledXQuery);
		StringSource localStringSource = new StringSource(TEST_XQUERY_SOURCE);

		callAndTestBorrowCompiledXQuery(localStringSource);
	}

	/**
	 * test with a new StringSource object having same content
	 */
	@Test
	public void borrowCompiledXQueryNewStringSource2() throws PermissionDeniedException {
		pool.returnCompiledXQuery(stringSource, preCompiledXQuery);
		StringSource localStringSource = new StringSource(TEST_XQUERY_SOURCE);

		callAndTestBorrowCompiledXQuery(localStringSource);
		callAndTestBorrowCompiledXQuery(localStringSource);
	}

	private void callAndTestBorrowCompiledXQuery(StringSource stringSourceArg) throws PermissionDeniedException {
		final CompiledXQuery compiledXQuery = pool.borrowCompiledXQuery(broker, stringSourceArg);
		assertNotNull(
				"borrowCompiledXQuery should retrieve something for this stringSource",
				compiledXQuery);
		assertEquals(
				"borrowCompiledXQuery should retrieve the preCompiled XQuery for this stringSource",
				compiledXQuery, preCompiledXQuery);
        pool.returnCompiledXQuery(stringSourceArg, compiledXQuery);
	}
}