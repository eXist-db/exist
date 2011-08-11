package org.exist.backup.restore;

import org.apache.log4j.Logger;
import org.exist.backup.restore.listener.RestoreListener;
import org.exist.security.Permission;
import org.exist.xmldb.UserManagementService;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;

/**
 *
 * @author Adam Retter <adam@exist-db.org>
 */

class ResourceDeferredPermission extends DeferredPermission<Resource> {

    private final static Logger LOG = Logger.getLogger(ResourceDeferredPermission.class);
    
    public ResourceDeferredPermission(RestoreListener listener, Resource resource, String owner, String group, Integer mode) {
        super(listener, resource, owner, group, mode);
    }

    @Override
    public void apply() {
        try {
            UserManagementService service = (UserManagementService)getTarget().getParentCollection().getService("UserManagementService", "1.0");
            Permission permissions = service.getPermissions(getTarget());
            service.setPermissions(getTarget(), getOwner(), getGroup(), getMode(), getAces()); //persist
        } catch(XMLDBException xe) {
            String name = "unknown";
            try { name = getTarget().getId(); } catch(XMLDBException x) { LOG.error(x.getMessage(), x); }
            final String msg = "ERROR: Failed to set permissions on Document '" + name + "'.";
            LOG.error(msg, xe);
            getListener().warn(msg);
        }
    }
}
