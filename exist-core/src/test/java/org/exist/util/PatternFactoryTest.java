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
package org.exist.util;

import org.junit.jupiter.api.Test;

import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PatternFactoryTest {

    @Test
    void differentPatternAndFlagsPairsDoNotShareAnEntry() {
        final PatternFactory factory = PatternFactory.getInstance();

        // "a1" + 0 and "a" + 10 used to build the same key, "a10"
        final Pattern first = factory.getPattern("a1", 0);
        final Pattern second = factory.getPattern("a", 10);

        assertEquals("a1", first.pattern());
        assertEquals(0, first.flags());
        assertEquals("a", second.pattern());
        assertEquals(10, second.flags());
    }

    @Test
    void flagsAreNotMistakenForPartOfThePattern() {
        final PatternFactory factory = PatternFactory.getInstance();

        // "b" + 2 and the one-argument "b2" used to build the same key, "b2"
        final Pattern withFlags = factory.getPattern("b", Pattern.CASE_INSENSITIVE);
        final Pattern withoutFlags = factory.getPattern("b2");

        assertEquals("b", withFlags.pattern());
        assertEquals(Pattern.CASE_INSENSITIVE, withFlags.flags());
        assertEquals("b2", withoutFlags.pattern());
        assertEquals(0, withoutFlags.flags());
    }

    @Test
    void samePatternAndFlagsShareAnEntry() {
        final PatternFactory factory = PatternFactory.getInstance();

        assertEquals(factory.getPattern("c", Pattern.MULTILINE), factory.getPattern("c", Pattern.MULTILINE));
        assertEquals(factory.getPattern("d"), factory.getPattern("d", 0));
    }
}
