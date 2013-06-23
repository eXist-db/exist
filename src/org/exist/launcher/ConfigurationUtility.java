package org.exist.launcher;

import org.apache.commons.io.FileUtils;
import org.exist.util.ConfigurationHelper;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;

public class ConfigurationUtility {

    public static boolean isFirstStart() {
        final File propFile = ConfigurationHelper.lookup("vm.properties");
        return !propFile.exists();
    }

    public static void saveProperties(Properties properties) throws IOException {
        final File propFile = ConfigurationHelper.lookup("vm.properties");
        final Properties vmProperties = LauncherWrapper.getVMProperties();
        System.out.println("system properties: " + vmProperties.toString());
        for (Map.Entry entry : vmProperties.entrySet()) {
            String userProperty = properties.getProperty(entry.getKey().toString());
            if (userProperty == null) {
                properties.setProperty(entry.getKey().toString(), entry.getValue().toString());
            }
        }

        FileOutputStream os = new FileOutputStream(propFile);
        properties.store(os, "This file contains a list of VM parameters to be passed to Java\n" +
                "when eXist is started by double clicking on start.jar (or calling\n" +
                "\"java -jar start.jar\" without parameters on the shell).");
        os.close();
    }

    public static void saveConfiguration(Properties properties) throws IOException, TransformerException {
        final File config = ConfigurationHelper.lookup("conf.xml");
        final File bakFile = new File(config.getParent(), "conf.xml.orig");
        if (!bakFile.exists()) {
            FileUtils.copyFile(config, bakFile);
        }
        final TransformerFactory factory = TransformerFactory.newInstance();
        final StreamSource xslSource = new StreamSource(ConfigurationUtility.class.getResourceAsStream("conf.xsl"));
        final Transformer transformer = factory.newTransformer(xslSource);
        final StreamSource xmlSource = new StreamSource(config);
        final StreamResult output = new StreamResult(config);

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