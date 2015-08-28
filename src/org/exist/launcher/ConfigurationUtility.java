package org.exist.launcher;

import org.exist.util.ConfigurationHelper;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.Properties;

public class ConfigurationUtility {

    public static boolean isFirstStart() {
        final Path propFile = ConfigurationHelper.lookup("vm.properties");
        return !Files.exists(propFile);
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

    public static void saveConfiguration(Properties properties) throws IOException, TransformerException {
        final Path config = ConfigurationHelper.lookup("conf.xml");
        final Path bakFile = config.resolveSibling("conf.xml.orig");
        if (!Files.exists(bakFile)) {
            Files.copy(config, bakFile, StandardCopyOption.REPLACE_EXISTING);
        }
        final TransformerFactory factory = TransformerFactory.newInstance();
        final StreamSource xslSource = new StreamSource(ConfigurationUtility.class.getResourceAsStream("conf.xsl"));
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