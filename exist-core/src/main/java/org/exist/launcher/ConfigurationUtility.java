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

import org.exist.util.DatabaseConfigurationException;
import org.exist.util.FileUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import java.util.Properties;

import static java.nio.charset.StandardCharsets.UTF_8;

public class ConfigurationUtility {

    public static final String LAUNCHER_PROPERTIES_FILE_NAME = "launcher.properties";
    public static final String LAUNCHER_PROPERTY_MAX_MEM = "memory.max";
    public static final String LAUNCHER_PROPERTY_MIN_MEM = "memory.min";
    public static final String LAUNCHER_PROPERTY_VMOPTIONS = "vmoptions";
    public static final String LAUNCHER_PROPERTY_NEVER_INSTALL_SERVICE = "service.install.never";

    /**
     * We try to resolve any config file relative to an eXist-db
     * config file indicated by the System Property {@link org.exist.util.ConfigurationHelper#PROP_EXIST_CONFIGURATION_FILE},
     * if such a file does not exist, then we try and resolve it from the user.home or EXIST_HOME.
     *
     * @param configFileName the name/relative path of the config file to lookup
     * @param shouldExist if the file should already exist
     *
     * @return the file path (may not exist!)
     */
    public static Path lookup(final String configFileName, final boolean shouldExist) {
        return org.exist.util.ConfigurationHelper.getFromSystemProperty()
                .filter(Files::exists)
                .map(existConfigFile -> existConfigFile.resolveSibling(configFileName))
                .filter(f -> !shouldExist || Files.exists(f))
                .orElseGet(() -> org.exist.util.ConfigurationHelper.lookup(configFileName));
    }

    public static boolean isFirstStart() {
        final Path propFile = lookup(LAUNCHER_PROPERTIES_FILE_NAME, false);
         return !Files.exists(propFile);
    }

    public static Map<String, Integer> getJettyPorts() throws DatabaseConfigurationException {
        final Map<String, Integer> ports = new HashMap<>();
        final Path jettyHttpConfig = lookup("jetty/jetty-http.xml", true);
        final Path jettyHttpsConfig = lookup("jetty/jetty-ssl.xml", true);
        getJettyPorts(ports, jettyHttpConfig);
        getJettyPorts(ports, jettyHttpsConfig);
        return ports;
    }

    private static void getJettyPorts(Map<String, Integer> ports, Path jettyConfig) throws DatabaseConfigurationException {
        if (Files.exists(jettyConfig)) {
            try {
                final XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(Files
                        .newBufferedReader(jettyConfig));
                while (reader.hasNext()) {
                    final int status = reader.next();
                    if (status == XMLStreamReader.START_ELEMENT && "SystemProperty".equals(reader.getLocalName())) {
                        final String name = reader.getAttributeValue(null, "name");
                        if (name != null && ("jetty.http.port".equals(name) || "jetty.ssl.port".equals(name))) {
                            final String defaultValue = reader.getAttributeValue(null, "default");
                            if (defaultValue != null) {
                                try {
                                    ports.put(name, Integer.parseInt(defaultValue));
                                } catch(NumberFormatException e) {
                                    // skip
                                }
                            }
                        }
                    }
                }
            } catch (XMLStreamException | IOException e) {
                throw new DatabaseConfigurationException(e.getMessage(), e);
            }
        }
    }

    public static Properties loadProperties() {
        final Properties launcherProperties = new Properties();
        final java.nio.file.Path propFile = lookup(LAUNCHER_PROPERTIES_FILE_NAME, false);
        InputStream is = null;
        try {
            if (Files.isReadable(propFile)) {
                is = Files.newInputStream(propFile);
            }
            if (is == null) {
                is = Launcher.class.getResourceAsStream(LAUNCHER_PROPERTIES_FILE_NAME);
            }

            if (is != null) {
                launcherProperties.load(new InputStreamReader(is, UTF_8));
            }
        } catch (final IOException e) {
            System.err.println(LAUNCHER_PROPERTIES_FILE_NAME + " not found");
        } finally {
            if(is != null) {
                try {
                    is.close();
                } catch(final IOException ioe) {
                    ioe.printStackTrace();
                }
            }
        }
        return launcherProperties;
    }

    public static void saveProperties(final Properties properties) throws IOException {
        final Path propFile = lookup(LAUNCHER_PROPERTIES_FILE_NAME, false);
        final Properties launcherProperties = loadProperties();
        for (final String key: properties.stringPropertyNames()) {
            launcherProperties.setProperty(key, properties.getProperty(key));
        }

        System.out.println("Launcher properties: " + launcherProperties.toString());
        for (final String key: launcherProperties.stringPropertyNames()) {
            System.out.println(key + "=" + launcherProperties.getProperty(key));
        }
        System.out.println();

        try (final Writer writer = Files.newBufferedWriter(propFile)) {
            launcherProperties.store(writer, null);
        }
    }

    private static Path backupOriginal(final Path propFile) throws IOException {
        final SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        final String bakFileName = FileUtils.fileName(propFile) + ".orig." + sdf.format(Calendar.getInstance().getTime());
        final Path bakFile = propFile.resolveSibling(bakFileName);
        Files.copy(propFile, bakFile);
        return bakFile;
    }

    public static void saveConfiguration(String path, String xsl, Properties properties) throws IOException,
            TransformerException {
        final Path config = lookup(path, false);
        applyXSL(properties, config, xsl);
    }

    private static void applyXSL(Properties properties, Path config, String xsl) throws IOException,
            TransformerException {
        final Path orig = backupOriginal(config);
        final TransformerFactory factory = TransformerFactory.newInstance();
        final StreamSource xslSource = new StreamSource(ConfigurationUtility.class.getResourceAsStream(xsl));
        final Transformer transformer = factory.newTransformer(xslSource);
        final StreamSource xmlSource = new StreamSource(orig.toFile());
        final StreamResult output = new StreamResult(config.toFile());

        transformer.setErrorListener(new ErrorListener() {
            @Override
            public void warning(TransformerException exception) throws TransformerException {
                System.out.println(exception.getMessageAndLocation());
            }

            @Override
            public void error(TransformerException exception) throws TransformerException {
                System.out.println(exception.getMessageAndLocation());
            }

            @Override
            public void fatalError(TransformerException exception) throws TransformerException {
                System.out.println(exception.getMessageAndLocation());
            }
        });
        for (Map.Entry entry: properties.entrySet()) {
            transformer.setParameter(entry.getKey().toString(), entry.getValue());
        }
        transformer.transform(xmlSource, output);
    }
}