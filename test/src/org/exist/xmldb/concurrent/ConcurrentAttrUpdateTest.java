/*
 * Created on Sep 25, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.exist.xmldb.concurrent;

import java.io.File;

import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.AttributeUpdateAction;
import org.junit.After;
import org.junit.Before;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertNotNull;

/**
 * @author wolf
 */
public class ConcurrentAttrUpdateTest extends ConcurrentTestBase {

	private final static String URI = XmldbURI.LOCAL_DB;
	
	@SuppressWarnings("unused")
	private final static String QUERY =
		"//ELEMENT[@attribute-1]";
	
	private File tempFile;

	public ConcurrentAttrUpdateTest() {
		super(URI, "C1");
	}

	@Before
	@Override
	public void setUp() throws Exception {
        super.setUp();
        String[] wordList = DBUtils.wordList(rootCol);
        assertNotNull(wordList);
        tempFile = DBUtils.generateXMLFile(250, 10, wordList);
        DBUtils.addXMLResource(getTestCollection(), "R1.xml", tempFile);
        addAction(new AttributeUpdateAction(URI + "/C1", "R1.xml", wordList), 20, 0, 0);
        //addAction(new XQueryAction(URI + "/C1", "R1.xml", QUERY), 100, 100, 30);
	}

    @After
    @Override
    public void tearDown() throws XMLDBException {
        super.tearDown();
        tempFile.delete();
    }
}

