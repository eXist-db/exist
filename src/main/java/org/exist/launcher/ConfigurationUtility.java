package org.exist.launcher;

import org.apache.commons.configuration2.PropertiesConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
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
import java.io.Reader;
import java.io.Writer;
import java.nio.file.*;
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
        final Path jettyHttpConfig = ConfigurationHelper.lookup("tools/jetty/etc/jetty-http.xml");
        final Path jettyHttpsConfig = ConfigurationHelper.lookup("tools/jetty/etc/jetty-ssl.xml");
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
                        if (name != null && (name.equals("jetty.port") || name.equals("jetty.ssl.port"))) {
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

    public static void saveProperties(Properties properties) throws ConfigurationException, IOException {
        final Path propFile = ConfigurationHelper.lookup("vm.properties");
        final PropertiesConfiguration vmProperties = LauncherWrapper.getVMProperties();
        System.out.println("system properties: " + vmProperties.toString());
        for (Map.Entry entry: properties.entrySet()) {
            vmProperties.setProperty(entry.getKey().toString(), entry.getValue());
        }
        try (final Writer writer = Files.newBufferedWriter(propFile)) {
            vmProperties.write(writer);
        }
    }

    public static void saveWrapperProperties(Properties properties) throws ConfigurationException, IOException {
        final Path propFile = ConfigurationHelper.lookup("tools/yajsw/conf/wrapper.conf");
        saveOrig(propFile);
        final PropertiesConfiguration wrapperConf = new PropertiesConfiguration();
        try (final Reader reader = Files.newBufferedReader(propFile)) {
            wrapperConf.read(reader);
        }
        wrapperConf.setProperty("wrapper.java.maxmemory",
                properties.getProperty("memory.max", wrapperConf.getString("wrapper.java.maxmemory")));
        wrapperConf.setProperty("wrapper.java.initmemory",
                properties.getProperty("memory.min", wrapperConf.getString("wrapper.java.initmemory")));
        try (final Writer writer = Files.newBufferedWriter(propFile)) {
            wrapperConf.write(writer);
        }
    }

    private static Path saveOrig(Path propFile) throws IOException {
        final Path bakFile = propFile.resolveSibling(propFile.getFileName() + ".orig");
        if (!Files.exists(bakFile)) {
            Files.copy(propFile, bakFile, StandardCopyOption.REPLACE_EXISTING);
        }
        return bakFile;
    }

    public static void saveConfiguration(String path, String xsl, Properties properties) throws IOException,
            TransformerException {
        final Path config = ConfigurationHelper.lookup(path);
        applyXSL(properties, config, xsl);
    }

    private static void applyXSL(Properties properties, Path config, String xsl) throws IOException,
            TransformerException {
        final Path orig = saveOrig(config);
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