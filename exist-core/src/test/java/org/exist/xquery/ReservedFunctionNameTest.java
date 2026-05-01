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
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XPathQueryService;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Verifies that XQuery's ReservedFunctionNames constraint is enforced for
 * FunctionDecl: a reserved keyword may not be used as the unprefixed name
 * of a function declaration. See W3C XQuery 3.0+ A.1.1 and the XQTS test
 * set prod-FunctionDecl/function-decl-reserved-function-names-*.
 */
@RunWith(Parameterized.class)
public class ReservedFunctionNameTest {

    @ClassRule
    public static final ExistXmldbEmbeddedServer EXIST_EMBEDDED_SERVER =
            new ExistXmldbEmbeddedServer(false, true, true);

    @Parameterized.Parameter
    public String reservedName;

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"attribute"},
                {"comment"},
                {"document-node"},
                {"element"},
                {"function"},
                {"if"},
                {"item"},
                {"namespace-node"},
                {"node"},
                {"processing-instruction"},
                {"schema-attribute"},
                {"schema-element"},
                {"switch"},
                {"text"},
                {"typeswitch"}
        });
    }

    @Test
    public void reservedNameRejected() throws XMLDBException {
        final String query =
                "declare default function namespace 'http://www.w3.org/2005/xquery-local-functions';\n"
                        + "declare function " + reservedName + "() { fn:true() };\n"
                        + "local:" + reservedName + "()";

        final XPathQueryService service = EXIST_EMBEDDED_SERVER.getRoot().getService(XPathQueryService.class);
        try {
            service.query(query);
            fail("Expected XPST0003 for reserved function name '" + reservedName + "'");
        } catch (final XMLDBException e) {
            final String message = e.getMessage() == null ? "" : e.getMessage();
            assertTrue("Expected XPST0003 in message but got: " + message,
                    message.contains("XPST0003"));
        }
    }
}
