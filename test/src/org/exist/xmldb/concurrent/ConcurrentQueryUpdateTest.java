package org.exist.xmldb.concurrent;

import java.io.File;

import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.XQueryUpdateAction;
import org.junit.After;
import org.junit.Before;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;

public class ConcurrentQueryUpdateTest extends ConcurrentTestBase {

	private final static String URI = XmldbURI.LOCAL_DB;
	
	@SuppressWarnings("unused")
	private File tempFile;
	
	public ConcurrentQueryUpdateTest() {
		super(URI, "C1");
	}

	@Before
	@Override
	public void setUp() throws Exception {
		super.setUp();

		Collection col = getTestCollection();
		XMLResource res = (XMLResource) col.createResource("testappend.xml", "XMLResource");
		res.setContent("<root><node id=\"1\"/></root>");
		col.storeResource(res);

		addAction(new XQueryUpdateAction(URI + "/C1", "testappend.xml"), 20, 0, 0);
		addAction(new XQueryUpdateAction(URI + "/C1", "testappend.xml"), 20, 0, 0);
	}

	@After
	@Override
	public void tearDown() throws XMLDBException {
		Collection col = getTestCollection();
		XQueryService service = (XQueryService) col.getService("XQueryService", "1.0");
		ResourceSet result = service.query("distinct-values(//node/@id)");
		assertEquals(result.getSize(), 41);
		for (int i = 0; i < result.getSize(); i++) {
			XMLResource next = (XMLResource) result.getResource((long)i);
			next.getContent();
		}

		super.tearDown();
		DBUtils.shutdownDB(URI);
	}
}
