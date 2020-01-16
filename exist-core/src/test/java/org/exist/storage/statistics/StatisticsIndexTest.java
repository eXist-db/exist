/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010-2011 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.

 */
package org.exist.storage.statistics;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.exist.storage.BrokerPool;
import org.exist.test.ExistEmbeddedServer;
import org.junit.*;

import static org.exist.storage.NativeBroker.DEFAULT_DATA_DIR;
import static org.junit.Assert.assertTrue;

public class StatisticsIndexTest {

    private static Path configFile;

    @BeforeClass
    public static void prepare() throws URISyntaxException {
        final ClassLoader loader = StatisticsIndexTest.class.getClassLoader();
        final char separator = System.getProperty("file.separator").charAt(0);
        final String packagePath = StatisticsIndexTest.class.getPackage().getName().replace('.', separator);

        configFile = Paths.get(loader.getResource(packagePath + separator + "conf.xml").toURI());
    }

    @Rule
    public final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer("db1", configFile, null, true);

    @Test
    public void statsFileExists() {
        final Path dataDir = existEmbeddedServer.getBrokerPool().getConfiguration().getProperty(BrokerPool.PROPERTY_DATA_DIR, Paths.get(DEFAULT_DATA_DIR));
        assertTrue(Files.exists(dataDir.resolve("stats.dbx")));
    }
}
