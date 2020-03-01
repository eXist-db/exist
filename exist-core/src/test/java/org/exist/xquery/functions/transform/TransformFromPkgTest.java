/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2020 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.transform;

import org.exist.repo.AutoDeploymentTrigger;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.*;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
public class TransformFromPkgTest {

    @Rule
    public ExistXmldbEmbeddedServer existXmldbEmbeddedServer = new ExistXmldbEmbeddedServer(true, false, true);

    private static String PREV_AUTODEPLOY_DIRECTORY = null;

    @BeforeClass
    public static void setup() throws URISyntaxException {
        final URL functxPkgUrl = TransformFromPkgTest.class.getResource("/functx/functx-1.0.1.xar");
        final Path functxDir = Paths.get(functxPkgUrl.toURI());
        PREV_AUTODEPLOY_DIRECTORY = System.getProperty(AutoDeploymentTrigger.AUTODEPLOY_DIRECTORY_PROPERTY);
        System.setProperty(AutoDeploymentTrigger.AUTODEPLOY_DIRECTORY_PROPERTY, functxDir.getParent().toAbsolutePath().toString());
    }

    @AfterClass
    public static void cleanup() {
        if (PREV_AUTODEPLOY_DIRECTORY != null && !PREV_AUTODEPLOY_DIRECTORY.isEmpty()) {
            System.setProperty(AutoDeploymentTrigger.AUTODEPLOY_DIRECTORY_PROPERTY, PREV_AUTODEPLOY_DIRECTORY);
        }
    }

    @Test
    public void transformWithModuleFromPkg() throws XMLDBException {
        final String xslt =
                "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n" +
                        "    xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                        "    xmlns:functx=\"http://www.functx.com\"\n" +
                        "    exclude-result-prefixes=\"xs\"\n" +
                        "    version=\"2.0\">\n" +
                        "    \n" +
                        "    <xsl:import href=\"http://www.functx.com/functx.xsl\"/>\n" +
                        "    \n" +
                        "    <xsl:template match=\"/\">\n" +
                        "        <xsl:value-of select=\"functx:replace-first('hello', 'he', 'ho')\"/>\n" +
                        "    </xsl:template>\n" +
                        "    \n" +
                        "</xsl:stylesheet>";

        final String xml = "<x>bonjourno</x>";

        final String xquery = "transform:transform(" + xml + ", " + xslt + ", ())";

        final ResourceSet result = existXmldbEmbeddedServer.executeQuery(xquery);
        assertNotNull(result);
        assertEquals(1, result.getSize());
    }
}
