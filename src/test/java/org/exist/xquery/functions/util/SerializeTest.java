package org.exist.xquery.functions.util;

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
        assertXMLEqual(r,"<test/>");
    }

    @Test
    public void testSerialize2() throws XPathException, XMLDBException, IOException, SAXException {
        final String query = "let $xml := <test><a/><b/></test>\n" +
                "return\n" +
                "util:serialize($xml,'method=xml indent=no')";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertXMLEqual(r,"<test><a/><b/></test>");
    }

    @Test
    public void testSerializeText() throws XPathException, XMLDBException, IOException, SAXException {
        final String query = "let $xml := <test><a>test</a></test>\n" +
                "return\n" +
                "util:serialize($xml,'method=text indent=no')";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        assertEquals(r,"test");
    } 

    @Test
    public void testSerializeIndent() throws XPathException, XMLDBException, IOException, SAXException {
        final String query = "let $xml := <test><a/><b/></test>\n" +
                "return\n" +
                "util:serialize($xml,'method=xml indent=yes')";
        final ResourceSet result = existEmbeddedServer.executeQuery(query);
        final String r = (String) result.getResource(0).getContent();
        XMLUnit.setIgnoreWhitespace(true);
        assertXMLEqual(r,"<test><a/><b/></test>");
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
}