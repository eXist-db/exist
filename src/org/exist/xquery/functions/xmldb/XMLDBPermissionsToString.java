/*
 * Created on 10.10.2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.exist.xquery.functions.xmldb;

import org.exist.dom.QName;
import org.exist.security.Permission;
import org.exist.security.PermissionFactory;
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
 */
public class XMLDBPermissionsToString extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("permissions-to-string", XMLDBModule.NAMESPACE_URI, XMLDBModule.PREFIX),
			"Format the resource or collection permissions passed as an integer " +
			"value into a string. The returned string shows the permissions following " +
			"the usual Unix conventions, i.e. all permissions set is returned as " +
			"rwurwurwu, where the first three chars are for user permissions, " +
			"followed by group and world. 'r' denotes read, 'w' write and 'u' update " +
			"permissions",
			new SequenceType[] {
					new SequenceType(Type.INTEGER, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE));
	
	/**
	 * @param context
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
		Permission perm = PermissionFactory.getPermission(value);
		return new StringValue(perm.toString());
	}

}
