/*
 * Created on Sep 25, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.exist.xmldb.concurrent;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.exist.util.FileUtils;
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

//	private static final String QUERY =
//		"//ELEMENT[@attribute-1]";

    private String[] wordList;
    private Path tempFile;

    @Before
    public void setUp() throws Exception {
        this.wordList = DBUtils.wordList();
        assertNotNull(wordList);
        this.tempFile = DBUtils.generateXMLFile(250, 10, wordList);
        DBUtils.addXMLResource(getTestCollection(), "R1.xml", tempFile);
    }

    @After
    public void tearDown() throws XMLDBException {
        FileUtils.deleteQuietly(tempFile);
    }

    @Override
    public String getTestCollectionName() {
        return "C1";
    }

    @Override
    public List<Runner> getRunners() {
        return Arrays.asList(
                new Runner(new AttributeUpdateAction(XmldbURI.LOCAL_DB + "/C1", "R1.xml", wordList), 20, 0, 0)
                //new Runner(new XQueryAction(getUri + "/C1", "R1.xml", QUERY), 100, 100, 30);
        );
    }
}

