package org.exist.backup.restore;

import org.apache.log4j.Logger;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.xmldb.UserManagementService;
import org.exist.xmldb.XmldbURI;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author aretter
 */


class CollectionDeferredPermission extends DeferredPermission<Collection> {
    
    private final static Logger LOG = Logger.getLogger(CollectionDeferredPermission.class);
    
    public CollectionDeferredPermission(RestoreListener listener, Collection collection, String owner, String group, Integer mode) {
        super(listener, collection, owner, group, mode);
    }

    @Override
    public void apply() {
        try {

            UserManagementService service;
            if(getTarget().getName().equals(XmldbURI.ROOT_COLLECTION)) {
                service = (UserManagementService)getTarget().getService("UserManagementService", "1.0");
            } else {
                Collection parent = getTarget().getParentCollection();
                service = (UserManagementService)parent.getService("UserManagementService", "1.0");
            }

            service.setPermissions(getTarget(), getOwner(), getGroup(), getMode(), getAces()); //persist
        } catch (XMLDBException xe) {
            String name = "unknown";
            try { name = getTarget().getName(); } catch(XMLDBException x) { LOG.error(x.getMessage(), x); }
            final String msg = "ERROR: Failed to set permissions on Collection '" + name + "'.";
            LOG.error(msg, xe);
            getListener().warn(msg);
        }
    }
}