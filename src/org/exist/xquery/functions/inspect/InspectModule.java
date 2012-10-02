package org.exist.xquery.functions.inspect;

import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.SourceFactory;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.exist.xquery.xqdoc.XQDocHelper;
import org.xml.sax.helpers.AttributesImpl;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Map;

public class InspectModule extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("inspect-module", InspectionModule.NAMESPACE_URI, InspectionModule.PREFIX),
            "Compiles a module from source (without importing it) and returns a sequence of function items " +
            "defined in the module.",
            new SequenceType[] {
                new FunctionParameterSequenceType("location", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                        "The location URI of the module to inspect"),
            },
            new FunctionReturnSequenceType(Type.ELEMENT, Cardinality.ZERO_OR_ONE,
                "An XML fragment describing the module and all functions contained in it."));

    private static final QName MODULE_QNAME = new QName("module");
    private static final QName VARIABLE_QNAME = new QName("variable");

    public InspectModule(XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        String moduleLoadPath = context.getModuleLoadPath();
        String location = args[0].getStringValue();
        XQueryContext tempContext = new XQueryContext(context.getBroker().getBrokerPool(), AccessContext.XMLDB);
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
                    throw new XPathException(this, "Error while loading XQuery module: " + locationUri.toString(), e);
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

        if (module == null)
            return Sequence.EMPTY_SEQUENCE;
        MemTreeBuilder builder = context.getDocumentBuilder();
        AttributesImpl attribs = new AttributesImpl();
        attribs.addAttribute("", "uri", "uri", "CDATA", module.getNamespaceURI());
        attribs.addAttribute("", "prefix", "prefix", "CDATA", module.getDefaultPrefix());
        int nodeNr = builder.startElement(MODULE_QNAME, attribs);
        XQDocHelper.parse(module);
        if (module.getDescription() != null) {
            builder.startElement(InspectFunction.DESCRIPTION_QNAME, null);
            builder.characters(module.getDescription());
            builder.endElement();
        }
        if (module.getMetadata() != null) {
            for (Map.Entry<String, String> entry: module.getMetadata().entrySet()) {
                builder.startElement(new QName(entry.getKey()), null);
                builder.characters(entry.getValue());
                builder.endElement();
            }
        }
        // variables
        for (VariableDeclaration var: module.getVariableDeclarations()) {
            attribs.clear();
            attribs.addAttribute("", "name", "name", "CDATA", var.getName());
            SequenceType type = var.getSequenceType();
            if (type != null) {
                attribs.addAttribute("", "type", "type", "CDATA", Type.getTypeName(type.getPrimaryType()));
                attribs.addAttribute("", "cardinality", "cardinality", "CDATA", Cardinality.getDescription(type.getCardinality()));
            }
            builder.startElement(VARIABLE_QNAME, attribs);
            builder.endElement();
        }
        // functions
        for (FunctionSignature sig : module.listFunctions()) {
            if (!((ExternalModuleImpl)module).isPrivate(sig)) {
                InspectFunction.generateDocs(sig, builder);
            }
        }
        builder.endElement();
        return builder.getDocument().getNode(nodeNr);
    }

    private ExternalModule compile(XQueryContext tempContext, String location, Source source) throws XPathException, IOException {
        QName qname = source.isModule();
        if (qname == null)
            return null;
        return tempContext.compileModule(qname.getLocalName(), qname.getNamespaceURI(), location, source);
    }
}
