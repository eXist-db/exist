package org.exist.xquery.functions.validate;

import org.exist.test.EmbeddedExistTester;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.exist.xquery.XPathException;

import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;
import org.custommonkey.xmlunit.exceptions.XpathException;

import org.xml.sax.SAXException;
import java.io.IOException;

/**
 *
 * @author jim.fuller@webcomposite.com
 */
public class JingOnvdlTest extends EmbeddedExistTester {

    private final static String RNG_DATA1 =
            "<element  name=\'Book\' xmlns='http://relaxng.org/ns/structure/1.0'  ns=\'http://www.books.org\'> " +
            "<element name=\'Title\'><text/></element>" +
            "<element name=\'Author\'><text/></element>" +
            "<element name=\'Date\'><text/></element>" +
            "<element name=\'ISBN\'><text/></element>" +
            "<element name=\'Publisher\'><text/></element>" +
            "</element>";
    private final static String NVDL_DATA1 = "<rules xmlns='http://purl.oclc.org/dsdl/nvdl/ns/structure/1.0'> " +
            "<namespace ns=\'http://www.books.org\'>" +
            "     <validate schema=\"Book.rng\" />" +
            "</namespace>" +
            "</rules>";
    private final static String XML_DATA1 = "<Book xmlns='http://www.books.org'> " +
            "<Title>The Wisdom of Crowds</Title>" +
            "<Author>James Surowiecki</Author>" +
            "<Date>2005</Date>" +
            "<ISBN>0-385-72170-6</ISBN>" +
            "<Publisher>Anchor Books</Publisher>" +
            "</Book>";

    private final static String XML_DATA2 = "<Book xmlns='http://www.books.org'> " +
            "<Title>The Wisdom of Crowds</Title>" +
            "<Author>James Surowiecki</Author>" +
            "<Dateee>2005</Dateee>" +
            "<ISBN>0-385-72170-6</ISBN>" +
            "<Publisher>Anchor Books</Publisher>" +
            "</Book>";

    public JingOnvdlTest() {
    }

    @Before
    public void setUp() throws Exception {

        String query = "xmldb:create-collection('xmldb:exist:///db','validate-test')";
        ResourceSet result = executeQuery(query);

        String query1 = "xmldb:store('/db/validate-test', 'test.nvdl'," + NVDL_DATA1 + ")";
        ResourceSet result1 = executeQuery(query1);

        String query2 = "xmldb:store('/db/validate-test', 'Book.rng'," + RNG_DATA1 + ")";
        ResourceSet result2 = executeQuery(query2);

        String data1 = "xmldb:store('/db/validate-test', 'valid.xml'," + XML_DATA1 + ")";
        ResourceSet result3 = executeQuery(data1);

        String data2 = "xmldb:store('/db/validate-test', 'invalid.xml'," + XML_DATA2 + ")";
        ResourceSet result4 = executeQuery(data2);
    }

    @Test
    public void onvdl_valid() throws XPathException, IOException, XpathException, SAXException {

        ResourceSet result = null;
        String r = "";
        try {
            String query = "let $a := " + XML_DATA1 +
                    "let $b := xs:anyURI('/db/validate-test/test.nvdl')" +
                    "return " +
                    "validation:validate-report($a,$b)";
            result = executeQuery(query);
            r = (String) result.getResource(0).getContent();
            System.out.println(r);

            assertXpathEvaluatesTo("valid", "//status/text()", r);

        } catch (XMLDBException e) {
            LOG.error("testONVDL(): " + e.getMessage(), e);
            fail(e.getMessage());
        }

    }

    @Test
    public void onvdl_invalid() throws XPathException, IOException, XpathException, SAXException {

        ResourceSet result = null;
        String r = "";
        try {
            String query = "let $a := <test/>" +
                    "let $b := xs:anyURI('/db/validate-test/test.nvdl')" +
                    "return " +
                    "validation:validate-report($a,$b)";
            result = executeQuery(query);
            r = (String) result.getResource(0).getContent();
            System.out.println(r);

            assertXpathEvaluatesTo("invalid", "//status/text()", r);

        } catch (XMLDBException e) {
            LOG.error("testONVDLFail(): " + e.getMessage(), e);
            fail(e.getMessage());
        }

    }


    @Test
    public void onvdl_stored_valid() {
        String query = "validation:jing-report( doc('/db/validate-test/valid.xml'), doc('/db/validate-test/test.nvdl') )";

        try {
            ResourceSet results = executeQuery(query);
            assertEquals(1, results.getSize());
            
            String r = (String) results.getResource(0).getContent();
            System.out.println(r);

            assertXpathEvaluatesTo("valid", "//status/text()", r);

        } catch (Exception ex) {
            LOG.error(ex);
            ex.printStackTrace();
            fail(ex.getMessage());

        }
    }

    @Test
    public void onvdl_stored_invalid() {
        String query = "validation:jing-report( doc('/db/validate-test/invalid.xml'), doc('/db/validate-test/test.nvdl') )";

        try {
            ResourceSet results = executeQuery(query);
            assertEquals(1, results.getSize());

            String r = (String) results.getResource(0).getContent();
            System.out.println(r);

            assertXpathEvaluatesTo("invalid", "//status/text()", r);

        } catch (Exception ex) {
            LOG.error(ex);
            fail(ex.getMessage());
        }
    }

    @Test
    public void onvdl_anyuri_valid() {
        String query = "validation:jing( xs:anyURI('xmldb:exist:///db/validate-test/valid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/validate-test/test.nvdl') )";

        try {
            ResourceSet results = executeQuery(query);
            assertEquals(1, results.getSize());
            assertEquals(query, "true",
                    results.getResource(0).getContent().toString());

        } catch (Exception ex) {
            LOG.error(ex);
            fail(ex.getMessage());
        }
    }

    @Test
    public void onvdl_anyuri_invalid() {
        String query = "validation:jing( xs:anyURI('xmldb:exist:///db/validate-test/invalid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/validate-test/test.nvdl') )";

        try {
            ResourceSet results = executeQuery(query);
            assertEquals(1, results.getSize());
            assertEquals(query, "false",
                    results.getResource(0).getContent().toString());

        } catch (Exception ex) {
            LOG.error(ex);
            fail(ex.getMessage());
        }
    }
}
