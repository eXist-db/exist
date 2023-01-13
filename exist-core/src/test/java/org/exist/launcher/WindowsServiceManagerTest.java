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
package org.exist.launcher;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class WindowsServiceManagerTest {

    @Test
    public void asJavaCmdlineMemoryString() {
        assertEquals(Optional.of("1024k"), WindowsServiceManager.asJavaCmdlineMemoryString("1024k"));
        assertEquals(Optional.of("1024K"), WindowsServiceManager.asJavaCmdlineMemoryString("1024K"));
        assertEquals(Optional.of("1024m"), WindowsServiceManager.asJavaCmdlineMemoryString("1024m"));
        assertEquals(Optional.of("1024M"), WindowsServiceManager.asJavaCmdlineMemoryString("1024M"));
        assertEquals(Optional.of("1024g"), WindowsServiceManager.asJavaCmdlineMemoryString("1024g"));
        assertEquals(Optional.of("1024G"), WindowsServiceManager.asJavaCmdlineMemoryString("1024G"));

        // default to MB
        assertEquals(Optional.of("128m"), WindowsServiceManager.asJavaCmdlineMemoryString("128"));

        // ignore junk
        assertEquals(Optional.empty(), WindowsServiceManager.asJavaCmdlineMemoryString("One"));

        // if the unit is unknown, fallback to MB
        assertEquals(Optional.of("1024m"), WindowsServiceManager.asJavaCmdlineMemoryString("1024t"));
        assertEquals(Optional.of("1024m"), WindowsServiceManager.asJavaCmdlineMemoryString("1024T"));
        assertEquals(Optional.of("1024m"), WindowsServiceManager.asJavaCmdlineMemoryString("1024z"));
        assertEquals(Optional.of("1024m"), WindowsServiceManager.asJavaCmdlineMemoryString("1024Z"));
    }

    @Test
    public void asPrunSrvMemoryString() {
        assertEquals(Optional.of("1"), WindowsServiceManager.asPrunSrvMemoryString("1024k"));
        assertEquals(Optional.of("1"), WindowsServiceManager.asPrunSrvMemoryString("1024K"));
        assertEquals(Optional.of("1024"), WindowsServiceManager.asPrunSrvMemoryString("1024m"));
        assertEquals(Optional.of("1024"), WindowsServiceManager.asPrunSrvMemoryString("1024M"));
        assertEquals(Optional.of("1048576"), WindowsServiceManager.asPrunSrvMemoryString("1024g"));
        assertEquals(Optional.of("1048576"), WindowsServiceManager.asPrunSrvMemoryString("1024G"));

        // default to MB
        assertEquals(Optional.of("128"), WindowsServiceManager.asPrunSrvMemoryString("128"));

        // ignore junk
        assertEquals(Optional.empty(), WindowsServiceManager.asPrunSrvMemoryString("One"));

        // if the unit is unknown, fallback to MB
        assertEquals(Optional.of("1024"), WindowsServiceManager.asPrunSrvMemoryString("1024t"));
        assertEquals(Optional.of("1024"), WindowsServiceManager.asPrunSrvMemoryString("1024T"));
        assertEquals(Optional.of("1024"), WindowsServiceManager.asPrunSrvMemoryString("1024z"));
        assertEquals(Optional.of("1024"), WindowsServiceManager.asPrunSrvMemoryString("1024Z"));
    }
}
