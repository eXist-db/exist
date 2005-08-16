//$Id$
package org.exist.cluster;

import org.xmldb.api.base.Collection;
import org.xmldb.api.modules.CollectionManagementService;

/**
 * Created by Francesco Mondora.
 *
 * @author Nicola Breda aka Maiale
 *         Date: 08-aug-2005
 *         Time: 09.21.12
 *         Revision $Revision$
 */
public class RemoveCollectionClusterEvent extends ClusterEvent {

    String parent;
    String collectionName;
    private static final long serialVersionUID = 0L;

    public RemoveCollectionClusterEvent(String parent, String collectionName) {
        this.parent = parent;
        this.collectionName = collectionName;
    }

    public String getCollectionName()
    {
        return this.collectionName;
    }

    public String getParent()
    {
        return this.parent;
    }
    /**
     * Execute the current command.
     */
    public void execute() throws ClusterException {
        try {
            Collection parent = getCollection(this.parent);
            CollectionManagementService mgtService =
                    (CollectionManagementService) parent.getService("CollectionManagementService", "1.0");
            mgtService.removeCollection(collectionName);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public int hashCode() {
        return this.collectionName.hashCode() ^
               this.parent.hashCode() ^
               this.getClass().getName().hashCode() ;
    }
}
