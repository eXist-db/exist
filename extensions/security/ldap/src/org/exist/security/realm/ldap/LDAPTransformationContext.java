package org.exist.security.realm.ldap;

import java.util.ArrayList;
import java.util.List;

import org.exist.config.Configurable;
import org.exist.config.Configuration;
import org.exist.config.Configurator;
import org.exist.security.realm.TransformationContext;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.config.annotation.ConfigurationFieldAsElement;

/**
 * @author aretter
 */
@ConfigurationClass("transformation")
public class LDAPTransformationContext implements TransformationContext, Configurable {

    @ConfigurationFieldAsElement("add-group")
    //protected List<String> addGroup = new ArrayList<String>();
    protected String addGroup; //TODO convert to list

    private final Configuration configuration;

    public LDAPTransformationContext(final Configuration config) {
        this.configuration = Configurator.configure(this, config);
    }

    @Override
    public List<String> getAdditionalGroups() {
        final List<String> additionalGroups = new ArrayList<>();
        additionalGroups.add(addGroup);
        return additionalGroups;
    }

    @Override
    public boolean isConfigured() {
        return (configuration != null);
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }
}