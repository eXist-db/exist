package org.exist.xquery.functions.fn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

public class FnDefaultLanguage extends BasicFunction {

    private static final Logger logger = LogManager.getLogger(FunCurrentDateTime.class);

    public static final FunctionSignature FS_DEFAULT_LANGUAGE = FunctionDSL.functionSignature(
            new QName("default-language", Function.BUILTIN_FUNCTION_NS),
            "Returns the xs:language that is " +
                    "the value of the default language property from the dynamic context " +
                    "during the evaluation of a query or transformation in which " +
                    "fn:default-language() is executed.",
            FunctionDSL.returns(Type.LANGUAGE, Cardinality.EXACTLY_ONE, "the default language within query execution time span"));

    public FnDefaultLanguage(final XQueryContext context) {
        super(context, FS_DEFAULT_LANGUAGE);
    }

    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                    "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT SEQUENCE", contextSequence);
            }
        }
        final Sequence result = new StringValue(context.getDefaultLanguage());
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }
        return result;

    }

}
