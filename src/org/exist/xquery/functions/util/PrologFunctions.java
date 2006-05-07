package org.exist.xquery.functions.util;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class PrologFunctions extends BasicFunction {

	public final static FunctionSignature signatures[] = {
		new FunctionSignature(
			new QName("import-module", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Dynamically imports an XQuery module into the current context. The namespace " +
			"URI of the module is specified in argument $a, $b is the prefix that will be assigned " +
			"to that namespace, $c is the location of the module. The parameters have the same " +
			"meaning as in an 'import module ...' expression in the query prolog.",
			new SequenceType[] {
				new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY)),
		new FunctionSignature(
			new QName("declare-namespace", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Dynamically declares a namespace/prefix mapping for the current context. The " +
			"prefix is specified in $a, the namespace URI in $b.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY)),
		new FunctionSignature(
			new QName("declare-option", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
			"Dynamically declares a serialization option as with 'declare option'.",
			new SequenceType[] {
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE),
				new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
			},
			new SequenceType(Type.ITEM, Cardinality.EMPTY)),
	};
	
	public PrologFunctions(XQueryContext context, FunctionSignature signature) {
		super(context, signature);
	}
	
	public Sequence eval(Sequence[] args, Sequence contextSequence)
			throws XPathException {
		if (isCalledAs("declare-namespace"))
			declareNamespace(args);
		else if (isCalledAs("declare-option"))
			declareOption(args);
		else
			importModule(args);
		return Sequence.EMPTY_SEQUENCE;
	}

	private void declareNamespace(Sequence[] args) throws XPathException {
		String prefix;
		if (args[0].isEmpty())
			prefix = "";
		else
			prefix = args[0].getStringValue();
		String uri = args[1].getStringValue();
		context.declareNamespace(prefix, uri);
	}
	
	private void importModule(Sequence[] args) throws XPathException {
		String uri = args[0].getStringValue();
		String prefix = args[1].getStringValue();
		String location = args[2].getStringValue();
		context.importModule(uri, prefix, location);
	}
	
	private void declareOption(Sequence[] args) throws XPathException {
		String qname = args[0].getStringValue();
		String options = args[1].getStringValue();
		context.addPragma(qname, options);
	}
}
