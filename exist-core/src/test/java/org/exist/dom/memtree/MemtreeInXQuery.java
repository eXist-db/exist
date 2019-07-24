package org.exist.dom.memtree;

import com.googlecode.junittoolbox.ParallelRunner;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.*;
import org.junit.runner.RunWith;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
@RunWith(ParallelRunner.class)
public class MemtreeInXQuery {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(true, true, true);

    @Test
    public void pi_attributes() throws XMLDBException {
        final String xquery = "let $doc := document{\n" +
                "    processing-instruction{\"ok\"}{\"ok\"},\n" +
                "    <root/>\n" +
                "}\n" +
                "return count($doc//processing-instruction()/@*)";

        final ResourceSet result = existEmbeddedServer.executeQuery(xquery);

        assertEquals(1, result.getSize());
        assertEquals(0, Integer.parseInt(result.getResource(0).getContent().toString()));

        result.clear();
    }

    @Test
    public void pi_children() throws XMLDBException {
        final String xquery = "let $doc := document{\n" +
                "    processing-instruction{\"ok\"}{\"ok\"},\n" +
                "    <root/>\n" +
                "}\n" +
                "return count($doc//processing-instruction()/node())";

        final ResourceSet result = existEmbeddedServer.executeQuery(xquery);

        assertEquals(1, result.getSize());
        assertEquals(0, Integer.parseInt(result.getResource(0).getContent().toString()));

        result.clear();
    }

    @Test
    public void pi_descendantAttributes() throws XMLDBException {
        final String xquery = "let $doc := document{\n" +
                "    processing-instruction{\"ok\"}{\"ok\"},\n" +
                "    <root/>\n" +
                "}\n" +
                "return count($doc//processing-instruction()//@*)";

        final ResourceSet result = existEmbeddedServer.executeQuery(xquery);

        assertEquals(1, result.getSize());
        assertEquals(0, Integer.parseInt(result.getResource(0).getContent().toString()));

        result.clear();
    }

    @Test
    public void attr_attributes() throws XMLDBException {
        final String xquery = "let $doc := document {\n" +
                "    element a {\n" +
                "        attribute x { \"y\" }\n" +
                "    }\n" +
                "} return\n" +
                "    count($doc/a/@x/@y)";

        final ResourceSet result = existEmbeddedServer.executeQuery(xquery);

        assertEquals(1, result.getSize());
        assertEquals(0, Integer.parseInt(result.getResource(0).getContent().toString()));

        result.clear();
    }

    @Test
    public void attr_children() throws XMLDBException {
        final String xquery = "let $doc := document {\n" +
                "    element a {\n" +
                "        attribute x { \"y\" }\n" +
                "    }\n" +
                "} return\n" +
                "    count($doc/a/@x/node())";

        final ResourceSet result = existEmbeddedServer.executeQuery(xquery);

        assertEquals(1, result.getSize());
        assertEquals(0, Integer.parseInt(result.getResource(0).getContent().toString()));

        result.clear();
    }
}
