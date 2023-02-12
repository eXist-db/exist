/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.xquery.functions.fn;

import com.evolvedbinary.j8fu.function.ConsumerE;
import io.lacuna.bifurcan.IEntry;
import io.lacuna.bifurcan.IMap;
import io.lacuna.bifurcan.Map;
import io.lacuna.bifurcan.Maps;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.Module;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.value.*;

import java.util.*;

import static org.exist.xquery.functions.map.MapType.newLinearMap;

/**
 * Implements fn:load-xquery-module. Creates a temporary context for the imported module, so the
 * current XQuery execution context is not polluted.
 *
 * eXist does currently not support setting external variables in a library module or defining a context
 * sequence for variables. The "context-item" and "variables" options are thus ignored.
 *
 * @author Wolfgang
 */
public class LoadXQueryModule extends BasicFunction {

    public final static FunctionSignature LOAD_XQUERY_MODULE_1 = new FunctionSignature(
            new QName("load-xquery-module", Function.BUILTIN_FUNCTION_NS, FnModule.PREFIX),
            "Provides access to the public functions and global variables of a dynamically-loaded XQuery library module.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("module-uri", Type.STRING,
                            Cardinality.EXACTLY_ONE, "The target namespace of the module")
            },
            new FunctionReturnSequenceType(
                    Type.MAP_ITEM,
                    Cardinality.EXACTLY_ONE,
                    "a map with two entries: 1) 'variables': a map with one entry for each public global variable declared in " +
                            "the library module. The key of the entry is the name of the variable, as an xs:QName value; the " +
                            "associated value is the value of the variable; 2) 'functions': a map which contains one " +
                            "entry for each public function declared in the library module, except that when two functions have " +
                            "the same name (but different arity), they share the same entry. The key of the entry is the name of the " +
                            "function(s), as an xs:QName value; the associated value is a map A. This map (A) contains one entry for each " +
                            "function with the given name; its key is the arity of the function, as an xs:integer value, and its associated " +
                            "value is the function itself, as a function item. The function can be invoked using the rules for dynamic " +
                            "function invocation.")
    );

    public final static FunctionSignature LOAD_XQUERY_MODULE_2 = new FunctionSignature(
            new QName("load-xquery-module", Function.BUILTIN_FUNCTION_NS, FnModule.PREFIX),
            "Provides access to the public functions and global variables of a dynamically-loaded XQuery library module.",
            new SequenceType[] {
                    new FunctionParameterSequenceType("module-uri", Type.STRING,
                            Cardinality.EXACTLY_ONE, "The target namespace of the module"),
                    new FunctionParameterSequenceType("options", Type.MAP_ITEM,
                            Cardinality.EXACTLY_ONE, "Options for loading the module")
            },
            new FunctionReturnSequenceType(
                    Type.MAP_ITEM,
                    Cardinality.EXACTLY_ONE,
                    "a map with two entries: 1) 'variables': a map with one entry for each public global variable declared in " +
                            "the library module. The key of the entry is the name of the variable, as an xs:QName value; the " +
                            "associated value is the value of the variable; 2) 'functions': a map which contains one " +
                            "entry for each public function declared in the library module, except that when two functions have " +
                            "the same name (but different arity), they share the same entry. The key of the entry is the name of the " +
                            "function(s), as an xs:QName value; the associated value is a map A. This map (A) contains one entry for each " +
                            "function with the given name; its key is the arity of the function, as an xs:integer value, and its associated " +
                            "value is the function itself, as a function item. The function can be invoked using the rules for dynamic " +
                            "function invocation.")
    );

    public final static StringValue OPTIONS_LOCATION_HINTS = new StringValue("location-hints");
    public final static StringValue OPTIONS_XQUERY_VERSION = new StringValue("xquery-version");
    public final static StringValue OPTIONS_VARIABLES = new StringValue("variables");
    public final static StringValue OPTIONS_CONTEXT_ITEM = new StringValue("context-item");
    public final static StringValue OPTIONS_VENDOR = new StringValue("vendor-options");

    public final static StringValue RESULT_FUNCTIONS = new StringValue("functions");
    public final static StringValue RESULT_VARIABLES = new StringValue("variables");

    public LoadXQueryModule(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        final String targetNamespace = args[0].getStringValue();
        if (targetNamespace.isEmpty()) {
            throw new XPathException(this, ErrorCodes.FOQM0001, "Target namespace must be a string with length > 0");
        }
        AnyURIValue[] locationHints = null;
        String xqVersion = getXQueryVersion(context.getXQueryVersion());
        AbstractMapType externalVars = new MapType(this, context);
        Sequence contextItem = Sequence.EMPTY_SEQUENCE;

        // evaluate options
        if (getArgumentCount() == 2) {
            final AbstractMapType map = (AbstractMapType) args[1].itemAt(0);
            final Sequence locationHintsOption = map.get(OPTIONS_LOCATION_HINTS);
            locationHints = new AnyURIValue[locationHintsOption.getItemCount()];
            for (int i = 0; i < locationHints.length; i++) {
                locationHints[i] = (AnyURIValue) locationHintsOption.itemAt(i).convertTo(Type.ANY_URI);
            }

            final Sequence versions = map.get(OPTIONS_XQUERY_VERSION);
            if (!versions.isEmpty()) {
                xqVersion = versions.itemAt(0).getStringValue();
            }

            final Sequence vars = map.get(OPTIONS_VARIABLES);
            if (!vars.isEmpty()) {
                if (vars.hasOne() && vars.itemAt(0).getType() == Type.MAP_ITEM) {
                    externalVars = (AbstractMapType) vars.itemAt(0);
                } else {
                    throw new XPathException(this, ErrorCodes.XPTY0004, "Option 'variables' must be a map");
                }
            }
            contextItem = map.get(OPTIONS_CONTEXT_ITEM);
            if (contextItem.getItemCount() > 1) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "Option 'context-item' must contain zero or one " +
                        "items");
            }
        }

        // create temporary context so main context is not polluted
        final XQueryContext tempContext = new XQueryContext(context.getBroker().getBrokerPool(), context.getProfiler());
        try {
            tempContext.setModuleLoadPath(context.getModuleLoadPath());
            setExternalVars(externalVars, tempContext::declareGlobalVariable);
            tempContext.prepareForExecution();

            Module[] loadedModules = null;
            try {
                loadedModules = tempContext.importModule(targetNamespace, null, locationHints);

            } catch (final XPathException e) {
                if (e.getErrorCode() == ErrorCodes.XQST0059) {
                    // importModule may throw exception if no location is given and module cannot be resolved
                    throw new XPathException(this, ErrorCodes.FOQM0002, "Module with URI " + targetNamespace + " not found");
                }
                throw new XPathException(this, ErrorCodes.FOQM0003, "Error found when importing module: " + e.getMessage());
            }

            // not found, raise error
            if (loadedModules == null || loadedModules.length == 0) {
                throw new XPathException(this, ErrorCodes.FOQM0002, "Module with URI " + targetNamespace + " not found");
            }

            if (!xqVersion.equals(getXQueryVersion(tempContext.getXQueryVersion()))) {
                throw new XPathException(this, ErrorCodes.FOQM0003, "Imported module has wrong XQuery version: " +
                        getXQueryVersion(tempContext.getXQueryVersion()));
            }

            final IMap<AtomicValue, Sequence> variables = newLinearMap(null);
            final IMap<AtomicValue, IMap<AtomicValue, Sequence>> functions = newLinearMap(null);

            for (final Module loadedModule : loadedModules) {
                loadedModule.setContextItem(contextItem);
                setExternalVars(externalVars, loadedModule::declareVariable);
                if (!loadedModule.isInternalModule()) {
                    // ensure variable declarations in the imported module are analyzed.
                    // unlike when using a normal import statement, this is not done automatically
                    ((ExternalModule) loadedModule).analyzeGlobalVars();
                }

                getModuleVariables(loadedModule, variables);
                getModuleFunctions(loadedModule, tempContext, functions);
            }

            final IMap<AtomicValue, Sequence> result = Map.from(io.lacuna.bifurcan.List.of(
                    new Maps.Entry<>(RESULT_FUNCTIONS, new MapType(this, context, functions.mapValues((k, v) -> (Sequence) new MapType(this, context, v.forked(), Type.INTEGER)).forked(), Type.QNAME)),
                    new Maps.Entry<>(RESULT_VARIABLES, new MapType(this, context, variables.forked(), Type.QNAME))
            ));

            return new MapType(this, context, result, Type.STRING);
        } finally {
            context.addImportedContext(tempContext);
        }
    }

    private void getModuleVariables(final Module module, final IMap<AtomicValue, Sequence> variables) throws XPathException {
        for (final Iterator<QName> i = module.getGlobalVariables(); i.hasNext(); ) {
            final QName name = i.next();
            try {
                final Variable var = module.resolveVariable(name);
                variables.put(new QNameValue(context, name), var.getValue());
            } catch (final XPathException e) {
                throw new XPathException(this, ErrorCodes.FOQM0005, "Incorrect type for external variable " + name);
            }
        }
    }

    private void getModuleFunctions(final Module module, final XQueryContext tempContext, final IMap<AtomicValue, IMap<AtomicValue, Sequence>> functions) throws XPathException {
        final ValueSequence functionSeq = new ValueSequence();
        addFunctionRefsFromModule(this, tempContext, functionSeq, module);
        for (final SequenceIterator i = functionSeq.iterate(); i.hasNext(); ) {
            final FunctionReference ref = (FunctionReference) i.nextItem();
            final FunctionSignature signature = ref.getSignature();
            final QNameValue qn = new QNameValue(context, signature.getName());
            IMap<AtomicValue, Sequence> entry = functions.get(qn, null);
            if (entry == null) {
                entry = newLinearMap(null);
                functions.put(qn, entry);
            }
            entry.put(new IntegerValue(signature.getArgumentCount()), ref);
        }
    }

    private void setExternalVars(final AbstractMapType externalVars, final ConsumerE<Variable, XPathException> setter)
            throws XPathException {
        for (final IEntry<AtomicValue, Sequence> entry: externalVars) {
            if (!Type.subTypeOf(entry.key().getType(), Type.QNAME)) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "name of external variable must be a qname: " +
                        entry.key());
            }

            final Variable var = new VariableImpl(((QNameValue) entry.key()).getQName());
            var.setValue(entry.value());
            setter.accept(var);
        }
    }

    public static void addFunctionRefsFromModule(final Expression parent, final XQueryContext tempContext,
            final ValueSequence resultSeq, final Module module) throws XPathException {
        final FunctionSignature[] signatures = module.listFunctions();
        for (final FunctionSignature signature : signatures) {
            if (!signature.isPrivate()) {
                if (module.isInternalModule()) {
                    int arity;
                    if (signature.isVariadic()) {
                        arity = signature.getArgumentTypes().length;
                    }
                    else {
                        arity = signature.getArgumentCount();
                    }
                    final FunctionDef def = ((InternalModule)module).getFunctionDef(signature.getName(), arity);
                    final XQueryAST ast = new XQueryAST();
                    ast.setLine(parent.getLine());
                    ast.setColumn(parent.getColumn());
                    final List<Expression> args = new ArrayList<>(arity);
                    for (int i = 0; i < arity; i++) {
                        args.add(new Function.Placeholder(tempContext));
                    }
                    final Function fn = Function.createFunction(tempContext, ast, module, def);
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
                        funcCall.setLocation(parent.getLine(), parent.getColumn());
                        resultSeq.add(new FunctionReference(funcCall));
                    }
                }
            }
        }
    }

    private static String getXQueryVersion(final int version) {
        return String.valueOf(version / 10) + '.' + version % 10;
    }
}
