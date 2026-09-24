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
package org.exist.repo;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PackageServiceJsonTest {

    @Test
    public void readsATopLevelStringMember() {
        assertEquals("http://example.com/pkg",
                PackageService.extractJsonStringValue("{\"name\":\"http://example.com/pkg\",\"url\":\"u\"}", "name"));
    }

    /** A nested object's member of the same name is not the one asked for. */
    @Test
    public void ignoresNestedMembers() {
        assertEquals("u", PackageService.extractJsonStringValue(
                "{\"other\":{\"url\":\"nested\"},\"url\":\"u\"}", "url"));
    }

    @Test
    public void unescapesTheValue() {
        assertEquals("say \"hi\"", PackageService.extractJsonStringValue("{\"name\":\"say \\\"hi\\\"\"}", "name"));
    }

    @Test
    public void missingOrNonStringOrMalformedGivesNull() {
        assertNull(PackageService.extractJsonStringValue("{\"url\":\"u\"}", "name"));
        assertNull(PackageService.extractJsonStringValue("{\"name\":42}", "name"));
        assertNull(PackageService.extractJsonStringValue("{\"name\":", "name"));
        assertNull(PackageService.extractJsonStringValue("[\"name\"]", "name"));
    }
}
