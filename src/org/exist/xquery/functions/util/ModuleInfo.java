/*
 * Created on 15.10.2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package org.exist.xquery.functions.util;

import java.util.Iterator;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Module;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

/**
 * @author wolf
 */
public class ModuleInfo extends BasicFunction {

	public final static FunctionSignature registeredModulesSig =
		new FunctionSignature(
			new QName("registered-modules", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns a sequence containing the namespace URIs of all modules " +
			"currently known to the system, including built in and imported modules.",
			null,
			new SequenceType(Type.STRING, Cardinality.ONE_OR_MORE));
	
	public final static FunctionSignature moduleDescriptionSig =
		new FunctionSignature(
			new QName("get-module-description", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Returns a short description of the module identified by the namespace URI.",
			new SequenceType[] { new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE) },
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE));
	
	/**
	 * @param context
	 * @param signature
	 */
	public ModuleInfo(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}

	/* (non-Javadoc)
	 * @see org.exist.xquery.BasicFunction#eval(org.exist.xquery.value.Sequence[], org.exist.xquery.value.Sequence)
	 */
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		if("get-module-description".equals(getSignature().getName().getLocalName())) {
			String uri = args[0].getStringValue();
			Module module = context.getModule(uri);
			if(module == null)
				throw new XPathException(getASTNode(), "No module found matching namespace URI: " + uri);
			return new StringValue(module.getDescription());
		} else {
			ValueSequence resultSeq = new ValueSequence();
			for(Iterator i = context.getModules(); i.hasNext(); ) {
				Module module = (Module)i.next();
				resultSeq.add(new StringValue(module.getNamespaceURI()));
			}
			return resultSeq;
		}
	}

}
