/*
 * Created on Sep 25, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.exist.xmldb.test.concurrent;

import java.io.File;

/**
 * @author wolf
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class ConcurrentAttrUpdateTest extends ConcurrentTestBase {

	private final static String URI = "xmldb:exist:///db";
	
	private final static String QUERY =
		"//ELEMENT[@attribute-1]";
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(ConcurrentAttrUpdateTest.class);
	}
	
	private File tempFile;
	
	/**
	 * @param name
	 * @param uri
	 * @param testCollection
	 */
	public ConcurrentAttrUpdateTest(String name) {
		super(name, URI, "C1");
	}
	
	protected void setUp() throws Exception {
		super.setUp();
		
		String[] wordList = DBUtils.wordList(rootCol);
		tempFile = DBUtils.generateXMLFile(250, 10, wordList);
		DBUtils.addXMLResource(getTestCollection(), "R1.xml", tempFile);
		
		addAction(new AttributeUpdateAction(URI + "/C1", "R1.xml", wordList), 20, 0, 0);
//		addAction(new XQueryAction(URI + "/C1", "R1.xml", QUERY), 100, 100, 30);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.test.concurrent.ConcurrentTestBase#tearDown()
	 */
	protected void tearDown() throws Exception {
		super.tearDown();
		tempFile.delete();
	}

}
