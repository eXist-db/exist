package org.exist.xmldb.concurrent;

import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.ComplexUpdateAction;
import org.xmldb.api.modules.XMLResource;

/**
 * @author wolf
 */
public class ComplexUpdateTest extends ConcurrentTestBase {

	private final static String URI = XmldbURI.LOCAL_DB;
	
	private final static String XML =
		"<TEST><USER-SESSION-DATA version=\"0\"/></TEST>";
	
	public static void main(String[] args) {
		junit.textui.TestRunner.run(ComplexUpdateTest.class);
	}
	
	/**
     * 
     * 
     * @param name 
     */
	public ComplexUpdateTest(String name) {
		super(name, URI, "complex");
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.test.concurrent.ConcurrentTestBase#setUp()
	 */
	protected void setUp() {
		try {
			super.setUp();			
			XMLResource res = (XMLResource)getTestCollection().createResource("R01.xml", "XMLResource");
			res.setContent(XML);
			getTestCollection().storeResource(res);
			getTestCollection().close();			
			addAction(new ComplexUpdateAction(URI + "/complex", "R01.xml", 200), 1, 0, 0);
		} catch (Exception e) {            
            fail(e.getMessage()); 
        }				
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.test.concurrent.ConcurrentTestBase#tearDown()
	 */
	protected void tearDown() {
		try {
			DBUtils.shutdownDB(rootColURI);
		} catch (Exception e) {            
            fail(e.getMessage()); 
        }				
	}

}
