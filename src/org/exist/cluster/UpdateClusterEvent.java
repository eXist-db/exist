//$Id$
package org.exist.cluster;

import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XUpdateQueryService;

/**
 * Created by Francesco Mondora.
 *
 * @author Francesco Mondora aka Makkina
 *         Date: 14-dic-2004
 *         Time: 15.57.25
 *         Revision $Revision$
 */
public class UpdateClusterEvent extends ClusterEvent {

    private static final long serialVersionUID = 1L;
    
	String xcommand = "";

    public UpdateClusterEvent(String documentName, String collectionName, String xupdate) {
        super(documentName, collectionName);        
        this.xcommand = xupdate;
    }


    /**
     * Execute the current command.
     */
    public void execute() throws ClusterException {

        try {            
            Collection root = this.getCollection();
            XUpdateQueryService queryService = (XUpdateQueryService) root.getService("XUpdateQueryService", "1.0");
            queryService.update(xcommand);
        } catch (XMLDBException e) {
            e.printStackTrace();
        }

    }

    public int hashCode() {
        return collectionName.hashCode() ^ xcommand.hashCode();
    }

   
}
