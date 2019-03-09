package org.exist;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.evolvedbinary.j8fu.lazy.LazyVal;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 *
 * @author aretter
 */
public class SystemProperties {

    private static final Logger LOG = LogManager.getLogger(SystemProperties.class);
    private static final SystemProperties instance = new SystemProperties();

    private final LazyVal<Properties> properties = new LazyVal<>(this::load);

    public final static SystemProperties getInstance() {
        return instance;
    }

    private SystemProperties() {
    }

    private Properties load() {
        final Properties properties = new Properties();
        try (final InputStream is = SystemProperties.class.getResourceAsStream("system.properties")) {
            if (is != null) {
                properties.load(is);
            }
        } catch (final IOException ioe) {
            LOG.error("Unable to load system.properties from class loader: " +  ioe.getMessage(), ioe);
        }
        return properties;
    }

    public String getSystemProperty(final String propertyName) {
        return properties.get().getProperty(propertyName);
    }

    public String getSystemProperty(final String propertyName, final String defaultValue) {
        return properties.get().getProperty(propertyName, defaultValue);
    }
}