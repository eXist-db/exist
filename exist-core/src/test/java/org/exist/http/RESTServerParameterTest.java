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
package org.exist.http;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that the REST parameter names do not depend on the default locale.
 */
public class RESTServerParameterTest {

    @Test
    @ResourceLock(Resources.LOCALE)
    public void queryStringKeyDoesNotDependOnDefaultLocale() {
        final Locale previous = Locale.getDefault();
        try {
            // Turkish lower-cases "I" to a dotless i with the default locale
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("_indent", RESTServerParameter.Indent.queryStringKey());
            assertEquals("_omit-xml-declaration", RESTServerParameter.Omit_Xml_Declaration.queryStringKey());
        } finally {
            Locale.setDefault(previous);
        }
    }
}
