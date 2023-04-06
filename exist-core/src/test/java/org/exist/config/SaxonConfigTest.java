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

package org.exist.config;

import org.exist.test.ExistEmbeddedServer;
import org.exist.util.SaxonConfigurationHolder;
import org.junit.ClassRule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SaxonConfigTest {

  @ClassRule
  public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

  @Test
  public void configFromBroker() {
    final var brokerPool = existEmbeddedServer.getBrokerPool();

    final var existConfiguration = brokerPool.getConfiguration();
    assertThat(existConfiguration.getProperty("saxon.configuration")).isEqualTo("saxon-config.xml");

    final var saxonConfigurationHolder = SaxonConfigurationHolder.getHolderForBroker(brokerPool);
    final var saxonConfiguration = saxonConfigurationHolder.getConfiguration();

    // There is no way to install EE at the test/build phase.
    // Sanity check is to confirm this does indeed return "HE" (Home Edition).
    final var saxonProcessor = saxonConfigurationHolder.getProcessor();
    assertThat(saxonProcessor.getSaxonEdition()).isEqualTo("HE");

    final var saxonConfiguration2 = brokerPool.getSaxonConfiguration();
    assertThat(saxonConfiguration2).isSameAs(saxonConfiguration);

  }
}
