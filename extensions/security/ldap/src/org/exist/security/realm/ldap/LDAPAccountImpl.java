package org.exist.security.realm.ldap;

import org.exist.config.Configuration;
import org.exist.config.ConfigurationException;
import org.exist.config.annotation.ConfigurationClass;
import org.exist.security.Account;
import org.exist.security.Group;
import org.exist.security.PermissionDeniedException;
import org.exist.security.AbstractRealm;
import org.exist.security.internal.AccountImpl;
import org.exist.storage.DBBroker;

/**
 *
 * @author aretter
 */
@ConfigurationClass("account")
public class LDAPAccountImpl extends AccountImpl {

    public LDAPAccountImpl(AbstractRealm realm, Configuration configuration) throws ConfigurationException {
        super(realm, configuration);
    }

    public LDAPAccountImpl(DBBroker broker, AbstractRealm realm, AccountImpl from_user) throws ConfigurationException {
        super(broker, realm, from_user);
    }

    public LDAPAccountImpl(DBBroker broker, AbstractRealm realm, int id, Account from_user) throws ConfigurationException, PermissionDeniedException {
        super(broker, realm, id, from_user);
    }

    public LDAPAccountImpl(DBBroker broker, AbstractRealm realm, String name) throws ConfigurationException {
        super(broker, realm, name);
    }

    public LDAPAccountImpl(DBBroker broker, AbstractRealm realm, int id, String name, String password) throws ConfigurationException {
        super(broker, realm, id, name, password);
    }

    LDAPAccountImpl(AbstractRealm realm, Configuration config, boolean removed) throws ConfigurationException {
        super(realm, config, removed);
    }

    @Override
    public Group addGroup(Group group) throws PermissionDeniedException {
        if(group instanceof LDAPGroupImpl) {
            //TODO
            //we dont support writes to LDAP yet!
            return null;
        } else {
            //adds an LDAP User to a group from a different Realm
            return super.addGroup(group);
        }
    }

    @Override
    public Group addGroup(String name) throws PermissionDeniedException {
        Group group = getRealm().getGroup(name);

        //allow LDAP users to have groups from other realms
        if(group == null) {
           //if the group is not present in this realm, look externally
           group = getRealm().getExternalGroup(name);
        }
        return addGroup(group);
    }
}