package org.exist.xquery;

import com.googlecode.junittoolbox.ParallelRunner;
import org.junit.runner.RunWith;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
@RunWith(ParallelRunner.class)
public class MemtreeDescendantOrSelfNodeKindTest extends AbstractDescendantOrSelfNodeKindTest {

    private String getInMemoryQuery(final String queryPostfix) {
        return "declare boundary-space preserve;\n"
                + "let $doc := document {\n" +
            TEST_DOCUMENT +
            "\n}\n" +
            "return\n" +
            queryPostfix;
    }

    @Override
    protected ResourceSet executeQueryOnDoc(final String docQuery) throws XMLDBException {
        final String query = getInMemoryQuery(docQuery);
        return existEmbeddedServer.executeQuery(query);
    }
}
