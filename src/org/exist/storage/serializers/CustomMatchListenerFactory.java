package org.exist.storage.serializers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.indexing.MatchListener;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Configures and maintains a list of {@link org.exist.storage.serializers.CustomMatchListener}.
 * There will be one CustomMatchListenerFactory for every {@link org.exist.storage.serializers.Serializer}
 * instance.
 */
public class CustomMatchListenerFactory {

    private final static Logger LOG = LogManager.getLogger(CustomMatchListenerFactory.class);

    public final static String CONFIGURATION_ELEMENT = "custom-filter";
    public final static String CONFIGURATION_ATTR_CLASS = "class";
    public final static String CONFIG_MATCH_LISTENERS = "serialization.custom-match-listeners";

    private CustomMatchListener first = null;
    private CustomMatchListener last = null;

    public CustomMatchListenerFactory(final DBBroker broker, final Configuration config) {
        this(broker, config, null);
    }

    public CustomMatchListenerFactory(final DBBroker broker, final Configuration config, final List<String> customClasses) {
        final List<String> classesAtConfig = (List<String>) config.getProperty(CONFIG_MATCH_LISTENERS);

        final Collection<String> classes;
        if (customClasses == null) {
            if (classesAtConfig == null) {
                return;
            }
            classes = classesAtConfig;
        } else {
            if (classesAtConfig == null) {
                classes = customClasses;
            } else {
                classes = new LinkedHashSet<>();

                classes.addAll(classesAtConfig);
                classes.addAll(customClasses);
            }
        }

        for (final String className : classes) {
            try {
                final Class<?> listenerClass = Class.forName(className);
                if (CustomMatchListener.class.isAssignableFrom(listenerClass)) {
                    final CustomMatchListener listener = (CustomMatchListener) listenerClass.newInstance();
                    listener.setBroker(broker);
                    if (first == null) {
                        first = listener;
                        last = listener;
                    } else {
                        last.setNextInChain(listener);
                        last = listener;
                    }
                } else {
                    LOG.error("Failed to instantiate class {}: it is not a subclass of CustomMatchListener", listenerClass.getName());
                }
            } catch (final Exception e) {
                LOG.error("An exception was caught while trying to instantiate a custom MatchListener: " + e.getMessage(), e);
            }
        }
    }

    public MatchListener getFirst() {
        if (first != null) {
            first.reset();
        }
        return first;
    }

    public MatchListener getLast() {
        return last;
    }
}