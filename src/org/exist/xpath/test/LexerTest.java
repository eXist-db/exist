/*
 * LexerTest.java - Jul 22, 2003
 * 
 * @author wolf
 */
package org.exist.xpath.test;

import java.io.IOException;
import java.io.StringReader;

import org.exist.EXistException;
import org.exist.Parser;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeSet;
import org.exist.parser.XPathLexer;
import org.exist.parser.XPathParser;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.xpath.PathExpr;
import org.exist.xpath.Value;
import org.xml.sax.SAXException;

import antlr.RecognitionException;
import antlr.TokenStreamException;

import junit.framework.TestCase;

public class LexerTest extends TestCase {

	private boolean localDb = false;
	
	private static final String xml =
		"<text><body>" +
		"<p>\u660E &#x660E;</p>" +
		"<p>&#xC5F4; &#xB2E8;&#xACC4;</p>" +
		"<p>\u4ED6\u4E3A\u8FD9\u9879\u5DE5\u7A0B\u6295" +
		"\u5165\u4E86\u5341\u4E09\u5E74\u65F6\u95F4\u3002</p>" +
		"</body></text>";
		
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
		String home, file = "conf.xml";
		home = System.getProperty("exist.home");
		if (home == null)
			home = System.getProperty("user.dir");
		try {
			Configuration config = new Configuration(file, home);
			BrokerPool.configure(1, 5, config);
		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
		localDb = true;
	}

	public void testQuery() {
		//String query = "document(*)//p[. &= '\uB2E8\uACC4']";
		String query = "document(*)//p[. = '\u4ED6\u4E3A\u8FD9\u9879\u5DE5\u7A0B\u6295" +
			"\u5165\u4E86\u5341\u4E09\u5E74\u65F6\u95F4\u3002']";
			
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
		try {	
			// parse the xml source
			broker = pool.get();
			Parser parser = new Parser(broker, user, true);
			parser.parse(xml, "/db/test/test.xml");
			
			// parse the query into the internal syntax tree
			XPathLexer lexer = new XPathLexer(new StringReader(query));
			XPathParser xparser = new XPathParser(pool, user, lexer);
			PathExpr expr = new PathExpr(pool);
			xparser.expr(expr);
			
			// execute the query
			DocumentSet docs = expr.preselect();
			Value resultValue = expr.eval(docs, null, null);
			
			// check results
			NodeSet resultSet = (NodeSet)resultValue.getNodeList();
			System.out.println("----------------------------------");
			System.out.println("found: " + resultSet.getLength());
		} catch (RecognitionException e) {
			e.printStackTrace();
		} catch (TokenStreamException e) {
			e.printStackTrace();
		} catch (EXistException e) {
			e.printStackTrace();
		} catch (PermissionDeniedException e) {
			e.printStackTrace();
		} catch (SAXException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			pool.release(broker);
		}
		if(localDb)
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

	protected void setUp() throws Exception {
		if(!BrokerPool.isConfigured())
			configure();
	}

}
