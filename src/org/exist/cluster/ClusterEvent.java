//$Id$
package org.exist.cluster;

import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.modules.XMLResource;

import java.io.Serializable;

/**
 * Created by Francesco Mondora.
 *
 * @author Francesco Mondora aka Makkina
 * @author Michele Danieli aka mdanieli
 *         Date: Aug 30, 2004
 *         Time: 3:42:17 PM
 *         Revision $Revision$
 */
public abstract class ClusterEvent implements Serializable {
    protected String collectionName;
    protected String documentName;

    public ClusterEvent(){

    }
    protected ClusterEvent(String documentName, String collectionName) {
        this.documentName = documentName;
        this.collectionName = collectionName;
    }

    public boolean equals( Object o ){
        if( o instanceof ClusterEvent ) return (this.hashCode()==((ClusterEvent) o).hashCode());
        else return false;
    }

    /**
     * Execute the current command.
     */
    public abstract void execute() throws ClusterException;


    public XMLResource getResource() throws XMLDBException {
        Collection collection = getCollection();
        return (XMLResource) collection.createResource(documentName, "XMLResource");

    }

    public org.xmldb.api.base.Collection getCollection( String cName ) throws XMLDBException {
        return DatabaseManager.getCollection("xmldb:exist://" + cName, ClusterConfiguration.getDBName(), ClusterConfiguration.getDbPassword());
    }

    public org.xmldb.api.base.Collection getCollection() throws XMLDBException {
        return getCollection( collectionName );
    }


}
