package org.exist.storage.serializers;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.indexing.MatchListener;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;

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

    public CustomMatchListenerFactory(DBBroker broker, Configuration config) {
        final List<String> classes = (List) config.getProperty(CONFIG_MATCH_LISTENERS);
        if (classes == null)
            {return;}
        CustomMatchListener listener;
        for (final String className : classes) {
            try {
                final Class<?> listenerClass = Class.forName(className);
                if (CustomMatchListener.class.isAssignableFrom(listenerClass)) {
                    listener = (CustomMatchListener) listenerClass.newInstance();
                    listener.setBroker(broker);
                    if (first == null) {
                        first = listener;
                        last = listener;
                    } else {
                        last.setNextInChain(listener);
                        last = listener;
                    }
                } else
                    {LOG.error("Failed to instantiate class " + listenerClass.getName() +
                            ": it is not a subclass of CustomMatchListener");}
            } catch (final Exception e) {
                LOG.error("An exception was caught while trying to instantiate a custom MatchListener: " +
                    e.getMessage(), e);
            }
        }
    }

    public MatchListener getFirst() {
        if (first != null)
            {first.reset();}
        return first;
    }

    public MatchListener getLast() {
        return last;
    }
}