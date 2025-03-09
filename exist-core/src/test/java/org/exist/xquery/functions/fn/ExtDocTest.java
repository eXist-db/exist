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

package org.exist.xquery.functions.fn;

import com.googlecode.junittoolbox.ParallelParameterized;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.FileUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.transform.Source;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.xmldb.api.base.ResourceType.XML_RESOURCE;

@RunWith(ParallelParameterized.class)
public class ExtDocTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"external-doc-ns-1", "<elem1 xmlns:xyz=\"http://xyz\"/>", null},
                {"external-doc-ns-2", "<elem1 xmlns=\"hello\" xmlns:xyz=\"http://xyz\"/>", null},
                {"external-doc-ns-3", "<abc:elem1 xmlns:abc=\"hello\" xmlns:xyz=\"http://xyz\"/>", null},
                {"external-doc-ns-4", "<abc:elem1 xmlns:abc=\"hello\" xmlns:xyz=\"http://xyz\" xmlns=\"123\"/>", null}
        });
    }


    @Parameter
    public String docName;

    @Parameter(value = 1)
    public String docContent;

    @Parameter(value = 2)
    public Path externalDoc;

    @Before
    public void storeExtDoc() throws IOException {
        final Path externalDocFile = Files.createTempFile(docName, "xml");
        Files.writeString(externalDocFile, docContent, UTF_8);
        this.externalDoc = externalDocFile;
    }

    @After
    public void removeExtDoc() {
        if (externalDoc != null) {
            FileUtils.deleteQuietly(externalDoc);
        }
    }

    @Test
    public void parse() throws XMLDBException {
        final URI docUri = externalDoc.toUri();
        final ResourceSet result = existEmbeddedServer.executeQuery(
            "xquery version \"3.1\";\n" +
            "\n" +
            "declare namespace output = \"http://www.w3.org/2010/xslt-xquery-serialization\";" +
            "declare option output:omit-xml-declaration \"yes\";\n" +
            "\n" +
            "fn:doc('" + docUri + "')"
        );

        assertNotNull(result);
        assertEquals(1, result.getSize());
        final Resource resource = result.getResource(0);
        assertNotNull(resource);
        assertEquals(XML_RESOURCE, resource.getResourceType());

        final Source expectedSource = Input.fromString(docContent).build();
        final Source actualSource = Input.fromNode(((XMLResource)resource).getContentAsDOM()).build();

        final Diff diff = DiffBuilder.compare(expectedSource)
                .withTest(actualSource)
                .checkForSimilar()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }
}
