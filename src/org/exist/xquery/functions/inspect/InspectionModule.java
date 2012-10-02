package org.exist.xquery.functions.inspect;

import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;

public class InspectionModule extends AbstractInternalModule {

    public final static String NAMESPACE_URI = "http://exist-db.org/xquery/inspection";

    public final static String PREFIX = "inspect";

    public final static String RELEASE = "2.0";

    public final static FunctionDef[] functions = {
        new FunctionDef(InspectFunction.SIGNATURE, InspectFunction.class),
        new FunctionDef(InspectModule.signature, InspectModule.class),
        new FunctionDef(ModuleFunctions.signatures[0], ModuleFunctions.class),
        new FunctionDef(ModuleFunctions.signatures[1], ModuleFunctions.class)
    };

    public InspectionModule(Map<String, List<? extends Object>> parameters) {
        super(functions, parameters, true);
    }

    @Override
    public String getNamespaceURI() {
        return NAMESPACE_URI;
    }

    @Override
    public String getDefaultPrefix() {
        return PREFIX;
    }

    @Override
    public String getDescription() {
        return "Functions for inspecting XQuery modules and functions";
    }

    @Override
    public String getReleaseVersion() {
        return RELEASE;
    }

    /**
     * Helper method to load a module from a given location URI.
     *
     * @throws XPathException
     */
    protected static ExternalModule loadModule(XQueryContext context, String location, boolean importIntoContext) throws XPathException {
        String moduleLoadPath = context.getModuleLoadPath();
        XQueryContext tempContext;
        if (importIntoContext)
            tempContext = context;
        else
            tempContext = new XQueryContext(context.getBroker().getBrokerPool(), AccessContext.XMLDB);
        ExternalModule module = null;
        if(location.startsWith( XmldbURI.XMLDB_URI_PREFIX)
                || ((location.indexOf(':') == -1) && moduleLoadPath.startsWith(XmldbURI.XMLDB_URI_PREFIX))) {
            // Is the module source stored in the database?
            try {
                XmldbURI locationUri = XmldbURI.xmldbUriFor( location );

                if( moduleLoadPath.startsWith( XmldbURI.XMLDB_URI_PREFIX ) ) {
                    XmldbURI moduleLoadPathUri = XmldbURI.xmldbUriFor( moduleLoadPath );
                    locationUri = moduleLoadPathUri.resolveCollectionPath( locationUri );
                }

                DocumentImpl sourceDoc = null;
                try {
                    sourceDoc = tempContext.getBroker().getXMLResource(locationUri.toCollectionPathURI(), Lock.READ_LOCK);

                    if(sourceDoc == null) {
                        throw new XPathException(ErrorCodes.XQST0059, "Module location hint URI '" + location + " does not refer to anything.", new ValueSequence(new StringValue(location)));
                    }

                    if(( sourceDoc.getResourceType() != DocumentImpl.BINARY_FILE ) || !sourceDoc.getMetadata().getMimeType().equals( "application/xquery" )) {
                        throw new XPathException(ErrorCodes.XQST0059, "Module location hint URI '" + location + " does not refer to an XQuery.", new ValueSequence(new StringValue(location)));
                    }

                    DBSource moduleSource = new DBSource( tempContext.getBroker(), (BinaryDocument)sourceDoc, true );
                    tempContext.setModuleLoadPath("xmldb:exist:///db");
                    module = compile(tempContext, location, moduleSource);

                } catch(PermissionDeniedException e) {
                    throw new XPathException(ErrorCodes.XQST0059, "Permission denied to read module source from location hint URI '" + location + ".", new ValueSequence(new StringValue(location)), e);
                } catch(Exception e) {
                    throw new XPathException(ErrorCodes.XQST0059, "Error while loading XQuery module: " + locationUri.toString(), e);
                } finally {
                    if(sourceDoc != null) {
                        sourceDoc.getUpdateLock().release(Lock.READ_LOCK);
                    }
                }
            } catch(URISyntaxException e) {
                throw new XPathException(ErrorCodes.XQST0059, "Invalid module location hint URI '" + location + ".", new ValueSequence(new StringValue(location)), e);
            }
        } else {
            // No. Load from file or URL
            try {
                Source moduleSource = SourceFactory.getSource(tempContext.getBroker(), moduleLoadPath, location, true);
                module = compile(tempContext, location, moduleSource);
            } catch(MalformedURLException e) {
                throw new XPathException(ErrorCodes.XQST0059, "Invalid module location hint URI '" + location + ".", new ValueSequence(new StringValue(location)), e);
            } catch(IOException e) {
                throw new XPathException(ErrorCodes.XQST0059, "Source for module not found module location hint URI '" + location + ".", new ValueSequence(new StringValue(location)), e);
            } catch(PermissionDeniedException e) {
                throw new XPathException(ErrorCodes.XQST0059, "Permission denied to read module source from location hint URI '" + location + ".", new ValueSequence(new StringValue(location)), e);
            }
        }
        return module;
    }

    private static ExternalModule compile(XQueryContext tempContext, String location, Source source) throws XPathException, IOException {
        QName qname = source.isModule();
        if (qname == null)
            return null;
        return tempContext.compileModule(qname.getLocalName(), qname.getNamespaceURI(), location, source);
    }
}
