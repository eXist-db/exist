package org.exist.xquery.functions.system;

import org.apache.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

public class GetModuleLoadPath extends BasicFunction {

    protected final static Logger logger = Logger.getLogger(GetModuleLoadPath.class);

    public final static FunctionSignature signature =
		new FunctionSignature(
			new QName("get-module-load-path", SystemModule.NAMESPACE_URI, SystemModule.PREFIX),
			"Returns the module load path from the current context. The module load path " +
            "corresponds to the location on the file system from where modules are loaded " +
            "into an XQuery. This will usually the directory from which the main XQuery was " +
            "compiled.",
			FunctionSignature.NO_ARGS,
			new FunctionParameterSequenceType("result", Type.STRING, Cardinality.EXACTLY_ONE, "The load path"));


    public GetModuleLoadPath(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        return new StringValue(context.getModuleLoadPath());
    }
}
