package org.exist.xupdate;

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.storage.BrokerPool;
import org.exist.util.XMLUtil;
import org.w3c.dom.Node;

/**
 * Remove.java
 * 
 * @author Wolfgang Meier
 */
public class Remove extends Modification {

    private final static Logger LOG = Logger.getLogger(Remove.class);
    
	/**
	 * Constructor for Remove.
	 * @param pool
	 * @param user
	 * @param selectStmt
	 */
	public Remove(BrokerPool pool, User user, String selectStmt) {
		super(pool, user, selectStmt);
	}

	/**
	 * @see org.exist.xupdate.Modification#process(org.exist.dom.DocumentSet)
	 */
	public long process(DocumentSet docs)
		throws PermissionDeniedException, EXistException {
		System.out.println(XMLUtil.dump(content));
		NodeSet qr = select(docs);
		LOG.debug("select found " + qr.getLength() + " nodes for remove");
		NodeProxy proxy;
		Node node, parent;
		for (Iterator i = qr.iterator(); i.hasNext();) {
			proxy = (NodeProxy) i.next();
			if (!proxy
				.doc
				.getCollection()
				.getPermissions()
				.validate(user, Permission.UPDATE))
				throw new PermissionDeniedException(
					"write access to collection denied; user="
						+ user.getName());
			if (!proxy.doc.getPermissions().validate(user, Permission.UPDATE))
				throw new PermissionDeniedException("permission to remove document denied");
			node = proxy.getNode();
            parent = node.getParentNode();
            if(parent.getNodeType() != Node.ELEMENT_NODE) {
                LOG.warn("cannot remove the root node");
            } else
                parent.removeChild(node);
		}
		return qr.getLength();
	}

	/**
	 * @see org.exist.xupdate.Modification#getName()
	 */
	public String getName() {
		return "remove";
	}

}
