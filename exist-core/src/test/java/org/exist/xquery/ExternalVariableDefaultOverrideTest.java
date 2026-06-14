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

import org.exist.test.ExistXmldbEmbeddedServer;
import org.junit.ClassRule;
import org.junit.Test;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XQueryService;

import static org.junit.Assert.assertEquals;

/**
 * A value supplied for an external variable by the external environment must take precedence over the
 * variable's declared default value (XQuery 3.1 §4.15 External Variables). Previously eXist always
 * evaluated the default for an {@code external := ...} declaration, ignoring a supplied value.
 */
public class ExternalVariableDefaultOverrideTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer server = new ExistXmldbEmbeddedServer(false, true, true);

    @Test
    public void suppliedValueOverridesDefault() throws XMLDBException {
        final XQueryService service = server.getRoot().getService(XQueryService.class);
        service.declareVariable("greeting", "supplied");
        final ResourceSet result = service.query(
                "declare variable $greeting external := 'default'; $greeting");
        assertEquals(1, result.getSize());
        assertEquals("supplied", result.getResource(0).getContent());
    }

    @Test
    public void typedSuppliedValueOverridesDefault() throws XMLDBException {
        final XQueryService service = server.getRoot().getService(XQueryService.class);
        service.declareVariable("n", 21);
        final ResourceSet result = service.query(
                "declare variable $n as xs:integer external := 0; $n * 2");
        assertEquals(1, result.getSize());
        assertEquals("42", result.getResource(0).getContent());
    }

    @Test
    public void defaultUsedWhenNotSupplied() throws XMLDBException {
        final XQueryService service = server.getRoot().getService(XQueryService.class);
        final ResourceSet result = service.query(
                "declare variable $absent external := 'default'; $absent");
        assertEquals(1, result.getSize());
        assertEquals("default", result.getResource(0).getContent());
    }

    @Test
    public void missingRequiredExternalStillErrors() throws XMLDBException {
        final XQueryService service = server.getRoot().getService(XQueryService.class);
        try {
            service.query("declare variable $required external; $required");
            org.junit.Assert.fail("expected XPDY0002 for an unbound external variable with no default");
        } catch (final XMLDBException expected) {
            // XPDY0002: no value specified for external variable
        }
    }

    @Test
    public void plainGlobalInitializerUnaffected() throws XMLDBException {
        final XQueryService service = server.getRoot().getService(XQueryService.class);
        final ResourceSet result = service.query("declare variable $x := 'internal'; $x");
        assertEquals(1, result.getSize());
        assertEquals("internal", result.getResource(0).getContent());
    }
}
