package org.exist.xmldb.concurrent;

import java.util.Arrays;
import java.util.List;

import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.XQueryUpdateAction;
import org.junit.Before;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;

public class ConcurrentQueryUpdateTest extends ConcurrentTestBase {

	@Before
	public void setUp() throws Exception {
		final Collection col = getTestCollection();
		final XMLResource res = (XMLResource) col.createResource("testappend.xml", "XMLResource");
		res.setContent("<root><node id=\"1\"/></root>");
		col.storeResource(res);
	}

	@Override
	public void assertAdditional() throws XMLDBException {
		final Collection col = getTestCollection();
		final XQueryService service = (XQueryService) col.getService("XQueryService", "1.0");
		final ResourceSet result = service.query("distinct-values(//node/@id)");
		assertEquals(result.getSize(), 41);
		for (int i = 0; i < result.getSize(); i++) {
			final XMLResource next = (XMLResource) result.getResource((long)i);
			next.getContent();
		}
	}

	@Override
	public String getTestCollectionName() {
		return "C1";
	}

	@Override
	public List<Runner> getRunners() {
		return Arrays.asList(
				new Runner(new XQueryUpdateAction(XmldbURI.LOCAL_DB + "/C1", "testappend.xml"), 20, 0, 0),
				new Runner(new XQueryUpdateAction(XmldbURI.LOCAL_DB + "/C1", "testappend.xml"), 20, 0, 0)
		);
	}
}
