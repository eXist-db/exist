package org.exist.xmldb.concurrent;

import java.io.File;

import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.XQueryUpdateAction;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

public class ConcurrentQueryUpdateTest extends ConcurrentTestBase {

	public static void main(String[] args) {
		junit.textui.TestRunner.run(ConcurrentQueryUpdateTest.class);
	}

	private final static String URI = XmldbURI.LOCAL_DB;
	
	@SuppressWarnings("unused")
	private File tempFile;
	
	public ConcurrentQueryUpdateTest(String name) {
		super(name, URI, "C1");
	}
	
	protected void setUp() {
		try {
			super.setUp();
			
			Collection col = getTestCollection();
			XMLResource res = (XMLResource) col.createResource("testappend.xml", "XMLResource");
			res.setContent("<root><node id=\"1\"/></root>");
			col.storeResource(res);
			
			addAction(new XQueryUpdateAction(URI + "/C1", "testappend.xml"), 20, 0, 0);
			addAction(new XQueryUpdateAction(URI + "/C1", "testappend.xml"), 20, 0, 0);
		} catch (Exception e) {            
            fail(e.getMessage()); 
        }
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.test.concurrent.ConcurrentTestBase#tearDown()
	 */
	protected void tearDown() {
		try {
			Collection col = getTestCollection();
			XQueryService service = (XQueryService) col.getService("XQueryService", "1.0");
			ResourceSet result = service.query("distinct-values(//node/@id)");
			assertEquals(result.getSize(), 41);
			for (int i = 0; i < result.getSize(); i++) {
				XMLResource next = (XMLResource) result.getResource((long)i);
				System.out.println(next.getContent());
			}
			
			super.tearDown();
		    DBUtils.shutdownDB(URI);
		} catch (Exception e) {
			e.printStackTrace();
            fail(e.getMessage()); 
        }				
	}
}
