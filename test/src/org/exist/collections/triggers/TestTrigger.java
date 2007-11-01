package org.exist.collections.triggers;

import org.exist.dom.DocumentImpl;
import org.exist.dom.DocumentSet;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.TransactionManager;
import org.exist.storage.txn.Txn;
import org.exist.collections.CollectionConfigurationException;
import org.exist.collections.IndexInfo;
import org.exist.xmldb.XmldbURI;
import org.exist.xupdate.XUpdateProcessor;
import org.exist.xupdate.Modification;
import org.exist.security.xacml.AccessContext;
import org.xml.sax.InputSource;

import java.util.Map;
import java.io.StringReader;

/**
 * Test trigger to check if trigger configuration is working properly.
 */
public class TestTrigger extends FilteringTrigger {

    private final static String TEMPLATE = "<?xml version=\"1.0\"?><events></events>";

    private DocumentImpl doc;

    public void configure(DBBroker broker, org.exist.collections.Collection parent, Map parameters) throws CollectionConfigurationException {
        super.configure(broker, parent, parameters);
        XmldbURI docPath = XmldbURI.create("messages.xml");
        System.out.println("TestTrigger prepares");
        this.doc = parent.getDocument(broker, docPath);
        if (this.doc == null) {
            TransactionManager transactMgr = broker.getBrokerPool().getTransactionManager();
            Txn transaction = transactMgr.beginTransaction();
            try {
                getLogger().debug("creating new file for collection contents");

                // IMPORTANT: temporarily disable triggers on the collection.
                // We would end up in infinite recursion if we don't do that
                parent.setTriggersEnabled(false);
                IndexInfo info = parent.validateXMLResource(transaction, broker, docPath, TEMPLATE);
                //TODO : unlock the collection here ?
                parent.store(transaction, broker, info, TEMPLATE, false);
                this.doc = info.getDocument();

                transactMgr.commit(transaction);
            } catch (Exception e) {
                transactMgr.abort(transaction);
                throw new CollectionConfigurationException(e.getMessage(), e);
            } finally {
                parent.setTriggersEnabled(true);
            }
        }
    }

    public void prepare(int event, DBBroker broker, Txn transaction, XmldbURI documentPath, DocumentImpl existingDocument) throws TriggerException {
        String xupdate;
        switch (event) {
            case STORE_DOCUMENT_EVENT:
                xupdate = "<?xml version=\"1.0\"?>" +
                        "<xu:modifications version=\"1.0\" xmlns:xu=\"" + XUpdateProcessor.XUPDATE_NS + "\">" +
                        "   <xu:append select='/events'>" +
                        "       <xu:element name='event'>" +
                        "           <xu:attribute name='id'>STORE</xu:attribute>" +
                        "           <xu:attribute name='collection'>" + doc.getCollection().getURI() + "</xu:attribute>" +
                        "       </xu:element>" +
                        "   </xu:append>" +
                        "</xu:modifications>";
                break;
            case REMOVE_DOCUMENT_EVENT:
                xupdate = "<?xml version=\"1.0\"?>" +
                        "<xu:modifications version=\"1.0\" xmlns:xu=\"" + XUpdateProcessor.XUPDATE_NS + "\">" +
                        "   <xu:append select='/events'>" +
                        "       <xu:element name='event'>" +
                        "           <xu:attribute name='id'>REMOVE</xu:attribute>" +
                        "           <xu:attribute name='collection'>" + doc.getCollection().getURI() + "</xu:attribute>" +
                        "       </xu:element>" +
                        "   </xu:append>" +
                        "</xu:modifications>";
                break;
            default:
                return;
        }
        DocumentSet docs = new DocumentSet();
        docs.add(doc);
        try {
            // IMPORTANT: temporarily disable triggers on the collection.
            // We would end up in infinite recursion if we don't do that
            getCollection().setTriggersEnabled(false);
            // create the XUpdate processor
            XUpdateProcessor processor = new XUpdateProcessor(broker, docs, AccessContext.TRIGGER);
            // process the XUpdate
            Modification modifications[] = processor.parse(new InputSource(new StringReader(xupdate)));
            for (int i = 0; i < modifications.length; i++)
                modifications[i].process(null);
            broker.flush();
        } catch (Exception e) {
            e.printStackTrace();
            throw new TriggerException(e.getMessage(), e);
        } finally {
            // IMPORTANT: reenable trigger processing for the collection.
            getCollection().setTriggersEnabled(true);
        }
    }
}
