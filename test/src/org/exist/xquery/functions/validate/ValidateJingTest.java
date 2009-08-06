package org.exist.xquery.functions.validate;

import org.exist.test.EmbeddedExistTester;
import org.junit.Test;
import static org.junit.Assert.*;

import org.exist.xquery.XPathException;


import org.junit.Ignore;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author jim.fuller@webcomposite.com
 */
public class ValidateJingTest extends EmbeddedExistTester {

    @Test
    public void testValidateXSDwithJing() {
        LOG.info("start test");

        ResourceSet result = null;
        String r = "";
        try {
            String query = "let $v := <doc>\n" +
                    "\t<title>Title</title>\n" +
                    "\t<p>Some paragraph.</p>\n" +
                    "      </doc>\n" +
                    "let $schema := <xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                    "\t\t elementFormDefault=\"qualified\">\n" +
                    "\t<xs:element name=\"doc\">\n" +
                    "\t  <xs:complexType>\n" +
                    "\t    <xs:sequence>\n" +
                    "\t      <xs:element minOccurs=\"0\" ref=\"title\"/>\n" +
                    "\t      <xs:element minOccurs=\"0\" maxOccurs=\"unbounded\" ref=\"p\"/>\n" +
                    "\t    </xs:sequence>\n" +
                    "\t  </xs:complexType>\n" +
                    "\t</xs:element>\n" +
                    "\t<xs:element name=\"title\" type=\"xs:string\"/>\n" +
                    "\t<xs:element name=\"p\" type=\"xs:string\"/>\n" +
                    "      </xs:schema>\n" +
                    "return\n" +
                    "\n" +
                    "\tvalidation:jing($v,$schema)";

            result = xpxqService.query(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("true", r);

        } catch (XMLDBException e) {
            LOG.error("testValidateXSDwithJing(): " + e.getMessage(), e);
            fail(e.getMessage());
        }

    }

    @Test
    public void testValidateXSDwithJing_invalid() {
        LOG.info("start test");

        ResourceSet result = null;
        String r = "";
        try {
            String query = "let $v := <doc>\n" +
                    "\t<title1>Title</title1>\n" +
                    "\t<p>Some paragraph.</p>\n" +
                    "      </doc>\n" +
                    "let $schema := <xs:schema xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                    "\t\t elementFormDefault=\"qualified\">\n" +
                    "\t<xs:element name=\"doc\">\n" +
                    "\t  <xs:complexType>\n" +
                    "\t    <xs:sequence>\n" +
                    "\t      <xs:element minOccurs=\"0\" ref=\"title\"/>\n" +
                    "\t      <xs:element minOccurs=\"0\" maxOccurs=\"unbounded\" ref=\"p\"/>\n" +
                    "\t    </xs:sequence>\n" +
                    "\t  </xs:complexType>\n" +
                    "\t</xs:element>\n" +
                    "\t<xs:element name=\"title\" type=\"xs:string\"/>\n" +
                    "\t<xs:element name=\"p\" type=\"xs:string\"/>\n" +
                    "      </xs:schema>\n" +
                    "return\n" +
                    "\n" +
                    "\tvalidation:jing($v,$schema)";

            result = executeQuery(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("false", r);

        } catch (XMLDBException e) {
            System.out.println("testValidateXSDwithJing_invalid(): " + e);
            fail(e.getMessage());
        }

    }

    @Test
    public void testValidateRNGwithJing() throws XPathException {

        LOG.info("start test");

        ResourceSet result = null;
        String r = "";
        try {
            String query = "let $v := <doc>\n" +
                    "\t<title>Title</title>\n" +
                    "\t<p>Some paragraph.</p>\n" +
                    "      </doc>\n" +
                    "let $schema := <grammar xmlns=\"http://relaxng.org/ns/structure/1.0\">\n" +
                    "  <start>\n" +
                    "    <ref name=\"doc\"/>\n" +
                    "  </start>\n" +
                    "  <define name=\"doc\">\n" +
                    "    <element name=\"doc\">\n" +
                    "      <optional>\n" +
                    "        <ref name=\"title\"/>\n" +
                    "      </optional>\n" +
                    "      <zeroOrMore>\n" +
                    "        <ref name=\"p\"/>\n" +
                    "      </zeroOrMore>\n" +
                    "    </element>\n" +
                    "  </define>\n" +
                    "  <define name=\"title\">\n" +
                    "    <element name=\"title\">\n" +
                    "      <text/>\n" +
                    "    </element>\n" +
                    "  </define>\n" +
                    "  <define name=\"p\">\n" +
                    "    <element name=\"p\">\n" +
                    "      <text/>\n" +
                    "    </element>\n" +
                    "  </define>\n" +
                    "</grammar>\n" +
                    "return\n" +
                    "\n" +
                    "\tvalidation:jing($v,$schema)";

            result = executeQuery(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("true", r);

        } catch (XMLDBException e) {
            LOG.error("testValidateRNGwithJing(): " + e.getMessage(), e);
            fail(e.getMessage());
        }

    }

    @Test
    public void testValidateRNGwithJing_invalid() {

        LOG.info("start test");

        ResourceSet result = null;
        String r = "";
        try {
            String query = "let $v := <doc>\n" +
                    "\t<title1>Title</title1>\n" +
                    "\t<p>Some paragraph.</p>\n" +
                    "      </doc>\n" +
                    "let $schema := <grammar xmlns=\"http://relaxng.org/ns/structure/1.0\">\n" +
                    "  <start>\n" +
                    "    <ref name=\"doc\"/>\n" +
                    "  </start>\n" +
                    "  <define name=\"doc\">\n" +
                    "    <element name=\"doc\">\n" +
                    "      <optional>\n" +
                    "        <ref name=\"title\"/>\n" +
                    "      </optional>\n" +
                    "      <zeroOrMore>\n" +
                    "        <ref name=\"p\"/>\n" +
                    "      </zeroOrMore>\n" +
                    "    </element>\n" +
                    "  </define>\n" +
                    "  <define name=\"title\">\n" +
                    "    <element name=\"title\">\n" +
                    "      <text/>\n" +
                    "    </element>\n" +
                    "  </define>\n" +
                    "  <define name=\"p\">\n" +
                    "    <element name=\"p\">\n" +
                    "      <text/>\n" +
                    "    </element>\n" +
                    "  </define>\n" +
                    "</grammar>\n" +
                    "return\n" +
                    "\n" +
                    "\tvalidation:jing($v,$schema)";

            result = executeQuery(query);
            r = (String) result.getResource(0).getContent();
            assertEquals("false", r);

        } catch (XMLDBException e) {
            LOG.error("testValidateRNGwithJing_invalid(): " + e.getMessage(), e);
            fail(e.getMessage());
        }

    }

    @Test
    @Ignore("Looks good, but memory issue")
    public void repeatTests() {
        for (int i = 0; i < 1000; i++) {
            try {
                System.out.println("nr=" + i);
                testValidateRNGwithJing();
                testValidateRNGwithJing_invalid();
                testValidateXSDwithJing();
                testValidateXSDwithJing_invalid();

            } catch (Exception ex) {
                fail(ex.getMessage());
                ex.printStackTrace();
            }

        }
    }
}
