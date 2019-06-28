/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2019 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.storage;

import static org.junit.Assert.assertNotNull;

import org.exist.test.ExistEmbeddedServer;
import org.junit.Rule;
import org.junit.Test;

import java.util.Properties;

/**
 * @author <a href="mailto:ohumbel@gmail.com">Otmar Humbel</a>
 */
public class BrokerPoolNoRecoveryTest {

    @Rule
    public final ExistEmbeddedServer existEmbeddedServer =
            new ExistEmbeddedServer(createConfigProperties(), true, true);

    @Test
    public void testSync_Recovery_Disabled() {
        // For this test it is sufficient to have startDb() called in ExistEmbeddedServer.
        // With disabled recovery, this used to fail with a java.util.NoSuchElementException: No value present
        assertNotNull(existEmbeddedServer.getBrokerPool()); // for Codacy alone
    }

    private static Properties createConfigProperties() {
        Properties configProperties = new Properties();
        configProperties.put(BrokerPoolConstants.PROPERTY_RECOVERY_ENABLED, Boolean.FALSE);
        return configProperties;
    }
}