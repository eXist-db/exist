/*
 * Created on Feb 17, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.exist.xquery.functions.util;

import org.exist.dom.NodeProxy;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;


/**
 * @author wolf
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class DocumentId extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("document-id", ModuleImpl.NAMESPACE_URI, ModuleImpl.PREFIX),
			"Returns the internal id of the document to which the passed node belongs.",
			new SequenceType[] {
					new SequenceType(Type.NODE, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.INT, Cardinality.ZERO_OR_ONE));
	
	public DocumentId(XQueryContext context) {
		super(context, signature);
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		NodeValue node = (NodeValue)args[0].itemAt(0);
		if(node.getImplementationType() == NodeValue.PERSISTENT_NODE) {
			NodeProxy proxy = (NodeProxy)node;
			String path = proxy.doc.getFileName();
			return new IntegerValue(proxy.doc.getDocId(), Type.INT);
		}
		return Sequence.EMPTY_SEQUENCE;
	}
}
