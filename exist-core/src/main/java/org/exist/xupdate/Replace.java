package org.exist.xupdate;

import java.util.Map;

import org.exist.EXistException;
import org.exist.collections.triggers.TriggerException;
import org.exist.dom.persistent.AttrImpl;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentSet;
import org.exist.dom.persistent.ElementImpl;
import org.exist.dom.persistent.StoredNode;
import org.exist.dom.persistent.TextImpl;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.storage.NotificationService;
import org.exist.storage.UpdateListener;
import org.exist.storage.txn.Txn;
import org.exist.util.LockException;
import org.exist.xquery.XPathException;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Implements xupdate:replace, an extension to the XUpdate standard.
 * The modification replaces a node and its contents. It differs from xupdate:update
 * which only replaces the contents of the node, not the node itself.
 * 
 * @author wolf
 */
public class Replace extends Modification {

	/**
     * @param broker the database broker.
     * @param docs the document working set.
     * @param selectStmt the select statement.
     * @param namespaces the namespaces.
     * @param variables the variables.
	 */
	public Replace(DBBroker broker, DocumentSet docs, String selectStmt,
			Map<String, String> namespaces, Map<String, Object> variables) {
		super(broker, docs, selectStmt, namespaces, variables);
	}
	
	@Override
	public long process(Txn transaction) throws PermissionDeniedException, LockException,
			EXistException, XPathException, TriggerException {
		final NodeList children = content;
        if (children.getLength() == 0) 
            {return 0;}
        if (children.getLength() > 1)
        	{throw new EXistException("xupdate:replace requires exactly one content node");}
        LOG.debug("processing replace ...");
        int modifications = children.getLength();
        try {
            final StoredNode ql[] = selectAndLock(transaction);
            final NotificationService notifier = broker.getBrokerPool().getNotificationService();
            Node temp;
            TextImpl text;
            AttrImpl attribute;
            ElementImpl parent;
            for (int i = 0; i < ql.length; i++) {
                final StoredNode node = ql[i];
                if (node == null) {
                    LOG.warn("select " + selectStmt + " returned empty node set");
                    continue;
                }
                final DocumentImpl doc = node.getOwnerDocument();
                if (!doc.getPermissions().validate(broker.getCurrentSubject(), Permission.WRITE)) {
                        throw new PermissionDeniedException("User '" + broker.getCurrentSubject().getName() + "' does not have permission to write to the document '" + doc.getDocumentURI() + "'!");
                }
                parent = (ElementImpl) node.getParentStoredNode();
                if (parent == null)
                    {throw new EXistException("The root element of a document can not be replaced with 'xu:replace'. " +
                        "Please consider removing the document or use 'xu:update' to just replace the children of the root.");}
                switch (node.getNodeType()) {
                    case Node.ELEMENT_NODE:
                        if (modifications == 0) {modifications = 1;}
                        temp = children.item(0);
                        parent.replaceChild(transaction, temp, node);
                        break;
                    case Node.TEXT_NODE:
                        temp = children.item(0);
                        text = new TextImpl(temp.getNodeValue());
                        modifications = 1;
                        text.setOwnerDocument(doc);
                        parent.updateChild(transaction, node, text);
                        break;
                    case Node.ATTRIBUTE_NODE:
                        final AttrImpl attr = (AttrImpl) node;
                        temp = children.item(0);
                        attribute = new AttrImpl(attr.getQName(), temp.getNodeValue(), broker.getBrokerPool().getSymbols());
                        attribute.setOwnerDocument(doc);
                        parent.updateChild(transaction, node, attribute);
                        break;
                    default:
                        throw new EXistException("unsupported node-type");
                }
                doc.getMetadata().setLastModified(System.currentTimeMillis());
                modifiedDocuments.add(doc);
                broker.storeXMLResource(transaction, doc);
                notifier.notifyUpdate(doc, UpdateListener.UPDATE);
            }
            checkFragmentation(transaction, modifiedDocuments);
        } finally {
            unlockDocuments(transaction);
        }
        return modifications;
	}

	@Override
	public String getName() {
		return XUpdateProcessor.REPLACE;
	}

}
