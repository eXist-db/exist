package org.exist.xquery;

import org.junit.After;
import org.junit.Before;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmldb.api.modules.XQueryService;

/**
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class PersistentDescendantOrSelfNodeKindTest extends AbstractDescendantOrSelfNodeKindTest {

    private final String TEST_DOCUMENT_NAME = "PersistentDescendantOrSelfNodeKindTest.xml";

    private String getDbQuery(final String queryPostfix) {
        return "let $doc := doc('/db/" + TEST_DOCUMENT_NAME + "')\n" +
            "return\n" +
            queryPostfix;
    }

    @Override
    protected ResourceSet executeQueryOnDoc(final String docQuery) throws XMLDBException {
        final XQueryService service = (XQueryService) root.getService("XPathQueryService", "1.0");
        return service.query(getDbQuery(docQuery));
    }

    @Before
    public void storeTestDoc() throws XMLDBException {
        final XMLResource res = (XMLResource)root.createResource(TEST_DOCUMENT_NAME, "XMLResource");
        res.setContent(TEST_DOCUMENT);
        root.storeResource(res);
    }

    @After
    public void removeTestDoc() throws XMLDBException {
        final Resource res = root.getResource(TEST_DOCUMENT_NAME);
        root.removeResource(res);
    }
}
