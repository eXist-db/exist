package org.exist.xquery.functions.inspect;

import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.source.FileSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.xquery.*;
import org.exist.xquery.Module;
import org.exist.xquery.functions.fn.FunOnFunctions;
import org.exist.xquery.functions.fn.LoadXQueryModule;
import org.exist.xquery.parser.XQueryAST;
import org.exist.xquery.value.*;

import javax.xml.xpath.XPath;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Paths;
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
            final XQueryContext tempContext = new XQueryContext(context.getBroker().getBrokerPool(), context.getProfiler());
            tempContext.setModuleLoadPath(context.getModuleLoadPath());

            Module module = null;

            try {
                if (isCalledAs("module-functions-by-uri")) {
                    module = tempContext.importModule(args[0].getStringValue(), null, null);
                } else {
                    final URI locationUri = ((AnyURIValue) args[0]).toURI();
                    try {
                        final Source source = SourceFactory.getSource(context.getBroker(), tempContext.getModuleLoadPath(), locationUri.toString(), false);
                        if (source != null) {
                            tempContext.setSource(source);
                        }
                    } catch (final IllegalArgumentException e) {
                        throw new XPathException(this, e.getMessage());
                    }
                    module = tempContext.importModule(null, null, args[0].getStringValue());
                }
            } catch (final IOException | PermissionDeniedException e) {
                throw new XPathException(this, e.getMessage());
            } catch (final XPathException e) {
                LOG.error("Failed to import module: " + args[0].getStringValue() + ": " + e.getMessage(), e);
                if (e.getErrorCode().equals(ErrorCodes.XPST0003)) {
                    throw new XPathException(this, e.getMessage());
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
