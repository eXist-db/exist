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

import net.jcip.annotations.ThreadSafe;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.trans.XPathException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.BrokerPool;

import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.util.Optional;

@ThreadSafe
public final class SaxonConfiguration {

  private final static Logger LOG = LogManager.getLogger(SaxonConfiguration.class);

  public static final String SAXON_CONFIGURATION_ELEMENT_NAME = "saxon";
  public static final String SAXON_CONFIGURATION_FILE_ATTRIBUTE = "configuration-file";
  public static final String SAXON_CONFIGURATION_FILE_PROPERTY = "saxon.configuration";
  private static final String SAXON_DEFAULT_SAXON_CONFIG_FILE = "saxon-config.xml";

  /**
   * Holds the Saxon configuration specific to a single broker pool
   */
  private final net.sf.saxon.Configuration configuration;
  private final Processor processor;

  private SaxonConfiguration(final net.sf.saxon.Configuration configuration) {
    this.configuration = configuration;
    this.processor = new Processor(configuration);
  }

  /**
   * Get the Saxon API's {@link net.sf.saxon.Configuration} object.
   *
   * @return Saxon internal configuration object
   */
  public net.sf.saxon.Configuration getConfiguration() {
    return configuration;
  }

  /**
   * Get the Saxon API's {@link Processor} through which Saxon operations
   * such as transformation can be effected.
   *
   * @return the Saxon {@link Processor} associated with the configuration.
   */
  public Processor getProcessor() {
    return processor;
  }

  /**
   * Load the Saxon {@link net.sf.saxon.Configuration} from a configuration file when it is first needed;
   * if we cannot find a configuration file (and license) to give to Saxon, it (Saxon) may still be able to find
   * something by searching in more "well-known to Saxon" locations.
   *
   * @return a freshly loaded Saxon configuration
   */
  public static SaxonConfiguration loadConfiguration(final BrokerPool brokerPool) {

    final var existConfiguration = brokerPool.getConfiguration();
    final var saxonConfigFile = getSaxonConfigFile(existConfiguration);
    Optional<net.sf.saxon.Configuration> saxonConfiguration = Optional.empty();
    if (saxonConfigFile.isPresent()) {
      saxonConfiguration = readSaxonConfigurationFile(saxonConfigFile.get());
    }
    if (saxonConfiguration.isEmpty()) {
      saxonConfiguration = Optional.of(net.sf.saxon.Configuration.newConfiguration());
    }

    if (saxonConfigFile.isEmpty()) {
      LOG.warn("eXist could not find any Saxon configuration:\n" +
          "No Saxon configuration file in configuration item " + SAXON_CONFIGURATION_FILE_PROPERTY + "\n" +
          "No default eXist Saxon configuration file " + SAXON_DEFAULT_SAXON_CONFIG_FILE);
    }

    saxonConfiguration.ifPresent(SaxonConfiguration::reportLicensedFeatures);

    return new SaxonConfiguration(saxonConfiguration.get());
  }

  static private Optional<net.sf.saxon.Configuration> readSaxonConfigurationFile(final File saxonConfigFile) {
    try {
      return Optional.of(net.sf.saxon.Configuration.readConfiguration(
          new StreamSource(new FileInputStream(saxonConfigFile))));
    } catch (XPathException | FileNotFoundException e) {
      LOG.warn("Saxon could not read the configuration file: " + saxonConfigFile +
          ", with error: " + e.getMessage(), e);
    } catch (RuntimeException runtimeException) {
      if (runtimeException.getCause() instanceof ClassNotFoundException e) {
        LOG.warn("Saxon could not honour the configuration file: " + saxonConfigFile +
            ", with class not found error: " + e.getMessage() + ". You may need to install the SaxonPE or SaxonEE JAR in eXist.");
      } else {
        throw runtimeException;
      }
    }
    return Optional.empty();
  }

  static private void reportLicensedFeatures(final net.sf.saxon.Configuration configuration) {
    configuration.displayLicenseMessage();

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
      LOG.info("Saxon - no licensed features reported.");
    } else {
      LOG.info("Saxon - licensed features are" + sb + ".");
    }
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
    if (existConfiguration.getProperty(SAXON_CONFIGURATION_FILE_PROPERTY) instanceof String saxonConfigurationFile) {
      final var configurationFile = resolveConfigurationFile(existConfiguration, saxonConfigurationFile);
      if (configurationFile.canRead()) {
        return Optional.of(configurationFile);
      } else {
        LOG.warn("Configuration item " + SAXON_CONFIGURATION_FILE_PROPERTY + " : " + configurationFile +
            " does not refer to a readable file. Continuing search for Saxon configuration.");
      }
    }

    final var configurationFile = resolveConfigurationFile(existConfiguration, SAXON_DEFAULT_SAXON_CONFIG_FILE);
    if (configurationFile.canRead()) {
      return Optional.of(configurationFile);
    }

    return Optional.empty();
  }
}
