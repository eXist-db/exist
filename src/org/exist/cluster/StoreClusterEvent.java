//$Id$
package org.exist.cluster;

import org.apache.log4j.Logger;
import org.xmldb.api.base.Collection;
import org.xmldb.api.modules.XMLResource;

/**
 * Created by Francesco Mondora.
 *
 * @author Francesco Mondora aka Makkina
 *         Date: Aug 30, 2004
 *         Time: 3:45:03 PM
 *         Revision $Revision$
 */
public class StoreClusterEvent extends ClusterEvent {
    static Logger log = Logger.getLogger( StoreClusterEvent.class ) ;
    private String content;

    public StoreClusterEvent(String content, String collectionName, String documentName) {
       super( documentName, collectionName );
        this.content = content;

    }

    /**
     * Execute the current command.
     */
    public void execute() throws ClusterException {

        log.info("Storing document ");



        //if (content!=null) {
        try {
            log.info("1 storing document ");
            Collection collection = getCollection();
            log.info("2 storing document ");
            XMLResource document = (XMLResource) collection.createResource(documentName, "XMLResource");
            log.info("3 storing document ");



            document.setContent(content);
            log.info("4 storing document ");




            log.info("5 Storing document " + document.getId() + "...");
            collection.storeResource(document);

            //todo: send an ACK to the master
        } catch (Exception e) {

            log.error(e);
           throw new ClusterException( e );
        }

    }

    public String toString() {
        return "StoreClusterEvent: [content: "+ content + "] [collection:"+ collectionName+"] [documentname:"+documentName+"]";
    }


    public int hashCode() {
        int e = documentName.hashCode();
        e = e ^ content.hashCode();
        e = e ^ collectionName.hashCode();
        e = e ^ this.getClass().getName().hashCode();
        return e;
    }
}
