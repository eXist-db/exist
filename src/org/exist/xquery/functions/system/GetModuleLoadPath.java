package org.exist.xquery.functions.system;

import org.exist.xquery.*;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.StringValue;
import org.exist.dom.QName;

public class GetModuleLoadPath extends BasicFunction {

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("module-load-path", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			"Returns the module load path from the current context. The module load path " +
            "corresponds to the location on the file system from where modules are loaded " +
            "into an XQuery. This will usually the directory from which the main XQuery was " +
            "compiled.",
			FunctionSignature.NO_ARGS,
			new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE));


    public GetModuleLoadPath(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        return new StringValue(context.getModuleLoadPath());
    }
}
