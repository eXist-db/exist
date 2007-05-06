/*
 * LexerTest.java - Jul 22, 2003
 * 
 * @author wolf
 */
package org.exist.xquery;

import java.io.StringReader;

import junit.framework.TestCase;

import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.security.User;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.Sequence;

import antlr.collections.AST;

public class LexerTest extends TestCase {

	private boolean localDb = false;

	private static final String xml =
		"<text><body>"
			+ "<p>\u660E &#x660E;</p>"
			+ "<p>&#xC5F4; &#xB2E8;&#xACC4;</p>"
			+ "<p>\u4ED6\u4E3A\u8FD9\u9879\u5DE5\u7A0B\u6295"
			+ "\u5165\u4E86\u5341\u4E09\u5E74\u65F6\u95F4\u3002</p>"
			+ "</body></text>";

	/**
	 * Constructor for LexerTest.
	 * @param arg0
	 */
	public LexerTest(String arg0) {
		super(arg0);
	}

	/**
	 * Start a local database instance.
	 */
	private void configure() {
		try {
			Configuration config = new Configuration();
			BrokerPool.configure(1, 5, config);
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		localDb = true;
	}

	public void testQuery() {
		//String query = "document()//p[. &= '\uB2E8\uACC4']";
		String query =
			"document()//p[. = '\u4ED6\u4E3A\u8FD9\u9879\u5DE5\u7A0B\u6295"
				+ "\u5165\u4E86\u5341\u4E09\u5E74\u65F6\u95F4\u3002']";

		// get a BrokerPool for access to the database engine
		BrokerPool pool = null;
		try {
			pool = BrokerPool.getInstance();
		} catch (EXistException e1) {
			e1.printStackTrace();
			fail(e1.getMessage());
		}
		User user = pool.getSecurityManager().getUser("admin");
		DBBroker broker = null;
		TransactionManager transact = null;
		Txn transaction = null;
		try {
			// parse the xml source
			broker = pool.get(user);
			transact = pool.getTransactionManager();
            assertNotNull(transact);
            transaction = transact.beginTransaction();
            assertNotNull(transaction);
            Collection collection = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
            broker.saveCollection(transaction, collection);

            IndexInfo info = collection.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), xml);
            //TODO : unlock the collection here ?
            collection.store(transaction, broker, info, xml, false);
            transact.commit(transaction);
		} catch (Exception e) {
			transact.abort(transaction);
            e.printStackTrace();
            fail(e.getMessage());
		}

		try {
            // parse the query into the internal syntax tree
			XQueryContext context = new XQueryContext(broker, AccessContext.TEST);
			XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
			XQueryParser xparser = new XQueryParser(lexer);
			XQueryTreeParser treeParser = new XQueryTreeParser(context);
			xparser.xpath();
			if (xparser.foundErrors()) {
				System.err.println(xparser.getErrorMessage());
				return;
			}

			AST ast = xparser.getAST();
			System.out.println("generated AST: " + ast.toStringTree());

			PathExpr expr = new PathExpr(context);
			treeParser.xpath(ast, expr);
			if (treeParser.foundErrors()) {
				System.err.println(treeParser.getErrorMessage());
				return;
			}
			expr.analyze(new AnalyzeContextInfo());
			// execute the query
			Sequence result = expr.eval(null, null);

			// check results
			System.out.println("----------------------------------");
			System.out.println("found: " + result.getItemCount());
		} catch (Exception e) {
            e.printStackTrace();
            fail(e.getMessage());
		} finally {
			pool.release(broker);
		}
		if (localDb)
			try {
				BrokerPool.stop();
			} catch (EXistException e2) {
				e2.printStackTrace();
			}
	}

	public static void main(String[] args) {
		junit.textui.TestRunner.run(LexerTest.class);
		//junit.swingui.TestRunner.run(LexerTest.class);
	}

	protected void setUp() {
		if (!BrokerPool.isConfigured())
			configure();
	}

    protected void tearDown() throws Exception {
        BrokerPool.stopAll(false);
    }
}
