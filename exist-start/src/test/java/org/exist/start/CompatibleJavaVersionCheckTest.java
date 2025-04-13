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
package org.exist.start;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class CompatibleJavaVersionCheckTest {

    @Test
    public void extractNoVersionComponents() {
        final Optional<int[]> maybeComponents = CompatibleJavaVersionCheck.extractJavaVersionComponents(Optional.empty());
        assertFalse(maybeComponents.isPresent());
    }

    @Test
    public void extractJava8VersionComponents() {
        final Optional<int[]> maybeComponents = CompatibleJavaVersionCheck.extractJavaVersionComponents(Optional.of("1.8.0_292"));
        assertTrue(maybeComponents.isPresent());
        final int[] components = maybeComponents.get();
        assertEquals(4, components.length);
        assertEquals(1, components[0]);
        assertEquals(8, components[1]);
        assertEquals(0, components[2]);
        assertEquals(292, components[3]);
    }

    @Test
    public void extractJava9VersionComponents() {
        Optional<int[]> maybeComponents = CompatibleJavaVersionCheck.extractJavaVersionComponents(Optional.of("9.0.4"));
        assertTrue(maybeComponents.isPresent());
        int[] components = maybeComponents.get();
        assertEquals(3, components.length);
        assertEquals(9, components[0]);
        assertEquals(0, components[1]);
        assertEquals(4, components[2]);

        maybeComponents = CompatibleJavaVersionCheck.extractJavaVersionComponents(Optional.of("9.0.7.1"));
        assertTrue(maybeComponents.isPresent());
        components = maybeComponents.get();
        assertEquals(4, components.length);
        assertEquals(9, components[0]);
        assertEquals(0, components[1]);
        assertEquals(7, components[2]);
        assertEquals(1, components[3]);
    }

    @Test
    public void extractJava10VersionComponents() {
        final Optional<int[]> maybeComponents = CompatibleJavaVersionCheck.extractJavaVersionComponents(Optional.of("10"));
        assertTrue(maybeComponents.isPresent());
        final int[] components = maybeComponents.get();
        assertEquals(1, components.length);
        assertEquals(10, components[0]);
    }

    @Test
    public void extractJava11VersionComponents() {
        Optional<int[]> maybeComponents = CompatibleJavaVersionCheck.extractJavaVersionComponents(Optional.of("11"));
        assertTrue(maybeComponents.isPresent());
        int[] components = maybeComponents.get();
        assertEquals(1, components.length);
        assertEquals(11, components[0]);

        maybeComponents = CompatibleJavaVersionCheck.extractJavaVersionComponents(Optional.of("11.0.11"));
        assertTrue(maybeComponents.isPresent());
        components = maybeComponents.get();
        assertEquals(3, components.length);
        assertEquals(11, components[0]);
        assertEquals(0, components[1]);
        assertEquals(11, components[2]);
    }

    @Test
    public void extractJava12VersionComponents() {
        Optional<int[]> maybeComponents = CompatibleJavaVersionCheck.extractJavaVersionComponents(Optional.of("12.0.1"));
        assertTrue(maybeComponents.isPresent());
        int[] components = maybeComponents.get();
        assertEquals(3, components.length);
        assertEquals(12, components[0]);
        assertEquals(0, components[1]);
        assertEquals(1, components[2]);

        maybeComponents = CompatibleJavaVersionCheck.extractJavaVersionComponents(Optional.of("12.0.2-BellSoft"));
        assertTrue(maybeComponents.isPresent());
        components = maybeComponents.get();
        assertEquals(3, components.length);
        assertEquals(12, components[0]);
        assertEquals(0, components[1]);
        assertEquals(2, components[2]);
    }

    @Test
    public void extractJava13VersionComponents() {
        final Optional<int[]> maybeComponents = CompatibleJavaVersionCheck.extractJavaVersionComponents(Optional.of("13.0.2"));
        assertTrue(maybeComponents.isPresent());
        final int[] components = maybeComponents.get();
        assertEquals(3, components.length);
        assertEquals(13, components[0]);
        assertEquals(0, components[1]);
        assertEquals(2, components[2]);
    }

    @Test
    public void extractJava14VersionComponents() {
        final Optional<int[]> maybeComponents = CompatibleJavaVersionCheck.extractJavaVersionComponents(Optional.of("14.0.2"));
        assertTrue(maybeComponents.isPresent());
        final int[] components = maybeComponents.get();
        assertEquals(3, components.length);
        assertEquals(14, components[0]);
        assertEquals(0, components[1]);
        assertEquals(2, components[2]);
    }

    @Test
    public void extractJava15VersionComponents() {
        final Optional<int[]> maybeComponents = CompatibleJavaVersionCheck.extractJavaVersionComponents(Optional.of("15.0.3"));
        assertTrue(maybeComponents.isPresent());
        final int[] components = maybeComponents.get();
        assertEquals(3, components.length);
        assertEquals(15, components[0]);
        assertEquals(0, components[1]);
        assertEquals(3, components[2]);
    }

    @Test
    public void checkNoVersion() throws StartException {
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion(Optional.empty());
    }

    @Test
    public void checkJava8() throws StartException {
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion(Optional.of("1.8.0_292"));
    }

    @Test
    public void checkJava9() throws StartException {
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion(Optional.of("9.0.4"));
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion(Optional.of("9.0.7.1"));
    }

    @Test
    public void checkJava10() throws StartException {
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion(Optional.of("10"));
    }

    @Test
    public void checkJava11() throws StartException {
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion(Optional.of("11"));
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion(Optional.of("11.0.11"));
    }

    @Test(expected = StartException.class)
    public void checkJava12() throws StartException {
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion(Optional.of("12.0.1"));
    }

    @Test(expected = StartException.class)
    public void checkJava12_BellSoft() throws StartException {
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion(Optional.of("12.0.2-BellSoft"));
    }

    @Test(expected = StartException.class)
    public void checkJava13() throws StartException {
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion(Optional.of("13.0.2"));
    }

    @Test(expected = StartException.class)
    public void checkJava14() throws StartException {
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion(Optional.of("14.0.2"));
    }

    @Test(expected = StartException.class)
    public void checkJava15_0_0() throws StartException {
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion(Optional.of("15.0.0"));
    }

    @Test(expected = StartException.class)
    public void checkJava15_0_1() throws StartException {
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion(Optional.of("15.0.1"));
    }

    @Test
    public void checkJava15_0_2() throws StartException {
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion(Optional.of("15.0.2"));
    }

    @Test
    public void checkJava15_0_3() throws StartException {
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion(Optional.of("15.0.3"));
    }

    @Test
    public void checkJava21() throws StartException {
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion(Optional.of("21.0.6"));
    }

    @Test
    public void checkJava25() throws StartException {
        CompatibleJavaVersionCheck.checkForCompatibleJavaVersion(Optional.of("25.0.1"));
    }
}
