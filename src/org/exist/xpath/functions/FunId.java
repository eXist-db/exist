package org.exist.xpath.functions;

import java.util.Iterator;

import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.XMLUtil;
import org.exist.storage.ElementValue;
import org.exist.xpath.*;
import org.exist.xpath.Cardinality;
import org.exist.xpath.Expression;
import org.exist.xpath.StaticContext;
import org.exist.xpath.XPathException;
import org.exist.xpath.value.Item;
import org.exist.xpath.value.Sequence;
import org.exist.xpath.value.SequenceIterator;
import org.exist.xpath.value.SequenceType;
import org.exist.xpath.value.Type;

public class FunId extends Function {

	public final static FunctionSignature signature =
			new FunctionSignature(
				new QName("id", BUILTIN_FUNCTION_NS),
				new SequenceType[] {
					 new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE)},
				new SequenceType(Type.ELEMENT, Cardinality.ZERO_OR_MORE));
				
	/**
	 * Constructor for FunId.
	 */
	public FunId(StaticContext context) {
		super(context, signature);
	}

	/**
	 * @see org.exist.xpath.Expression#eval(org.exist.dom.DocumentSet, org.exist.dom.NodeSet, org.exist.dom.NodeProxy)
	 */
	public Sequence eval(
		DocumentSet docs,
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if (getArgumentCount() < 1)
			throw new XPathException("function id requires one argument");
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		Expression arg = getArgument(0);
		Sequence idval = arg.eval(docs, contextSequence);
		if(idval.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		NodeSet result = new ExtArrayNodeSet();
		String nextId;
		for(SequenceIterator i = idval.iterate(); i.hasNext(); ) {
			nextId = i.nextItem().getStringValue();
			QName id = new QName(nextId, "", null);
			getId(context, result, docs, id);
		}
		return result;
	}

	private void getId(
		StaticContext context,
		NodeSet result,
		DocumentSet docs,
		QName id) {
		NodeSet attribs =
			(NodeSet) context.getBroker().findElementsByTagName(ElementValue.ATTRIBUTE_ID, docs, id);
		LOG.debug("found " + attribs.getLength() + " attributes for id " + id);
		NodeProxy n, p;
		for (Iterator i = attribs.iterator(); i.hasNext();) {
			n = (NodeProxy) i.next();
			p = new NodeProxy(n.doc, XMLUtil.getParentId(n.doc, n.gid));
			result.add(p);
		}
	}
}
