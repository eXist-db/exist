/*
 * LexerTest.java - Jul 22, 2003
 * 
 * @author wolf
 */
package org.exist.xquery;

import java.io.IOException;
import java.io.StringReader;
import java.util.Optional;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.TestConstants;
import org.exist.util.Configuration;
import org.exist.util.DatabaseConfigurationException;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.Sequence;

import antlr.collections.AST;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.xml.sax.SAXException;

public class LexerTest {

	private static final String xml =
		"<text><body>"
			+ "<p>\u660E &#x660E;</p>"
			+ "<p>&#xC5F4; &#xB2E8;&#xACC4;</p>"
			+ "<p>\u4ED6\u4E3A\u8FD9\u9879\u5DE5\u7A0B\u6295"
			+ "\u5165\u4E86\u5341\u4E09\u5E74\u65F6\u95F4\u3002</p>"
			+ "</body></text>";

	/**
	 * Start a local database instance.
	 */
	private void configure() throws DatabaseConfigurationException, EXistException {
		Configuration config = new Configuration();
		BrokerPool.configure(1, 5, config);
	}

	@Test
	public void query() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException, RecognitionException, XPathException, TokenStreamException {
		//String query = "xmldb:document()//p[. &= '\uB2E8\uACC4']";
		String query =
			"xmldb:document()//p[. = '\u4ED6\u4E3A\u8FD9\u9879\u5DE5\u7A0B\u6295"
				+ "\u5165\u4E86\u5341\u4E09\u5E74\u65F6\u95F4\u3002']";

		// get a BrokerPool for access to the database engine
		BrokerPool pool = BrokerPool.getInstance();


		final TransactionManager transact = pool.getTransactionManager();
		try(final DBBroker broker = pool.get(Optional.of(pool.getSecurityManager().getSystemSubject()))) {

			try(final Txn transaction = transact.beginTransaction()) {
				// parse the xml source

	            Collection collection = broker.getOrCreateCollection(transaction, TestConstants.TEST_COLLECTION_URI);
	            broker.saveCollection(transaction, collection);
	
	            IndexInfo info = collection.validateXMLResource(transaction, broker, XmldbURI.create("test.xml"), xml);
	            //TODO : unlock the collection here ?
	            collection.store(transaction, broker, info, xml, false);
	            transact.commit(transaction);
			}

            // parse the query into the internal syntax tree
            XQueryContext context = new XQueryContext(broker.getBrokerPool(), AccessContext.TEST);
            XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
            XQueryParser xparser = new XQueryParser(lexer);
            XQueryTreeParser treeParser = new XQueryTreeParser(context);
            xparser.xpath();
            if (xparser.foundErrors()) {
                System.err.println(xparser.getErrorMessage());
                return;
            }

            AST ast = xparser.getAST();


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
            int count = result.getItemCount();
		}
	}

    @Before
	public void setUp() throws EXistException, DatabaseConfigurationException {
		if (!BrokerPool.isConfigured(BrokerPool.DEFAULT_INSTANCE_NAME)) {
            configure();
        }
	}

    @After
    public void tearDown() throws Exception {
        BrokerPool.stopAll(false);
    }
}
