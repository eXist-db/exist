package org.exist.xquery.functions.util;

import com.evolvedbinary.j8fu.tuple.Tuple2;
import com.googlecode.junittoolbox.ParallelRunner;
import org.custommonkey.xmlunit.exceptions.XpathException;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;

import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import static org.junit.Assert.*;

import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;

import org.junit.runner.RunWith;
import org.xml.sax.SAXException;
import java.io.IOException;
import java.util.List;

import org.custommonkey.xmlunit.XMLUnit;
import org.exist.xquery.XPathException;

import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author jim.fuller@webcomposite.com
 */
@RunWith(ParallelRunner.class)
public class SerializeTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true);

    @Test
    public void testSerialize() throws XPathException, XMLDBException, IOException, SAXException {
        final String query = "let $xml := <test/>\n" +
                "return\n" +
                "util:serialize($xml,'method=xml')";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertXMLEqual("<test/>", r);
    }

    @Test
    public void testSerialize2() throws XPathException, XMLDBException, IOException, SAXException {
        final String query = "let $xml := <test><a/><b/></test>\n" +
                "return\n" +
                "util:serialize($xml,'method=xml indent=no')";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertXMLEqual("<test><a/><b/></test>", r);
    }

    @Test
    public void testSerializeText() throws XPathException, XMLDBException, IOException, SAXException {
        final String query = "let $xml := <test><a>test</a></test>\n" +
                "return\n" +
                "util:serialize($xml,'method=text indent=no')";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals("test", r);
    } 

    @Test
    public void testSerializeIndent() throws XPathException, XMLDBException, IOException, SAXException {
        final String query = "let $xml := <test><a/><b/></test>\n" +
                "return\n" +
                "util:serialize($xml,'method=xml indent=yes')";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        XMLUnit.setIgnoreWhitespace(true);
        assertXMLEqual("<test><a/><b/></test>", r);
    }

    @Test
    public void testSerializeXincludes() throws XPathException, XMLDBException, IOException, SAXException, XpathException {
        final String query = "let $xml := " +
                "<test xmlns:xi='http://www.w3.org/2001/XInclude'>" +
                "	<xi:include href='/db/system/security/config.xml'/>" +
                "</test>\n" +
                "return\n" +
                "util:serialize($xml,'enable-xincludes=yes')";

        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertXpathEvaluatesTo("2.0","/test//@version",r);
    }

    @Test
    public void parseOption() {
        final List<Tuple2<String, String>> result = Serialize.parseOption("method=text");
        assertEquals(1, result.size());
        final Tuple2<String, String> kv = result.get(0);
        assertEquals("method", kv._1);
        assertEquals("text", kv._2);
    }

    @Test
    public void parseOption_docTypePublic() {
        final List<Tuple2<String, String>> result = Serialize.parseOption("doctype-public=-//D//DTD P-Topic//EN");
        assertEquals(1, result.size());
        final Tuple2<String, String> kv = result.get(0);
        assertEquals("doctype-public", kv._1);
        assertEquals("-//D//DTD P-Topic//EN", kv._2);
    }

    @Test
    public void parseOption_docTypeSystem() {
        final List<Tuple2<String, String>> result = Serialize.parseOption("doctype-system=http://domain.com/xmetal/schemas/p-topic.dtd");
        assertEquals(1, result.size());
        final Tuple2<String, String> kv = result.get(0);
        assertEquals("doctype-system", kv._1);
        assertEquals("http://domain.com/xmetal/schemas/p-topic.dtd", kv._2);
    }

    @Test
    public void parseOption_multiSingleString() {
        final List<Tuple2<String, String>> result = Serialize.parseOption("method=text indent=no");
        assertEquals(2, result.size());

        final Tuple2<String, String> kv1 = result.get(0);
        assertEquals("method", kv1._1);
        assertEquals("text", kv1._2);

        final Tuple2<String, String> kv2 = result.get(1);
        assertEquals("indent", kv2._1);
        assertEquals("no", kv2._2);
    }

    @Test
    public void parseOption_multiSingleStringWithSpace() {
        final List<Tuple2<String, String>> result = Serialize.parseOption("doctype-public=-//D//DTD P-Topic//EN method=text indent=no");
        assertEquals(3, result.size());

        final Tuple2<String, String> kv = result.get(0);
        assertEquals("doctype-public", kv._1);
        assertEquals("-//D//DTD P-Topic//EN", kv._2);

        final Tuple2<String, String> kv2 = result.get(1);
        assertEquals("method", kv2._1);
        assertEquals("text", kv2._2);

        final Tuple2<String, String> kv3 = result.get(2);
        assertEquals("indent", kv3._1);
        assertEquals("no", kv3._2);
    }

    @Test
    public void parseOption_multiSingleStringWithSpace2() {
        final List<Tuple2<String, String>> result = Serialize.parseOption("method=text doctype-public=-//D//DTD P-Topic//EN indent=no");
        assertEquals(3, result.size());

        final Tuple2<String, String> kv1 = result.get(0);
        assertEquals("method", kv1._1);
        assertEquals("text", kv1._2);

        final Tuple2<String, String> kv2 = result.get(1);
        assertEquals("doctype-public", kv2._1);
        assertEquals("-//D//DTD P-Topic//EN", kv2._2);

        final Tuple2<String, String> kv3 = result.get(2);
        assertEquals("indent", kv3._1);
        assertEquals("no", kv3._2);
    }

    @Test
    public void parseOption_multiSingleStringWithSpace3() {
        final List<Tuple2<String, String>> result = Serialize.parseOption("method=text indent=no doctype-public=-//D//DTD P-Topic//EN");
        assertEquals(3, result.size());

        final Tuple2<String, String> kv1 = result.get(0);
        assertEquals("method", kv1._1);
        assertEquals("text", kv1._2);

        final Tuple2<String, String> kv2 = result.get(1);
        assertEquals("indent", kv2._1);
        assertEquals("no", kv2._2);

        final Tuple2<String, String> kv3 = result.get(2);
        assertEquals("doctype-public", kv3._1);
        assertEquals("-//D//DTD P-Topic//EN", kv3._2);
    }
}
