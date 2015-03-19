package org.exist;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author aretter
 */
public class SystemProperties {

    private final static Logger LOG = LogManager.getLogger(SystemProperties.class);

    private final static SystemProperties instance = new SystemProperties();
    private Properties properties = null;

    public final static SystemProperties getInstance() {
        return instance;
    }

    private SystemProperties() {
    }

    public synchronized String getSystemProperty(String propertyName, String defaultValue) {

        if(properties == null) {
            properties = new Properties();
            InputStream is = null;
            try {
                is = SystemProperties.class.getResourceAsStream("system.properties");
                if(is != null) {
                    properties.load(is);
                }
            } catch (final IOException ioe) {
                LOG.debug("Unable to load system.properties from class loader: " +  ioe.getMessage(), ioe);
            } finally {
                if(is != null) {
                    try { is.close(); } catch(final IOException ioe) { }
                }
            }
        }

        return properties.getProperty(propertyName, defaultValue);
    }
}