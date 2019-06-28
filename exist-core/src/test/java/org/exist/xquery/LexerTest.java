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
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.test.ExistEmbeddedServer;
import org.exist.test.TestConstants;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.parser.XQueryLexer;
import org.exist.xquery.parser.XQueryParser;
import org.exist.xquery.parser.XQueryTreeParser;
import org.exist.xquery.value.Sequence;

import antlr.collections.AST;
import org.junit.ClassRule;
import org.junit.Test;
import org.xml.sax.SAXException;

import static org.junit.Assert.fail;

public class LexerTest {

	private static final String xml =
		"<text><body>"
			+ "\u660E &#x660E;"
			+ "&#xC5F4; &#xB2E8;&#xACC4;"
			+ "\u4ED6\u4E3A\u8FD9\u9879\u5DE5\u7A0B\u6295"
			+ "\u5165\u4E86\u5341\u4E09\u5E74\u65F6\u95F4\u3002"
			+ "</body></text>";

	@ClassRule
	public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

	@Test
	public void query() throws EXistException, PermissionDeniedException, IOException, SAXException, LockException, RecognitionException, XPathException, TokenStreamException {
		String query =
			"//p[. = '\u4ED6\u4E3A\u8FD9\u9879\u5DE5\u7A0B\u6295"
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
	            collection.store(transaction, broker, info, xml);
	            transact.commit(transaction);
			}

            // parse the query into the internal syntax tree
            XQueryContext context = new XQueryContext(broker.getBrokerPool());
            XQueryLexer lexer = new XQueryLexer(context, new StringReader(query));
            XQueryParser xparser = new XQueryParser(lexer);
            XQueryTreeParser treeParser = new XQueryTreeParser(context);
            xparser.xpath();
            if (xparser.foundErrors()) {
                fail(xparser.getErrorMessage());
                return;
            }

            AST ast = xparser.getAST();


            PathExpr expr = new PathExpr(context);
            treeParser.xpath(ast, expr);
            if (treeParser.foundErrors()) {
               fail(treeParser.getErrorMessage());
                return;
            }
            expr.analyze(new AnalyzeContextInfo());
            // execute the query
            Sequence result = expr.eval(null, null);

            // check results
            int count = result.getItemCount();
		}
	}
}
