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

    public static final FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("module-functions", InspectionModule.NAMESPACE_URI, InspectionModule.PREFIX),
            "Returns a sequence of function items pointing to each public function in the current module.",
            new SequenceType[] {},
            new FunctionReturnSequenceType(
                Type.FUNCTION_REFERENCE,
                Cardinality.ZERO_OR_MORE,
                "Sequence of function items containing all public functions in the current module or the empty sequence " +
                "if the module is not known in the current context.")),
        new FunctionSignature(
            new QName("module-functions", InspectionModule.NAMESPACE_URI, InspectionModule.PREFIX),
            "Returns a sequence of function items pointing to each public function in the specified module.",
            new SequenceType[] { new FunctionParameterSequenceType("location", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The location URI of the module to be loaded.") },
            new FunctionReturnSequenceType(
                Type.FUNCTION_REFERENCE,
                Cardinality.ZERO_OR_MORE,
                "Sequence of function items containing all public functions in the module or the empty sequence " +
                "if the module is not known in the current context.")),
        new FunctionSignature(
            new QName("module-functions-by-uri", InspectionModule.NAMESPACE_URI, InspectionModule.PREFIX),
            "Returns a sequence of function items pointing to each public function in the specified module.",
            new SequenceType[] { new FunctionParameterSequenceType("uri", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The URI of the module to be loaded.") },
            new FunctionReturnSequenceType(
                Type.FUNCTION_REFERENCE,
                Cardinality.ZERO_OR_MORE,
                "Sequence of function items containing all public functions in the module or the empty sequence " +
                        "if the module is not known in the current context."))
    };

    public ModuleFunctions(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        ValueSequence list = new ValueSequence();
        if (getArgumentCount() == 1) {
            XQueryContext tempContext = new XQueryContext(context.getBroker().getBrokerPool(), AccessContext.XMLDB);
            tempContext.setModuleLoadPath(context.getModuleLoadPath());

            Module module;
            if (isCalledAs("module-functions-by-uri"))
                module = tempContext.importModule(args[0].getStringValue(), null, null);
            else
                module = tempContext.importModule(null, null, args[0].getStringValue());
            if (module == null)
                return Sequence.EMPTY_SEQUENCE;
            addFunctionRefsFromModule(tempContext, list, module);
        } else {
            addFunctionRefsFromContext(list);
        }
        return list;
    }

    private void addFunctionRefsFromModule(XQueryContext tempContext, ValueSequence resultSeq, Module module) throws XPathException {
        FunctionSignature signatures[] = module.listFunctions();
        for (FunctionSignature signature : signatures) {
            if (!signature.isPrivate()) {
                if (module.isInternalModule()) {
                    int arity;
                    if (signature.isOverloaded())
                        arity = signature.getArgumentTypes().length;
                    else
                        arity = signature.getArgumentCount();
                    FunctionDef def = ((InternalModule)module).getFunctionDef(signature.getName(), arity);
                    XQueryAST ast = new XQueryAST();
                    ast.setLine(getLine());
                    ast.setColumn(getColumn());
                    List<Expression> args = new ArrayList<Expression>(arity);
                    for (int i = 0; i < arity; i++) {
                        args.add(new Function.Placeholder(context));
                    }
                    Function fn = Function.createFunction(tempContext, ast, def);
                    fn.setArguments(args);
                    InternalFunctionCall call = new InternalFunctionCall(fn);
                    FunctionCall ref = FunctionFactory.wrap(tempContext, call);
                    resultSeq.addAll(new FunctionReference(ref));
                } else {
                    UserDefinedFunction func = ((ExternalModule) module).getFunction(signature.getName(), signature.getArgumentCount(), tempContext);
                    // could be null if private function
                    if (func != null) {
                        // create function reference
                        FunctionCall funcCall = new FunctionCall(tempContext, func);
                        funcCall.setLocation(getLine(), getColumn());
                        resultSeq.add(new FunctionReference(funcCall));
                    }
                }
            }
        }
    }

    private void addFunctionRefsFromContext(ValueSequence resultSeq) throws XPathException {
        for (Iterator<UserDefinedFunction> i = context.localFunctions(); i.hasNext(); ) {
            UserDefinedFunction f = i.next();
            FunctionCall call =
                    FunOnFunctions.lookupFunction(this, f.getSignature().getName(), f.getSignature().getArgumentCount());
            if (call != null) {
                resultSeq.add(new FunctionReference(call));
            }
        }
    }
}
