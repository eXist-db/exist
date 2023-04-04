package org.exist.config;

import org.exist.test.ExistEmbeddedServer;
import org.exist.util.SaxonConfigurationHolder;
import org.junit.ClassRule;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SaxonLicenceTest {

  @ClassRule
  public static final ExistEmbeddedServer existEmbeddedServer = new ExistEmbeddedServer(true, true);

  @Test
  public void configFromBroker() {
    final var brokerPool = existEmbeddedServer.getBrokerPool();

    final var existConfiguration = brokerPool.getConfiguration();
    assertThat(existConfiguration.getProperty("saxon.configuration")).isEqualTo("saxon-config-for-exist.xml");
    assertThat(existConfiguration.getProperty("saxon.license")).isEqualTo("saxon-license-for-exist.lic");

    final var saxonConfigurationHolder = SaxonConfigurationHolder.GetHolderForBroker(brokerPool);
    final var saxonConfiguration = saxonConfigurationHolder.getConfiguration();

    final var saxonProcessor = saxonConfigurationHolder.getProcessor();

    //TODO (AP) - brokerPool.getSaxonConfiguration() needs to use SaxonConfigurationHolder.GetHolderForBroker(brokerPool)
    final var saxonConfiguration2 = brokerPool.getSaxonConfiguration();
    assertThat(saxonConfiguration2).isSameAs(saxonConfiguration);

  }
}
