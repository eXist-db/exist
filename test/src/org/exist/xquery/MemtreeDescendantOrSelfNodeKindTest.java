package org.exist.xquery;

import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

/**
 * @author Adam Retter <adam.retter@googlemail.com>
 */
public class MemtreeDescendantOrSelfNodeKindTest extends AbstractDescendantOrSelfNodeKindTest {

    private String getInMemoryQuery(final String queryPostfix) {
        return "let $doc := document {\n" +
            TEST_DOCUMENT +
            "\n}\n" +
            "return\n" +
            queryPostfix;
    }

    @Override
    protected ResourceSet executeQueryOnDoc(final String docQuery) throws XMLDBException {
        final String query = getInMemoryQuery(docQuery);

        final XQueryService service = (XQueryService) root.getService("XPathQueryService", "1.0");
        return service.query(query);
    }
}
