package org.exist.xquery.functions;

import java.util.Iterator;
import java.util.StringTokenizer;

import org.exist.dom.DocumentSet;
import org.exist.dom.ExtArrayNodeSet;
import org.exist.dom.NodeProxy;
import org.exist.dom.NodeSet;
import org.exist.dom.QName;
import org.exist.dom.XMLUtil;
import org.exist.storage.ElementValue;
import org.exist.util.XMLChar;
import org.exist.xquery.*;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Expression;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.w3c.dom.Node;

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
	public FunId(XQueryContext context) {
		super(context, signature);
	}

	/**
	 * @see org.exist.xquery.Expression#eval(org.exist.dom.DocumentSet, org.exist.dom.NodeSet, org.exist.dom.NodeProxy)
	 */
	public Sequence eval(
		Sequence contextSequence,
		Item contextItem)
		throws XPathException {
		if (getArgumentCount() < 1)
			throw new XPathException("function id requires one argument");
		if(contextItem != null)
			contextSequence = contextItem.toSequence();
		Expression arg = getArgument(0);
		Sequence idval = arg.eval(contextSequence);
		if(idval.getLength() == 0)
			return Sequence.EMPTY_SEQUENCE;
		NodeSet result = new ExtArrayNodeSet();
		String nextId;
		DocumentSet docs;
		if(contextSequence == null || !(contextSequence instanceof NodeSet))
			docs = context.getStaticallyKnownDocuments();
		else
			docs = contextSequence.toNodeSet().getDocumentSet(); 
		for(SequenceIterator i = idval.iterate(); i.hasNext(); ) {
			nextId = i.nextItem().getStringValue();
			if(nextId.indexOf(' ') > -1) {
				// parse idrefs
				StringTokenizer tok = new StringTokenizer(nextId, " ");
				while(tok.hasMoreTokens()) {
					nextId = tok.nextToken();
					if(!XMLChar.isValidNCName(nextId))
						throw new XPathException(nextId + " is not a valid NCName");
					QName id = new QName(nextId, "", null);
					getId(result, docs, id);
				}
			} else {
				if(!XMLChar.isValidNCName(nextId))
					throw new XPathException(nextId + " is not a valid NCName");
				QName id = new QName(nextId, "", null);
				getId(result, docs, id);
			}
		}
		return result;
	}

	private void getId(
		NodeSet result,
		DocumentSet docs,
		QName id) {
		NodeSet attribs =
			(NodeSet) context.getBroker().findElementsByTagName(ElementValue.ATTRIBUTE_ID, docs, id);
		NodeProxy n, p;
		for (Iterator i = attribs.iterator(); i.hasNext();) {
			n = (NodeProxy) i.next();
			p = new NodeProxy(n.doc, XMLUtil.getParentId(n.doc, n.gid), Node.ELEMENT_NODE);
			result.add(p);
		}
	}
}
