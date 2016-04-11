package org.exist.launcher;

import org.exist.util.ConfigurationHelper;
import org.exist.util.DatabaseConfigurationException;
import org.xml.sax.InputSource;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.*;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class ConfigurationUtility {

    public static boolean isFirstStart() {
        final Path propFile = ConfigurationHelper.lookup("vm.properties");
        return !Files.exists(propFile);
    }

    public static Map<String, Integer> getJettyPorts() throws DatabaseConfigurationException {
        final Map<String, Integer> ports = new HashMap<>();
        final Path jettyConfig = ConfigurationHelper.lookup("tools/jetty/etc/jetty.xml");
        if (Files.exists(jettyConfig)) {
            try {
                final XMLStreamReader reader = XMLInputFactory.newInstance().createXMLStreamReader(Files
                        .newBufferedReader(jettyConfig));
                while (reader.hasNext()) {
                    final int status = reader.next();
                    if (status == XMLStreamReader.START_ELEMENT && "SystemProperty".equals(reader.getLocalName())) {
                        final String name = reader.getAttributeValue(null, "name");
                        if (name != null && name.startsWith("jetty.port")) {
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
        return ports;
    }

    public static void saveProperties(Properties properties) throws IOException {
        final Path propFile = ConfigurationHelper.lookup("vm.properties");
        final Properties vmProperties = LauncherWrapper.getVMProperties();
        System.out.println("system properties: " + vmProperties.toString());
        for (Map.Entry entry : vmProperties.entrySet()) {
            String userProperty = properties.getProperty(entry.getKey().toString());
            if (userProperty == null) {
                properties.setProperty(entry.getKey().toString(), entry.getValue().toString());
            }
        }

        try(final OutputStream os = Files.newOutputStream(propFile)) {
            properties.store(os, "This file contains a list of VM parameters to be passed to Java\n" +
                    "when eXist is started by double clicking on start.jar (or calling\n" +
                    "\"java -jar start.jar\" without parameters on the shell).");
        }
    }

    public static void saveConfiguration(String path, String xsl, Properties properties) throws IOException,
            TransformerException {
        final Path config = ConfigurationHelper.lookup(path);
        applyXSL(properties, config, xsl);
    }

    private static void applyXSL(Properties properties, Path config, String xsl) throws IOException,
            TransformerException {
        final Path bakFile = config.resolveSibling(config.getFileName() + ".orig");
        if (!Files.exists(bakFile)) {
            Files.copy(config, bakFile, StandardCopyOption.REPLACE_EXISTING);
        }
        final TransformerFactory factory = TransformerFactory.newInstance();
        final StreamSource xslSource = new StreamSource(ConfigurationUtility.class.getResourceAsStream(xsl));
        final Transformer transformer = factory.newTransformer(xslSource);
        final StreamSource xmlSource = new StreamSource(config.toFile());
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