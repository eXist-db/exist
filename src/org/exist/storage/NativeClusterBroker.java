//$Id$
package org.exist.storage;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.cluster.ClusterCollection;
import org.exist.cluster.ClusterComunication;
import org.exist.cluster.ClusterException;
import org.exist.collections.Collection;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.xmldb.XmldbURI;

/**
 * Created by Francesco Mondora.
 *
 * @author Francesco Mondora aka Makkina
 * @author Michele Danieli aka mdanieli
 *         Date: 13-dic-2004
 *         Time: 17.12.51
 *         Revision $Revision: 5432 $
 */
public class NativeClusterBroker extends NativeBroker {

    private static final Logger LOG = Logger.getLogger(NativeClusterBroker.class);

    public NativeClusterBroker(BrokerPool pool, Configuration config) throws EXistException {
        super(pool, config);
    }


    /**
     * Get collection object. If the collection does not exist, null is
     * returned.
     *
     * Wraps for cluster the resultant collection in a ClusterCollection
     *
     * @param name Description of the Parameter
     * @return The collection value
     */
    public Collection openCollection(XmldbURI name, int lockMode) {
        Collection c= super.openCollection(name, lockMode);

        return c==null?null:new ClusterCollection(c);

    }

    public void saveCollection(Txn transaction, Collection collection) throws PermissionDeniedException,
    	IOException {
        super.saveCollection( transaction, new ClusterCollection( collection ));
    }

    /**
     * get collection object If the collection does not yet exists, it is
     * created automatically.
     *
     * Wraps for cluster the resultant collection in a ClusterCollection
     *
     * @param name the collection's name
     * @return The orCreateCollection value
     * @throws org.exist.security.PermissionDeniedException
     *          Description of the Exception
     */
    public Collection getOrCreateCollection(Txn transaction, XmldbURI name) throws PermissionDeniedException,
    	IOException {
        Collection c=   super.getOrCreateCollection(transaction, name);
        return c==null?null:new ClusterCollection(c);

    }

    public void sync(int syncEvent)
    {
        super.sync(syncEvent);
        try
        {
            ClusterComunication cm = ClusterComunication.getInstance();
            if ( cm !=null) //waiting initialize CLusterCommunication
                cm.synch();
        }
        catch (ClusterException e)
        {
            //TODO verify if DB must be declared disaligned
            LOG.warn("ERROR IN JOURNAL SYNCHRONIZATION",e);
        }
    }
}
