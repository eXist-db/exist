/*
 * Created on 10.10.2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.exist.xquery.functions.xmldb;

import org.exist.dom.QName;
import org.exist.security.Permission;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.IntegerValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * @author wolf
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public class XMLDBPermissionsToString extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("permissions-to-string", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Returns the name of the user that holds a write lock on the document of the " +
			"specified node. If no lock is in place, the empty sequence is returned.",
			new SequenceType[] {
					new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE));
	
	/**
	 * @param context
	 * @param signature
	 */
	public XMLDBPermissionsToString(XQueryContext context) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		int value = ((IntegerValue)args[0].itemAt(0)).getInt();
		Permission perm = new Permission(value);
		return new StringValue(perm.toString());
	}

}
