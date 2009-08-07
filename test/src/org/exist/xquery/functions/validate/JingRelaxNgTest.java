package org.exist.xquery.functions.validate;

import org.exist.test.EmbeddedExistTester;
import java.io.File;
import java.io.FilenameFilter;
import org.junit.*;
import static org.junit.Assert.*;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;

/**
 *
 * @author wessels
 */
public class JingRelaxNgTest extends EmbeddedExistTester {

    @BeforeClass
    public static void prepareResources() throws Exception {

        String noValidation = "<?xml version='1.0'?>" +
                "<collection xmlns=\"http://exist-db.org/collection-config/1.0" +
                "\">" +
                "<validation mode=\"no\"/>" +
                "</collection>";

        Collection conf = createCollection(rootCollection, "system/config/db/personal");
        storeResource(conf, "collection.xconf", noValidation.getBytes());

        Collection collection = createCollection(rootCollection, "personal");

        File directory = new File("samples/validation/personal");

        FilenameFilter filter = new FilenameFilter() {

            public boolean accept(File dir, String name) {
                return (name.startsWith("personal"));
            }
        };

        for (File file : directory.listFiles(filter)) {
            LOG.info("Storing " + file.getAbsolutePath());
            byte[] data = readFile(directory, file.getName());
            storeResource(collection, file.getName(), data);
        }

    }


    @Test
    public void rng_stored_valid() {
        String query = "validation:jing( doc('/db/personal/personal-valid.xml'), doc('/db/personal/personal.rng') )";

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
    public void rng_stored_invalid() {
        String query = "validation:jing( doc('/db/personal/personal-invalid.xml'), doc('/db/personal/personal.rng') )";

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

    @Test
    public void rng_anyuri_valid() {
        String query = "validation:jing( xs:anyURI('xmldb:exist:///db/personal/personal-valid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/personal/personal.rng') )";

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
    public void rng_anyuri_invalid() {
        String query = "validation:jing( xs:anyURI('xmldb:exist:///db/personal/personal-invalid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/personal/personal.rng') )";

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

    @Test
    //@Ignore("rnc is binary file")
    public void rnc_stored_valid() {
        String query = "validation:jing-report( doc('/db/personal/personal-valid.xml'), " +
                //"util:binary-doc('/db/personal/personal.rnc') )";
                "xs:anyURI('xmldb:exist:///db/personal/personal.rnc') )";

        try {
            ResourceSet results = executeQuery(query);
            assertEquals(1, results.getSize());

            String r = (String) results.getResource(0).getContent();
            System.out.println(r);

            assertXpathEvaluatesTo("valid", "//status/text()", r);

        } catch (Exception ex) {
            LOG.error(ex);
            fail(ex.getMessage());
        }
    }

    @Test
    //@Ignore("rnc is binary file")
    public void rnc_stored_invalid() {
        String query = "validation:jing-report( doc('/db/personal/personal-invalid.xml'), " +
                //"util:binary-doc('/db/personal/personal.rnc') )";
                "xs:anyURI('xmldb:exist:///db/personal/personal.rnc') )";

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
    public void rnc_anyuri_valid() {
        String query = "validation:jing( xs:anyURI('xmldb:exist:///db/personal/personal-valid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/personal/personal.rnc') )";

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
    public void rnc_anyuri_invalid() {
        String query = "validation:jing( xs:anyURI('xmldb:exist:///db/personal/personal-invalid.xml'), " +
                "xs:anyURI('xmldb:exist:///db/personal/personal.rnc') )";

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
