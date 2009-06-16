package org.exist.xquery.modules.onvdl;

import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.exist.xquery.modules.onvdl.ONVDLModule;
import org.exist.dom.QName;

import com.thaiopensource.relaxng.util.Driver;

public class EntryFunction extends BasicFunction {

	public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("entry", ONVDLModule.NAMESPACE_URI, ONVDLModule.PREFIX),
			"A useless example function. It just echoes the input parameters.",
			new SequenceType[] { new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE)},
			new SequenceType(Type.STRING, Cardinality.ZERO_OR_MORE));

	public EntryFunction(XQueryContext context) {
		super(context, signature);
	}

	public Sequence eval(Sequence[] args, Sequence contextSequence)
		throws XPathException {
		Driver test = new com.thaiopensource.relaxng.util.Driver();
		return (Sequence)test;
	}

}