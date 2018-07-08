package org.exist.security.realm.ldap;

import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.security.AbstractGroup;
import org.exist.security.AbstractRealm;
import org.exist.storage.DBBroker;

/**
 * @author aretter
 */
@ConfigurationClass("group")
public class LDAPGroupImpl extends AbstractGroup {

    public LDAPGroupImpl(final AbstractRealm realm, final Configuration configuration) throws ConfigurationException {
        super(realm, configuration);
    }

    public LDAPGroupImpl(final DBBroker broker, final AbstractRealm realm, final int id, final String name) throws ConfigurationException {
        super(broker, realm, id, name, null);
    }

    LDAPGroupImpl(final AbstractRealm realm, final Configuration config, final boolean removed) throws ConfigurationException {
        this(realm, config);
        this.removed = removed;
    }

    LDAPGroupImpl(final DBBroker broker, final AbstractRealm realm, final String name) throws ConfigurationException {
        super(broker, realm, name);
    }
}