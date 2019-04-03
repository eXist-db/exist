/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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

package org.exist.dom.memtree;

import com.googlecode.junittoolbox.ParallelParameterized;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;
import org.xmlunit.builder.DiffBuilder;
import org.xmlunit.builder.Input;
import org.xmlunit.diff.Diff;

import javax.xml.transform.Source;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

/**
 * https://github.com/eXist-db/exist/issues/1682#issuecomment-402108184
 */
@RunWith(ParallelParameterized.class)
public class DocumentBuilderReceiverIntegrationTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true, true);

    @Parameterized.Parameters(name = "{0}")
    public static java.util.Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"docs-null-ns-1", "<x>{document { <dummy xmlns=''/> }}</x>", "<x><dummy/></x>"},
                {"merge-docs-ns-1", "<x>{parse-xml(\"<dummy xmlns=''/>\")}</x>", "<x><dummy/></x>"},
                {"merge-docs-ns-2", "<x xmlns=''>{parse-xml(\"<dummy xmlns=''/>\")}</x>", "<x><dummy/></x>"},
                {"merge-docs-ns-3", "<x xmlns='xyz'>{parse-xml(\"<dummy xmlns=''/>\")}</x>", "<x xmlns='xyz'><dummy xmlns=''/></x>"},
                {"merge-docs-ns-3", "<x xmlns=''>{parse-xml(\"<dummy xmlns='xyz'/>\")}</x>", "<x><dummy xmlns='xyz'/></x>"}
        });
    }

    @Parameterized.Parameter
    public String testName;

    @Parameterized.Parameter(value = 1)
    public String query;

    @Parameterized.Parameter(value = 2)
    public String expectedResult;

    @Test
    public void mergeDocuments() throws XMLDBException {
        final ResourceSet result = existEmbeddedServer.executeQuery(query);

        assertNotNull(result);
        assertEquals(1, result.getSize());

        final Resource resource = result.getResource(0);
        assertNotNull(resource);
        assertEquals(XMLResource.RESOURCE_TYPE, resource.getResourceType());

        final Source expectedSource = Input.fromString(expectedResult).build();
        final Source actualSource = Input.fromNode(((XMLResource)resource).getContentAsDOM()).build();

        final Diff diff = DiffBuilder.compare(expectedSource)
                .withTest(actualSource)
                .checkForSimilar()
                .ignoreWhitespace()
                .build();

        assertFalse(diff.toString(), diff.hasDifferences());
    }
}
