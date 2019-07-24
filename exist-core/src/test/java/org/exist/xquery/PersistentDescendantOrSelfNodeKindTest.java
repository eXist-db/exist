package org.exist.xquery;

import com.googlecode.junittoolbox.ParallelRunner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
@RunWith(ParallelRunner.class)
public class PersistentDescendantOrSelfNodeKindTest extends AbstractDescendantOrSelfNodeKindTest {

    private static final String TEST_DOCUMENT_NAME = "PersistentDescendantOrSelfNodeKindTest.xml";

    private String getDbQuery(final String queryPostfix) {
        return "let $doc := doc('/db/" + TEST_DOCUMENT_NAME + "')\n" +
            "return\n" +
            queryPostfix;
    }

    @Override
    protected ResourceSet executeQueryOnDoc(final String docQuery) throws XMLDBException {
        return  existEmbeddedServer.executeQuery(getDbQuery(docQuery));
    }

    @BeforeClass
    public static void storeTestDoc() throws XMLDBException {
        final Collection root =  existEmbeddedServer.getRoot();
        final XMLResource res = (XMLResource)root.createResource(TEST_DOCUMENT_NAME, "XMLResource");
        res.setContent(TEST_DOCUMENT);
        root.storeResource(res);
    }

    @AfterClass
    public static void removeTestDoc() throws XMLDBException {
        final Collection root =  existEmbeddedServer.getRoot();
        final Resource res = root.getResource(TEST_DOCUMENT_NAME);
        root.removeResource(res);
    }
}
