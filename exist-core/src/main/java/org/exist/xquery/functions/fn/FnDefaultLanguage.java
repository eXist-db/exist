package org.exist.xquery.functions.fn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.Dependency;
import org.exist.xquery.Function;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.Profiler;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

public class FnDefaultLanguage extends BasicFunction {

  protected static final Logger logger = LogManager.getLogger(FunCurrentDateTime.class);

  public final static FunctionSignature signature =
      new FunctionSignature(
          new QName("default-language", Function.BUILTIN_FUNCTION_NS),
          "Returns the xs:language that is " +
              "the value of the default language property from the dynamic context " +
              "during the evaluation of a query or transformation in which " +
              "fn:default-language() is executed.",
          null,
          new FunctionReturnSequenceType(Type.LANGUAGE,
              Cardinality.EXACTLY_ONE, "the default language " +
              "within query execution time span"));

  public FnDefaultLanguage(XQueryContext context) {
    super(context, signature);
  }

  public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
    if (context.getProfiler().isEnabled()) {
      context.getProfiler().start(this);
      context.getProfiler().message(this, Profiler.DEPENDENCIES,
          "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
      if (contextSequence != null)
      {context.getProfiler().message(this, Profiler.START_SEQUENCES,
          "CONTEXT SEQUENCE", contextSequence);}
    }
    final Sequence result = new StringValue(context.getDefaultLanguage());
    if (context.getProfiler().isEnabled())
    {context.getProfiler().end(this, "", result);}
    return result;

  }

}
