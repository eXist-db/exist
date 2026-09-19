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
package org.exist.xqdoc;

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertTrue;

/**
 * Pins the deprecation of {@code xqdm:scan}, agreed for 7.0.0 in
 * <a href="https://github.com/eXist-db/exist/issues/6717">issue #6717</a>.
 *
 * <p>Deprecation here means the notice is discoverable through every channel eXist offers for it —
 * {@code util:describe-function}, {@code inspect:inspect-module}, and the module description — while
 * the function itself remains callable. Removal is a later, separate step.</p>
 */
public class XQDocDeprecationTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(false, true, true);

    private static final String NS = "declare namespace xqdm='http://exist-db.org/xquery/xqdoc'; ";
    private static final String MODULE_URI = "http://exist-db.org/xquery/xqdoc";

    private static String query(final String xquery) throws XMLDBException {
        final XQueryService xqs = server.getRoot().getService(XQueryService.class);
        final ResourceSet rs = xqs.query(xquery);
        return rs.getSize() == 0 ? "" : String.valueOf(rs.getResource(0).getContent());
    }

    /** Both arities carry the notice, reachable via util:describe-function. */
    @Test
    public void describeFunctionReportsDeprecation() throws XMLDBException {
        final String deprecated = query(NS
                + "string-join(util:describe-function(xs:QName('xqdm:scan'))//deprecated, '|')");
        assertTrue("xqdm:scan should report a deprecation notice, got: " + deprecated,
                deprecated.contains("Deprecated for removal"));
        assertTrue("the notice should name the replacement, got: " + deprecated,
                deprecated.contains("inspect:inspect-module"));
    }

    /** The same notice is reachable via inspect:inspect-module. */
    @Test
    public void inspectModuleReportsDeprecation() throws XMLDBException {
        final String deprecated = query(NS + "string((inspect:inspect-module-uri(xs:anyURI('"
                + MODULE_URI + "'))//function[@name='xqdm:scan']/deprecated)[1])");
        assertTrue("inspect:inspect-module should report the deprecation, got: " + deprecated,
                deprecated.contains("Deprecated for removal"));
    }

    /** The module's own description carries the notice too. */
    @Test
    public void moduleDescriptionReportsDeprecation() throws XMLDBException {
        final String description = query(NS
                + "string(inspect:inspect-module-uri(xs:anyURI('" + MODULE_URI + "'))/description)");
        assertTrue("the module description should say it is deprecated, got: " + description,
                description.contains("DEPRECATED FOR REMOVAL"));
        assertTrue("the module description should link the decision, got: " + description,
                description.contains("issues/6717"));
    }

    /** Deprecated is not disabled: the function is still resolvable in a default configuration. */
    @Test
    public void functionRemainsAvailable() throws XMLDBException {
        assertTrue(Boolean.parseBoolean(
                query(NS + "exists(util:describe-function(xs:QName('xqdm:scan')))")));
    }
}
