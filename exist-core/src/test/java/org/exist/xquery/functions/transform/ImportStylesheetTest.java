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
package org.exist.xquery.functions.transform;

import org.exist.config.TwoDatabasesTest;
import org.exist.repo.AutoDeploymentTrigger;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.*;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;

import static com.ibm.icu.impl.Assert.fail;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Test transform:transform with an imported stylesheet from various
 * locations
 *
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 * @author <a href="mailto:juri.leino@existsolutions.com">Juri Leino</a>
 */
public class ImportStylesheetTest {

    private static final String XSL_DB_LOCATION = "/db/system/repo/functx-1.0.1/functx/functx.xsl";
    private static final String INPUT_XML = "<in>bonjourno</in>";
    private static final String EXPECTED_OUTPUT = "<out>hello</out>";

    private static Path getConfigFile() {
        final ClassLoader loader = ImportStylesheetTest.class.getClassLoader();
        final char separator = System.getProperty("file.separator").charAt(0);
        final String packagePath = ImportStylesheetTest.class.getPackage().getName().replace('.', separator);

        try {
            return Paths.get(loader.getResource(packagePath + separator + "conf.xml").toURI());
        } catch (final URISyntaxException e) {
            fail(e);
            return null;
        }
    }

    private static String getQuery(final String importLocation) {
        final String xslt =
                "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\"\n" +
                "    xmlns:xs=\"http://www.w3.org/2001/XMLSchema\"\n" +
                "    xmlns:functx=\"http://www.functx.com\"\n" +
                "    exclude-result-prefixes=\"functx xs\"\n" +
                "    version=\"2.0\">\n" +
                "    <xsl:import href=\"" + importLocation + "\"/>\n" +
                "    <xsl:template match=\"/\">\n" +
                "      <out>\n" +
                "        <xsl:value-of select=\"functx:replace-first(., 'bonjourno', 'hello')\"/>\n" +
                "      </out>\n" +
                "    </xsl:template>\n" +
                "</xsl:stylesheet>";

        return "transform:transform(" + INPUT_XML + ", " + xslt + ", ())";
    }

    private static void assertTransformationResult(ResourceSet result) throws XMLDBException {
        assertNotNull(result);
        assertEquals(1, result.getSize());
        assertEquals(EXPECTED_OUTPUT, result.getResource(0).getContent());
    }

    @ClassRule
    public static ExistXmldbEmbeddedServer existXmldbEmbeddedServer = new ExistXmldbEmbeddedServer(true, false, true, getConfigFile());

    @Test
    public void fromRegisteredImportUri() throws XMLDBException {
        final String xquery = getQuery("http://www.functx.com/functx.xsl");
        final ResourceSet result = existXmldbEmbeddedServer.executeQuery(xquery);
        assertTransformationResult(result);
    }

    @Test
    public void fromDbLocationWithoutScheme() throws XMLDBException {
        final String xquery = getQuery(XSL_DB_LOCATION);
        final ResourceSet result = existXmldbEmbeddedServer.executeQuery(xquery);
        assertTransformationResult(result);
    }

    @Test
    public void fromDbLocationWithXmldbScheme() throws XMLDBException {
        final String xquery = getQuery("xmldb:" + XSL_DB_LOCATION);
        final ResourceSet result = existXmldbEmbeddedServer.executeQuery(xquery);
        assertTransformationResult(result);
    }

    @Test
    public void fromDbLocationWithXmldbSchemeDoubleSlash() throws XMLDBException {
        final String xquery = getQuery("xmldb://" + XSL_DB_LOCATION);
        final ResourceSet result = existXmldbEmbeddedServer.executeQuery(xquery);
        assertTransformationResult(result);
    }

    /** This test fails at the moment with "unknown protocol: exist" */
    @Test
    @Ignore
    public void fromDbLocationWithExistScheme() throws XMLDBException {
        final String xquery = getQuery("exist:" + XSL_DB_LOCATION);
        final ResourceSet result = existXmldbEmbeddedServer.executeQuery(xquery);
        assertTransformationResult(result);
    }

    @Test
    public void fromDbLocationWithExistSchemeDoubleSlash() throws XMLDBException {
        final String xquery = getQuery("exist://" + XSL_DB_LOCATION);
        final ResourceSet result = existXmldbEmbeddedServer.executeQuery(xquery);
        assertTransformationResult(result);
    }
}
