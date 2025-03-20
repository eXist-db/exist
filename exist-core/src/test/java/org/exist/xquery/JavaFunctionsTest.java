/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.Configuration;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

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
            if ("yes".equals(javabinding)) {
                javabindingenabled = true;
            }
        }
    }
}
