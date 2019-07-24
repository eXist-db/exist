package org.exist.xquery;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.junit.Assert.assertEquals;

/**
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public abstract class AbstractDescendantOrSelfNodeKindTest {

    @ClassRule
    public final static ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    protected final static String TEST_DOCUMENT = "<doc xml:id=\"x\">\n"+
        "<?xml-stylesheet type=\"text/xsl\" href=\"test\"?>\n"+
        "    <a>\n"+
        "        <b x=\"1\">text<e>text</e>text</b>\n"+
        "        </a>\n"+
        "    <a>\n"+
        "        <c><!--comment-->\n"+
        "            <d xmlns=\"x\" y=\"2\" z=\"3\">text</d>\n"+
        "            </c>\n"+
        "    </a>\n"+
        "</doc>";


    protected abstract ResourceSet executeQueryOnDoc(final String docQuery) throws XMLDBException;

    @Test
    public void documentNodeCount() throws XMLDBException {
        final ResourceSet result = executeQueryOnDoc("count($doc//document-node())");
        assertEquals(1, result.getSize());
        assertEquals(0, Integer.parseInt((String)result.getResource(0).getContent()));
    }

    @Test
    public void nodeCount() throws XMLDBException {
        final ResourceSet result = executeQueryOnDoc("count($doc//node())");
        assertEquals(1, result.getSize());
        assertEquals(23, Integer.parseInt((String)result.getResource(0).getContent()));
    }

    @Test
    public void elementCount() throws XMLDBException {
        final ResourceSet result = executeQueryOnDoc("count($doc//element())");
        assertEquals(1, result.getSize());
        assertEquals(7, Integer.parseInt((String)result.getResource(0).getContent()));
    }

    @Test
    public void textCount() throws XMLDBException {
        final ResourceSet result = executeQueryOnDoc("count($doc//text())");
        assertEquals(1, result.getSize());
        assertEquals(14, Integer.parseInt((String)result.getResource(0).getContent()));
    }

    @Test
    public void attributeCount() throws XMLDBException {
        final ResourceSet result = executeQueryOnDoc("count($doc//attribute())");
        assertEquals(1, result.getSize());
        assertEquals(4, Integer.parseInt((String)result.getResource(0).getContent()));
    }

    @Test
    public void commentCount() throws XMLDBException {
        final ResourceSet result = executeQueryOnDoc("count($doc//comment())");
        assertEquals(1, result.getSize());
        assertEquals(1, Integer.parseInt((String)result.getResource(0).getContent()));
    }

    @Test
    public void processingInstructionCount() throws XMLDBException {
        final ResourceSet result = executeQueryOnDoc("count($doc//processing-instruction())");
        assertEquals(1, result.getSize());
        assertEquals(1, Integer.parseInt((String)result.getResource(0).getContent()));
    }
}
