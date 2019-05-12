/*
 * Created on 17.03.2005 - $Id: XQueryFunctionsTest.java 3080 2006-04-07 22:17:14Z dizzzz $
 */
package org.exist.xquery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.Configuration;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Tests for various standard XQuery functions
 *
 * @author jens
 */
public class JavaFunctionsTest {

    private static final Logger LOG = LogManager.getLogger(JavaFunctionsTest.class);

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    private boolean javabindingenabled = false;

    /**
     * Tests simple list functions to make sure java functions are being
     * called properly
     */
    @Test
    public void lists() throws XPathException {
        try {
            String query = "declare namespace list='java:java.util.ArrayList'; " +
                    "let $list := list:new() " +
                    "let $actions := (list:add($list,'a'),list:add($list,'b'),list:add($list,'c')) " +
                    "return list:get($list,1)";
            ResourceSet result = existEmbeddedServer.executeQuery(query);
            String r = (String) result.getResource(0).getContent();
            assertEquals("b", r);
        } catch (XMLDBException e) {
            //if exception is a java binding exception and java binding is disabled then this is a success
            if (e.getMessage().indexOf("Java binding is disabled in the current configuration") > -1 && !javabindingenabled) {
                return;
            }

            LOG.error(e.getMessage(), e);
            fail(e.getMessage());
        }
    }

    @Before
    public void setUp() throws Exception {
        //Check the configuration file to see if Java binding is enabled
        //if it is not enabled then we expect an exception when trying to
        //perform Java binding.
        Configuration config = new Configuration();
        String javabinding = (String) config.getProperty(FunctionFactory.PROPERTY_ENABLE_JAVA_BINDING);
        if (javabinding != null) {
            if (javabinding.equals("yes")) {
                javabindingenabled = true;
            }
        }
    }
}
