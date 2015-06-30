package org.exist.xmldb.concurrent;

import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.ComplexUpdateAction;
import org.junit.After;
import org.junit.Before;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * @author wolf
 */
public class ComplexUpdateTest extends ConcurrentTestBase {

	private final static String URI = XmldbURI.LOCAL_DB;
	
	private final static String XML =
		"<TEST><USER-SESSION-DATA version=\"0\"/></TEST>";

	public ComplexUpdateTest() {
		super(URI, "complex");
	}
	
	@Before
    @Override
	public void setUp() throws Exception {
        super.setUp();
        XMLResource res = (XMLResource)getTestCollection().createResource("R01.xml", "XMLResource");
        res.setContent(XML);
        getTestCollection().storeResource(res);
        getTestCollection().close();
        addAction(new ComplexUpdateAction(URI + "/complex", "R01.xml", 200), 1, 0, 0);
	}

    @After
    @Override
    public void tearDown() throws XMLDBException {
        super.tearDown();
    }
}
