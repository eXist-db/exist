package org.exist.xquery;

import org.junit.After;
import org.junit.Before;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

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
        return  existEmbeddedServer.executeQuery(getDbQuery(docQuery));
    }

    @Before
    public void storeTestDoc() throws XMLDBException {
        final Collection root =  existEmbeddedServer.getRoot();
        final XMLResource res = (XMLResource)root.createResource(TEST_DOCUMENT_NAME, "XMLResource");
        res.setContent(TEST_DOCUMENT);
        root.storeResource(res);
    }

    @After
    public void removeTestDoc() throws XMLDBException {
        final Collection root =  existEmbeddedServer.getRoot();
        final Resource res = root.getResource(TEST_DOCUMENT_NAME);
        root.removeResource(res);
    }
}
