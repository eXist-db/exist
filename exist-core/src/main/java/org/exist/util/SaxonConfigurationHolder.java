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

import com.evolvedbinary.j8fu.lazy.AtomicLazyVal;
import net.sf.saxon.lib.Feature;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.trans.XPathException;
import org.exist.storage.BrokerPool;
import org.jline.utils.Log;

import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class SaxonConfigurationHolder {

  public final static String CONFIGURATION_ELEMENT_NAME = "saxon";
  public final static String CONFIGURATION_FILE_ATTRIBUTE = "configuration-file";
  public final static String CONFIGURATION_FILE_PROPERTY = "saxon.configuration";
  private final static String DEFAULT_SAXON_CONFIG_FILE = "saxon-config.xml";

  /**
   * Maintain a separate, singleton Saxon configuration for each broker pool
   */
  private static final Map<BrokerPool, SaxonConfigurationHolder> perBroker = new HashMap<>();

  private final BrokerPool brokerPool;
  /**
   * Load the Saxon configuration for this broker pool on demand
   */
  private final AtomicLazyVal<net.sf.saxon.Configuration> saxonConfiguration = new AtomicLazyVal<>(this::loadConfiguration);

  /**
   * Create a Saxon processor from the Saxon configuration on demand
   */
  private final AtomicLazyVal<Processor> saxonProcessor = new AtomicLazyVal<>(this::createProcessor);

  /**
   * Holds the Saxon configuration related to a broker pool
   * Configuration elements are loaded on demand,
   * initially this just holds a reference to the owning {@link BrokerPool}
   *
   * @param brokerPool which owns this Saxon configuration
   */
  private SaxonConfigurationHolder(final BrokerPool brokerPool) {
    this.brokerPool = brokerPool;
  }

  /**
   * Factory for the singleton Saxon configuration wrapper per broker pool
   * @param brokerPool for which to fetch (and if necessary create) a Saxon configuration
   *
   * @return the associated Saxon configuration wrapper
   */
  public synchronized static SaxonConfigurationHolder GetHolderForBroker(final BrokerPool brokerPool) {
    if (!perBroker.containsKey(brokerPool)) {
      perBroker.put(brokerPool, new SaxonConfigurationHolder(brokerPool));
    }
    return perBroker.get(brokerPool);
  }

  /**
   * Get (lazy loading on first access) the Saxon API's {@link net.sf.saxon.Configuration} object
   *
   * @return Saxon internal configuration object
   */
  public net.sf.saxon.Configuration getConfiguration() {
    return saxonConfiguration.get();
  }

  /**
   * Get (lazy loading on first access) the Saxon API's {@link Processor} through which Saxon operations
   * such as transformation can be effected
   *
   * @return the Saxon {@link Processor} associated with the configuration.
   */
  public Processor getProcessor() {
    return saxonProcessor.get();
  }

  /**
   * Create the Saxon {@link Processor} from the {@link net.sf.saxon.Configuration} when it is first needed
   *
   * @return a freshly created Saxon processor
   */
  private Processor createProcessor() {
    return new Processor(saxonConfiguration.get());
  }

  /**
   * Load the Saxon {@link net.sf.saxon.Configuration} from a configuration file when it is first needed;
   * if we cannot find a configuration file (and license) to give to Saxon, it may still be able to find
   * something.
   *
   * @return a freshly loaded Saxon configuration
   */
  private net.sf.saxon.Configuration loadConfiguration() {

    final var existConfiguration = brokerPool.getConfiguration();
    final var saxonConfigFile = getSaxonConfigFile(existConfiguration);
    Optional<net.sf.saxon.Configuration> saxonConfiguration = Optional.empty();
    if (saxonConfigFile.isPresent()) {
      try {
        saxonConfiguration = Optional.of(net.sf.saxon.Configuration.readConfiguration(
            new StreamSource(new FileInputStream(saxonConfigFile.get()))));
      } catch (XPathException | FileNotFoundException e) {
        Log.warn("Saxon could not read the configuration file: " + saxonConfigFile.get() +
            ", with error: " + e.getMessage(), e);
      } catch (RuntimeException runtimeException) {
        if (runtimeException.getCause() instanceof ClassNotFoundException e) {
          Log.warn("Saxon could not honour the configuration file: " + saxonConfigFile.get() +
              ", with class not found error: " + e.getMessage(), e);
        } else {
          throw runtimeException;
        }
      }
    }
    if (saxonConfiguration.isEmpty()) {
      saxonConfiguration = Optional.of(net.sf.saxon.Configuration.newConfiguration());
    }

    if (saxonConfigFile.isEmpty()) {
      Log.warn("eXist could not find any Saxon configuration:\n" +
          "No Saxon configuration file in configuration item " + CONFIGURATION_FILE_PROPERTY + "\n" +
          "No default eXist Saxon configuration file " + DEFAULT_SAXON_CONFIG_FILE);
    }

    saxonConfiguration.ifPresent(net.sf.saxon.Configuration::displayLicenseMessage);
    saxonConfiguration.ifPresent(configuration -> {
      final var sb = new StringBuilder();
      if (configuration.isLicensedFeature(net.sf.saxon.Configuration.LicenseFeature.SCHEMA_VALIDATION)) {
        sb.append(" SCHEMA_VALIDATION");
      }
      if (configuration.isLicensedFeature(net.sf.saxon.Configuration.LicenseFeature.ENTERPRISE_XSLT)) {
        sb.append(" ENTERPRISE_XSLT");
      }
      if (configuration.isLicensedFeature(net.sf.saxon.Configuration.LicenseFeature.ENTERPRISE_XQUERY)) {
        sb.append(" ENTERPRISE_XQUERY");
      }
      if (configuration.isLicensedFeature(net.sf.saxon.Configuration.LicenseFeature.PROFESSIONAL_EDITION)) {
        sb.append(" PROFESSIONAL_EDITION");
      }
      if (sb.length() == 0) {
        Log.info("Saxon - no licensed features reported.");
      } else {
        Log.info("Saxon - licensed features are" + sb + ".");
      }
    });

    return saxonConfiguration.get();
  }

  /**
   * Resolve a possibly relative configuration file;
   * if it is relative, it is relative to the current exist configuration (conf.xml)
   *
   * @param existConfiguration configuration to which this file may be relative
   * @param filename the file we are trying to resolve
   * @return the input file, if it is absolute. a file relative to conf.xml, if the input file is relative
   */
  private static File resolveConfigurationFile(final Configuration existConfiguration, final String filename) {
    final var configurationFile = new File(filename);
    if (configurationFile.isAbsolute()) {
      return configurationFile;
    }
    final var configPath = existConfiguration.getConfigFilePath();
    if (configPath.isPresent()) {
      final var resolvedPath = configPath.get().getParent().resolve(configurationFile.toPath());
      return resolvedPath.toFile();
    }
    return configurationFile;
  }

  private static Optional<File> getSaxonConfigFile(final Configuration existConfiguration) {

    if (existConfiguration.getProperty(CONFIGURATION_FILE_PROPERTY) instanceof String saxonConfigurationFile) {
      final var configurationFile = resolveConfigurationFile(existConfiguration, saxonConfigurationFile);
      if (configurationFile.canRead()) {
        return Optional.of(configurationFile);
      } else {
        Log.warn("Configuration item " + CONFIGURATION_FILE_PROPERTY + " : " + configurationFile +
            " does not refer to a readable file. Continuing search for Saxon configuration.");
      }
    }

    final var configurationFile = resolveConfigurationFile(existConfiguration, DEFAULT_SAXON_CONFIG_FILE);
    if (configurationFile.canRead()) {
      return Optional.of(configurationFile);
    }

    return Optional.empty();
  }
}
