/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2020 The eXist-db Project
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xquery.functions.inspect;

import org.exist.security.PermissionDeniedException;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.xquery.*;
import org.exist.xquery.Module;
import org.exist.xquery.functions.fn.FunOnFunctions;
import org.exist.xquery.functions.fn.LoadXQueryModule;
import org.exist.xquery.value.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.functions.inspect.InspectionModule.functionSignature;

public class ModuleFunctions extends BasicFunction {

    public static final FunctionSignature FNS_MODULE_FUNCTIONS_CURRENT = functionSignature(
            "module-functions",
            "Returns a sequence of function items pointing to each public function in the current module.",
            returnsOptMany(Type.FUNCTION_REFERENCE, "Sequence of function items containing all public functions in the current module, " +
                    "or the empty sequence if the module is not known in the current context."),
            params()
    );
    
    public final static FunctionSignature FNS_MODULE_FUNCTIONS_OTHER = functionSignature(
            "module-functions",
            "Returns a sequence of function items pointing to each public function in the specified module.",
            returnsOptMany(Type.FUNCTION_REFERENCE, "Sequence of function items containing all public functions in the module, " +
                    "or the empty sequence if the module is not known in the current context."),
            param("location", Type.ANY_URI, "The location URI of the module to be loaded.")
    );
    
    public final static FunctionSignature FNS_MODULE_FUNCTIONS_OTHER_URI = functionSignature(
            "module-functions-by-uri",
            "Returns a sequence of function items pointing to each public function in the specified module.",
            returnsOptMany(Type.FUNCTION_REFERENCE, "Sequence of function items containing all public functions in the module, "
                    + "or the empty sequence if the module is not known in the current context."),
            param("uri", Type.ANY_URI, "The URI of the module to be loaded.")
    );     

    public ModuleFunctions(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final ValueSequence list = new ValueSequence();
        if (getArgumentCount() == 1) {
            final XQueryContext tempContext = new XQueryContext(context.getBroker().getBrokerPool(), context.getProfiler());
            tempContext.setModuleLoadPath(context.getModuleLoadPath());

            final String uri = ((AnyURIValue)args[0]).getStringValue();

            if (isCalledAs("module-functions")) {
                try {
                    final URI locationUri = new URI(AnyURIValue.escape(uri));
                    final Source source = SourceFactory.getSource(context.getBroker(), tempContext.getModuleLoadPath(), locationUri.toString(), false);
                    if (source != null) {
                        tempContext.setSource(source);
                    }
                } catch (final URISyntaxException e) {
                    throw new XPathException(this, ErrorCodes.XQST0046, e.getMessage());
                } catch (final IOException | PermissionDeniedException e) {
                    throw new XPathException(this, ErrorCodes.XQST0059, e.getMessage());
                }
            }

            // attempt to import the module
            Module module = null;
            try {
                module = tempContext.importModule(null, null, uri);
            } catch (final XPathException e) {
                /*
                    Error Codes from Context#importModule can be either:
                        XPST0003 - XPath/XQuery syntax error
                        XQST0033 - namespace issue: multiple bindings for the same namespace prefix
                        XQST0046 - namespace issue: invalid URI
                        XQST0059 - no module with that target namespace
                        XQST0070 - namespace issue: URI is bound to XML's namespace
                        XQST0088 - namespace issue: import namespace URI or module declaration namespace URI is zero-length
                        ERROR - other exceptional/undefined circumstance

                    According to the description of the functions for this module, of a module cannot be found at the namespace/URI
                    then an empty sequence is returned, therefore we can ignore error code XQST0059!
                 */

                if (e.getErrorCode().equals(ErrorCodes.XQST0059)) {
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Failed to import module: " + args[0].getStringValue() + ": " + e.getMessage(), e);
                    }
                    module = null;

                } else {
                    if (e.getLine() < 1) {
                        e.setLocation(this.getLine(), this.getColumn(), this.getSource());
                    }
                    throw e;
                }
            }

            if (module == null) {
                return Sequence.EMPTY_SEQUENCE;
            }
            if (!module.isInternalModule()) {
                // ensure variable declarations in the imported module are analyzed.
                // unlike when using a normal import statement, this is not done automatically
                ((ExternalModule)module).analyzeGlobalVars();
            }
            LoadXQueryModule.addFunctionRefsFromModule(this, tempContext, list, module);
        } else {
            addFunctionRefsFromContext(list);
        }

        return list;
    }

    private void addFunctionRefsFromContext(final ValueSequence resultSeq) throws XPathException {
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
