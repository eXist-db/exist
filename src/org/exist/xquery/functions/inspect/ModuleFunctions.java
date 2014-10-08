package org.exist.xquery.functions.inspect;

import org.exist.dom.QName;
import org.exist.security.xacml.AccessContext;
import org.exist.xquery.*;
import org.exist.xquery.functions.fn.FunOnFunctions;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.value.*;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ModuleFunctions extends BasicFunction {
        
    public final static FunctionSignature FNS_MODULE_FUNCTIONS_CURRENT = new FunctionSignature(
        new QName("module-functions", InspectionModule.NAMESPACE_URI, InspectionModule.PREFIX),
        "Returns a sequence of function items pointing to each public function in the current module.",
        new SequenceType[] {},
        new FunctionReturnSequenceType(
            Type.FUNCTION_REFERENCE,
            Cardinality.ZERO_OR_MORE,
            "Sequence of function items containing all public functions in the current module or the empty sequence " +
            "if the module is not known in the current context.")
    );
    
    public final static FunctionSignature FNS_MODULE_FUNCTIONS_OTHER = new FunctionSignature(
        new QName("module-functions", InspectionModule.NAMESPACE_URI, InspectionModule.PREFIX),
        "Returns a sequence of function items pointing to each public function in the specified module.",
        new SequenceType[] { new FunctionParameterSequenceType("location", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The location URI of the module to be loaded.") },
        new FunctionReturnSequenceType(
            Type.FUNCTION_REFERENCE,
            Cardinality.ZERO_OR_MORE,
            "Sequence of function items containing all public functions in the module or the empty sequence " +
            "if the module is not known in the current context.")
    );
    
    public final static FunctionSignature FNS_MODULE_FUNCTIONS_OTHER_URI = new FunctionSignature(
        new QName("module-functions-by-uri", InspectionModule.NAMESPACE_URI, InspectionModule.PREFIX),
        "Returns a sequence of function items pointing to each public function in the specified module.",
        new SequenceType[] { new FunctionParameterSequenceType("uri", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The URI of the module to be loaded.") },
        new FunctionReturnSequenceType(
            Type.FUNCTION_REFERENCE,
            Cardinality.ZERO_OR_MORE,
            "Sequence of function items containing all public functions in the module or the empty sequence " +
            "if the module is not known in the current context.")
    );     

    public ModuleFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final ValueSequence list = new ValueSequence();
        if (getArgumentCount() == 1) {
            final XQueryContext tempContext = new XQueryContext(context.getBroker().getBrokerPool(), AccessContext.XMLDB);
            tempContext.setModuleLoadPath(context.getModuleLoadPath());

            Module module = null;

            try {
            if (isCalledAs("module-functions-by-uri"))
                {module = tempContext.importModule(args[0].getStringValue(), null, null);}
            else
                {module = tempContext.importModule(null, null, args[0].getStringValue());}
            } catch (final Exception e) {
                LOG.debug("Failed to import module: " + args[0].getStringValue() + ": " + e.getMessage(), e);
            }
            if (module == null)
                {return Sequence.EMPTY_SEQUENCE;}
            addFunctionRefsFromModule(tempContext, list, module);
        } else {
            addFunctionRefsFromContext(list);
        }
        return list;
    }

    private void addFunctionRefsFromModule(XQueryContext tempContext, ValueSequence resultSeq, Module module) throws XPathException {
        final FunctionSignature signatures[] = module.listFunctions();
        for (final FunctionSignature signature : signatures) {
            if (!signature.isPrivate()) {
                if (module.isInternalModule()) {
                    int arity;
                    if (signature.isOverloaded())
                        {arity = signature.getArgumentTypes().length;}
                    else
                        {arity = signature.getArgumentCount();}
                    final FunctionDef def = ((InternalModule)module).getFunctionDef(signature.getName(), arity);
                    final XQueryAST ast = new XQueryAST();
                    ast.setLine(getLine());
                    ast.setColumn(getColumn());
                    final List<Expression> args = new ArrayList<Expression>(arity);
                    for (int i = 0; i < arity; i++) {
                        args.add(new Function.Placeholder(context));
                    }
                    final Function fn = Function.createFunction(tempContext, ast, def);
                    fn.setArguments(args);
                    final InternalFunctionCall call = new InternalFunctionCall(fn);
                    final FunctionCall ref = FunctionFactory.wrap(tempContext, call);
                    resultSeq.addAll(new FunctionReference(ref));
                } else {
                    final UserDefinedFunction func = ((ExternalModule) module).getFunction(signature.getName(), signature.getArgumentCount(), tempContext);
                    // could be null if private function
                    if (func != null) {
                        // create function reference
                        final FunctionCall funcCall = new FunctionCall(tempContext, func);
                        funcCall.setLocation(getLine(), getColumn());
                        resultSeq.add(new FunctionReference(funcCall));
                    }
                }
            }
        }
    }

    private void addFunctionRefsFromContext(ValueSequence resultSeq) throws XPathException {
        for (final Iterator<UserDefinedFunction> i = context.localFunctions(); i.hasNext(); ) {
            final UserDefinedFunction f = i.next();
            final FunctionCall call =
                    FunOnFunctions.lookupFunction(this, f.getSignature().getName(), f.getSignature().getArgumentCount());
            if (call != null) {
                resultSeq.add(new FunctionReference(call));
            }
        }
    }
}
