package org.exist.xmldb.concurrent;

import org.exist.xmldb.XmldbURI;
import org.exist.xmldb.concurrent.action.ComplexUpdateAction;
import org.junit.Before;
import org.xmldb.api.modules.XMLResource;

import java.util.Arrays;
import java.util.List;

/**
 * @author wolf
 */
public class ComplexUpdateTest extends ConcurrentTestBase {
	
	private static final String XML =
		"<TEST><USER-SESSION-DATA version=\"0\"/></TEST>";
	
	@Before
	public void setUp() throws Exception {
        final XMLResource res = (XMLResource)getTestCollection().createResource("R01.xml", "XMLResource");
        res.setContent(XML);
        getTestCollection().storeResource(res);
        getTestCollection().close();
	}

    @Override
    public String getTestCollectionName() {
        return "complex";
    }

    @Override
    public List<Runner> getRunners() {
        return Arrays.asList(
                new Runner(new ComplexUpdateAction(XmldbURI.LOCAL_DB + "/complex", "R01.xml", 200), 1, 0, 0)
        );
    }
}
