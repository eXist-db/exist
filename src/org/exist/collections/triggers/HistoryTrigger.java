package org.exist.collections.triggers;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import org.exist.collections.Collection;
import org.exist.collections.CollectionConfigurationException;
import org.exist.dom.DocumentImpl;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;

/**
 * This collection trigger will save all old versions of documents before
 * they are overwritten or removed. The old versions are kept in the
 * 'history root' which is by default '<code>/db/history</code>', but can be 
 * changed with the parameter '<code>root</code>'.
 * You need to configure this trigger for every collection whose history you
 * want to preserve, by modifying '<code>collection.xconf</code>' such that it
 * resembles this:
 *
 * <pre>
 *   &lt;?xml version='1.0'?>
 *   &lt;collection xmlns='http://exist-db.org/collection-config/1.0'>
 *     &lt;triggers>
 *       &lt;trigger 
 *         event='update'
 *         class='org.exist.collections.triggers.HistoryTrigger'
 *       />
 *       &lt;trigger
 *         event='remove'
 *         class='org.exist.collections.triggers.HistoryTrigger'
 *       />
 *     &lt;/triggers>
 *   &lt;/collection>
 * </pre>
 *
 * @author Mark Spanbroek
 * @see org.exist.collections.triggers.Trigger
 */
public class HistoryTrigger extends FilteringTrigger implements DocumentTrigger {

    protected String root = DBBroker.ROOT_COLLECTION  + "/history";

    public void configure(DBBroker broker, Collection parent, Map parameters) 
      throws CollectionConfigurationException {
        super.configure(broker, parent, parameters);
        if (parameters.containsKey("root")) {
            root = parameters.get("root").toString();
        }
    }
    
    public void prepare(int event, DBBroker broker, Txn transaction, String documentName, DocumentImpl existingDocument) throws TriggerException{
   	  if (existingDocument == null) return;
        // retrieve the document in question
        DocumentImpl doc = (DocumentImpl) existingDocument;
      
        // construct the destination path
        String path = root + doc.getName();
        
        // construct the destination document name
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd-HH:mm:ss:SSS");
        String name = formatter.format(new Date(doc.getMetadata().getLastModified()));
        
        // create the destination document
        try {
            Collection destination = broker.getOrCreateCollection(transaction, path);
            broker.saveCollection(transaction, destination);
            broker.copyResource(transaction, doc, destination, name);
        }
        catch(PermissionDeniedException exception) {
            throw new TriggerException(exception);
        }
        catch(LockException exception) {
            throw new TriggerException(exception);
        }
    }
    
    /* (non-Javadoc)
     * @see org.exist.collections.triggers.DocumentTrigger#finish(int, org.exist.storage.DBBroker, java.lang.String, org.w3c.dom.Document)
     */
    public void finish(int event, DBBroker broker, Txn transaction, DocumentImpl document) {
    	super.finish(event, broker, transaction, document);
    }
}