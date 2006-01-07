//$Id$
package org.exist.cluster;

import org.apache.log4j.Logger;
import org.xmldb.api.base.XMLDBException;


/**
 * Created by Francesco Mondora.
 *
 * @author Francesco Mondora aka Makkina
 * @author Michele Danieli aka cinde
 *         Date: Aug 30, 2004
 *         Time: 5:46:10 PM
 *         Revision $Revision$
 */
public class RemoveClusterEvent extends ClusterEvent{
	
	
    private static final long serialVersionUID = 1L;
	static Logger  log = Logger.getLogger( RemoveClusterEvent.class );

    public RemoveClusterEvent(String documentName, String collectionName) {
        super(documentName, collectionName);
    }


    /**
     * Execute the current command.
     */
    public void execute() throws ClusterException {

        try {
            getCollection().removeResource( getResource() );
        } catch (XMLDBException e) {
            log.error(e);
          //  e.printStackTrace();
            throw new ClusterException(e);

        }
    }

    public int hashCode() {
        int e = documentName.hashCode();
        e = e ^ collectionName.hashCode();
        e = e ^ this.getClass().getName().hashCode();
        return e;
    }

}
