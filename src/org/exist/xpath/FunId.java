package org.exist.xpath;

import java.util.Iterator;

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.dom.ArraySet;
import org.exist.dom.DocumentSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.SingleNodeSet;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.util.XMLUtil;

public class FunId extends Function {

	private final static Logger LOG = Logger.getLogger(Function.class);

	/**
	 * Constructor for FunId.
	 */
	public FunId(BrokerPool pool) {
		super(pool, "id");
	}

	/**
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.dom.NodeSet, org.exist.dom.NodeProxy)
	 */
	public Value eval(
		StaticContext context,
		DocumentSet docs,
		NodeSet contextSet,
		NodeProxy contextNode) throws XPathException {
		if (getArgumentCount() < 1)
			throw new XPathException("function id requires one argument");
		if (contextNode != null)
			contextSet = new SingleNodeSet(contextNode);
		Expression arg = getArgument(0);
		Value idval = arg.eval(context, docs, contextSet);
		ArraySet result = new ArraySet(5);
		if (idval.getType() == Value.isNodeList) {
			NodeSet set = (NodeSet) idval.getNodeList();
			for (int i = 0; i < idval.getLength(); i++) {
				QName id = new QName("&" + set.get(i).getNodeValue(), "", null);
				getId(result, docs, id);
			}
		} else {
			QName id = new QName("&" + idval.getStringValue(), "", null);
			getId(result, docs, id);
		}
		return new ValueNodeSet(result);
	}

	private void getId(NodeSet result, DocumentSet docs, QName id) {
		DBBroker broker = null;
		try {
			broker = pool.get();
			NodeSet attribs = (NodeSet) broker.findElementsByTagName(docs, id);
			LOG.debug("found " + attribs.getLength() + " attributes for id " + id);
			NodeProxy n, p;
			for (Iterator i = attribs.iterator(); i.hasNext();) {
				n = (NodeProxy) i.next();
				p = new NodeProxy(n.doc, XMLUtil.getParentId(n.doc, n.gid));
				result.add(p);
			}
		} catch (EXistException e) {
			LOG.warn("error getting ID values", e);
		} finally {
			pool.release(broker);
		}
	}
	
	/**
	 * @see org.exist.xpath.Expression#returnsType()
	 */
	public int returnsType() {
		return Constants.TYPE_NODELIST;
	}

}
