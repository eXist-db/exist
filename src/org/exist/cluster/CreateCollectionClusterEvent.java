//$Id$
package org.exist.cluster;

import org.xmldb.api.base.Collection;
import org.xmldb.api.modules.CollectionManagementService;

/**
 * Created by Francesco Mondora.
 *
 * @author Francesco Mondora aka Makkina
 *         Date: 14-dic-2004
 *         Time: 18.21.12
 *         Revision $Revision$
 */
public class CreateCollectionClusterEvent extends ClusterEvent {

    String parent;
    String collectionName;
    private static final long serialVersionUID = 0L;

    public CreateCollectionClusterEvent(String parent, String collectionName) {
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
            mgtService.createCollection(this.collectionName);

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
