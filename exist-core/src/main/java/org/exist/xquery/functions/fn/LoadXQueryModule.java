/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */

package org.exist.xquery.functions.fn;

import com.evolvedbinary.j8fu.function.ConsumerE;
import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.Module;
import org.exist.xquery.functions.map.AbstractMapType;
import org.exist.xquery.functions.map.MapType;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.value.*;

import java.util.*;

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
                    Type.MAP,
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
                    new FunctionParameterSequenceType("options", Type.MAP,
                            Cardinality.EXACTLY_ONE, "Options for loading the module")
            },
            new FunctionReturnSequenceType(
                    Type.MAP,
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
        if (targetNamespace.length() == 0) {
            throw new XPathException(this, ErrorCodes.FOQM0001, "Target namespace must be a string with length > 0");
        }
        Sequence locationHints = Sequence.EMPTY_SEQUENCE;
        String xqVersion = getXQueryVersion(context.getXQueryVersion());
        AbstractMapType externalVars = new MapType(context);
        Sequence contextItem = Sequence.EMPTY_SEQUENCE;

        // evaluate options
        if (getArgumentCount() == 2) {
            final AbstractMapType map = (AbstractMapType) args[1].itemAt(0);
            locationHints = map.get(OPTIONS_LOCATION_HINTS);

            final Sequence versions = map.get(OPTIONS_XQUERY_VERSION);
            if (!versions.isEmpty()) {
                xqVersion = versions.itemAt(0).getStringValue();
            }

            final Sequence vars = map.get(OPTIONS_VARIABLES);
            if (!vars.isEmpty()) {
                if (vars.hasOne() && vars.itemAt(0).getType() == Type.MAP) {
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
        tempContext.setModuleLoadPath(context.getModuleLoadPath());
        setExternalVars(externalVars, tempContext::declareGlobalVariable);

        Module loadedModule = null;
        try {
            if (locationHints.isEmpty()) {
                // no location hint given, resolve from statically known modules
                loadedModule = tempContext.importModule(targetNamespace, null, null);
            } else {
                // try to resolve the module from one of the location hints
                for (final SequenceIterator i = locationHints.iterate(); i.hasNext(); ) {
                    final String location = i.nextItem().getStringValue();
                    final Module importedModule = tempContext.importModule(null, null, location);
                    if (importedModule != null && importedModule.getNamespaceURI().equals(targetNamespace)) {
                        loadedModule = importedModule;
                        break;
                    }
                }
            }
        } catch (XPathException e) {
            if (e.getErrorCode() == ErrorCodes.XQST0059) {
                // importModule may throw exception if no location is given and module cannot be resolved
                throw new XPathException(this, ErrorCodes.FOQM0002, "Module with URI " + targetNamespace + " not found");
            }
            throw new XPathException(this, ErrorCodes.FOQM0003, "Error found when importing module: " + e.getMessage());
        }

        // not found, raise error
        if (loadedModule == null) {
            throw new XPathException(this, ErrorCodes.FOQM0002, "Module with URI " + targetNamespace + " not found");
        }

        if (!xqVersion.equals(getXQueryVersion(tempContext.getXQueryVersion()))) {
            throw new XPathException(ErrorCodes.FOQM0003, "Imported module has wrong XQuery version: " +
                    getXQueryVersion(tempContext.getXQueryVersion()));
        }

        final Module module = loadedModule;
        module.setContextItem(contextItem);
        setExternalVars(externalVars, module::declareVariable);
        if (!module.isInternalModule()) {
            // ensure variable declarations in the imported module are analyzed.
            // unlike when using a normal import statement, this is not done automatically
            ((ExternalModule)module).analyzeGlobalVars();
        }

        final MapType result = new MapType(context);

        final ValueSequence functionSeq = new ValueSequence();
        addFunctionRefsFromModule(this, tempContext, functionSeq, module);
        final MapType functions = new MapType(context);
        for (final SequenceIterator i = functionSeq.iterate(); i.hasNext(); ) {
            final FunctionReference ref = (FunctionReference) i.nextItem();
            final FunctionSignature signature = ref.getSignature();
            final QNameValue qn = new QNameValue(context, signature.getName());
            final MapType entry;
            if (functions.contains(qn)) {
                entry = (MapType) functions.get(qn);
            } else {
                entry = new MapType(context);
                functions.add(qn, entry);
            }
            entry.add(new IntegerValue(signature.getArgumentCount()), ref);
        }
        result.add(RESULT_FUNCTIONS, functions);

        final MapType variables = new MapType(context);
        for (final Iterator<QName> i = module.getGlobalVariables(); i.hasNext(); ) {
            final QName name = i.next();
            try {
                final Variable var = module.resolveVariable(name);
                variables.add(new QNameValue(context, name), var.getValue());
            } catch (XPathException e) {
                throw new XPathException(this, ErrorCodes.FOQM0005, "Incorrect type for external variable " + name);
            }
        }
        result.add(RESULT_VARIABLES, variables);
        return result;
    }

    private void setExternalVars(final AbstractMapType externalVars, final ConsumerE<Variable, XPathException> setter)
            throws XPathException {
        for (final Map.Entry<AtomicValue, Sequence> entry: externalVars) {
            if (!Type.subTypeOf(entry.getKey().getType(), Type.QNAME)) {
                throw new XPathException(this, ErrorCodes.XPTY0004, "name of external variable must be a qname: " +
                        entry.getKey());
            }

            final Variable var = new VariableImpl(((QNameValue) entry.getKey()).getQName());
            var.setValue(entry.getValue());
            setter.accept(var);
        }
    }

    public static void addFunctionRefsFromModule(Expression parent, XQueryContext tempContext, ValueSequence resultSeq,
                                                 Module module)
            throws XPathException {
        final FunctionSignature signatures[] = module.listFunctions();
        for (final FunctionSignature signature : signatures) {
            if (!signature.isPrivate()) {
                if (module.isInternalModule()) {
                    int arity;
                    if (signature.isOverloaded()) {
                        arity = signature.getArgumentTypes().length;
                    }
                    else {
                        arity = signature.getArgumentCount();
                    }
                    final FunctionDef def = ((InternalModule)module).getFunctionDef(signature.getName(), arity);
                    final XQueryAST ast = new XQueryAST();
                    ast.setLine(parent.getLine());
                    ast.setColumn(parent.getColumn());
                    final List<Expression> args = new ArrayList<Expression>(arity);
                    for (int i = 0; i < arity; i++) {
                        args.add(new Function.Placeholder(tempContext));
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
                        funcCall.setLocation(parent.getLine(), parent.getColumn());
                        resultSeq.add(new FunctionReference(funcCall));
                    }
                }
            }
        }
    }

    private static String getXQueryVersion(int version) {
        return String.valueOf(version / 10) + '.' + String.valueOf(version % 10);
    }
}
