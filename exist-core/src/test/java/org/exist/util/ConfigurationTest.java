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
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

class ConfigurationTest {
    @Test
    void testConfigurationConstructors() throws Exception {
        assertThatNoException().isThrownBy(Configuration::new);
        assertThatNoException().isThrownBy(() -> new Configuration(null));
    }

    @Test
    void testConfigurationConstructorWithClasspathConf(@TempDir Path existHomeDir) throws Exception {
        assertThatNoException().isThrownBy(() -> new Configuration("conf.xml"));
        assertThatNoException().isThrownBy(() -> new Configuration("conf.xml", Optional.empty()));
        assertThatNoException().isThrownBy(() -> new Configuration("conf.xml", Optional.of(existHomeDir)));
    }

    @Test
    void testConfigurationConstructorWithAbsoluteConf(@TempDir Path existHomeDir) throws Exception {
        final Path conf = existHomeDir.resolve("test-conf.xml");
        try (InputStream in = getClass().getResourceAsStream("/conf.xml")) {
            Files.copy(in, conf);
        }
        assertThatNoException().isThrownBy(() -> new Configuration(conf.toString(), Optional.of(existHomeDir)));
    }
}