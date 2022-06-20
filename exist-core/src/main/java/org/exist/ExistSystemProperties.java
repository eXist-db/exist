package org.exist;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import com.evolvedbinary.j8fu.lazy.AtomicLazyVal;
import net.jcip.annotations.ThreadSafe;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author <a href="mailto:adam@evolvedbinary.com">Adam Retter</a>
 */
@ThreadSafe
public class ExistSystemProperties {

    private static final Logger LOG = LogManager.getLogger(ExistSystemProperties.class);
    private static final ExistSystemProperties instance = new ExistSystemProperties();

    private final AtomicLazyVal<Properties> properties = new AtomicLazyVal<>(this::load);

    public final static ExistSystemProperties getInstance() {
        return instance;
    }

    private ExistSystemProperties() {
    }

    private Properties load() {
        final Properties properties = new Properties();
        try (final InputStream is = ExistSystemProperties.class.getResourceAsStream("system.properties")) {
            if (is != null) {
                properties.load(is);
            }
        } catch (final IOException ioe) {
            LOG.error("Unable to load system.properties from class loader: " +  ioe.getMessage(), ioe);
        }
        return properties;
    }

    public String getExistSystemProperty(final String propertyName) {
        return properties.get().getProperty(propertyName);
    }

    public String getExistSystemProperty(final String propertyName, final String defaultValue) {
        return properties.get().getProperty(propertyName, defaultValue);
    }
}