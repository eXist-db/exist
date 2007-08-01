//$Id$
package org.exist.cluster;

import java.io.Serializable;

import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

/**
 * Created by Francesco Mondora.
 *
 * @author Francesco Mondora aka Makkina
 *         Date: Aug 30, 2004
 *         Time: 3:42:17 PM
 *         Revision $Revision$
 */
public abstract class ClusterEvent implements Serializable {
    
    public static final int NO_EVENT = -1;

    private static final long serialVersionUID = 0L;

    protected String collectionName;
    protected String documentName;
    private int id = NO_EVENT;
    private int counter = 1;


    public ClusterEvent(){
    }
    protected ClusterEvent(String documentName, String collectionName) {
        this.documentName = documentName;
        this.collectionName = collectionName;
    }

    public String getCollectionName()
    {
        return collectionName;
    }

    public String getDocumentName()
    {
        return documentName;
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
        //todo: get the admin password
        return DatabaseManager.getCollection("xmldb:exist://" + cName, ClusterComunication.getDbaUser(), ClusterComunication.getDbaPwd());
    }

    public org.xmldb.api.base.Collection getCollection() throws XMLDBException {
        //todo: get the admin password
        return getCollection( collectionName );
    }


    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }


    public int getCounter() {
        return counter;
    }

    public void setCounter(int counter) {
        this.counter = counter;
    }
}
