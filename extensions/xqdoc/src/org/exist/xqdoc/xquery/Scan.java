package org.exist.xqdoc.xquery;

import org.apache.commons.io.output.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.security.xacml.AccessContext;
import org.exist.source.*;
import org.exist.storage.lock.Lock;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;
import org.exist.xqdoc.XQDocHelper;
import org.exist.xquery.*;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.*;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;
import org.xqdoc.conversion.XQDocException;

public class Scan extends BasicFunction {

    public final static FunctionSignature[] signatures = {
        new FunctionSignature(
            new QName("scan", XQDocModule.NAMESPACE_URI, XQDocModule.PREFIX),
            "Scan and extract function documentation from an external XQuery function module according to the" +
            "XQDoc specification. The single argument URI may either point to an XQuery module stored in the " +
            "db (URI starts with xmldb:exist:...) or a module in the file system. A file system module is " +
            "searched in the same way as if it were loaded through an \"import module\" statement. Static " +
            "mappings defined in conf.xml are searched first.",
            new SequenceType[] {
                new FunctionParameterSequenceType("uri", Type.ANY_URI, Cardinality.EXACTLY_ONE,
                    "The URI from which to load the function module")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                "the function docs.")
        ),
        new FunctionSignature(
            new QName("scan", XQDocModule.NAMESPACE_URI, XQDocModule.PREFIX),
            "Scan and extract function documentation from an external XQuery function module according to the " +
            "XQDoc specification. The two parameter version of the function expects to get the source code of " +
            "the module in the first argument and a name for the module in the second.",
            new SequenceType[] {
                new FunctionParameterSequenceType("data", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE,
                    "The base64 encoded source data of the module"),
                new FunctionParameterSequenceType("name", Type.STRING, Cardinality.EXACTLY_ONE,
                    "The name of the module")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                "the function docs.")
        )
    };

    private final static Pattern NAME_PATTERN = Pattern.compile("([^/\\.]+)\\.?[^\\.]*$");

    private final static String NORMALIZE_XQUERY = "resource:org/exist/xqdoc/xquery/normalize.xql";

    private CompiledXQuery normalizeXQuery = null;

    public Scan(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    //TODO ideally should be replaced by changing BinarySource to a streaming approach
    private byte[] binaryValueToByteArray(BinaryValue binaryValue) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        binaryValue.streamBinaryTo(baos);
        return baos.toByteArray();
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        Source source = null;
        String name;
        if (getArgumentCount() == 2) {
            
            byte data[];
            try{
                data = binaryValueToByteArray((BinaryValue)args[0].itemAt(0));
            } catch(IOException ioe) {
                throw new XPathException(ioe.getMessage(), ioe);
            }
            name = args[1].getStringValue();
            source = new BinarySource(data, true);
        } else {
            String uri = args[0].getStringValue();
            if (uri.startsWith(XmldbURI.XMLDB_URI_PREFIX)) {
                Collection collection = null;
                DocumentImpl doc = null;
                try {
                    XmldbURI resourceURI = XmldbURI.xmldbUriFor(uri);
                    collection = context.getBroker().openCollection(resourceURI.removeLastSegment(), Lock.READ_LOCK);
                    if (collection == null) {
                        LOG.warn("collection not found: " + resourceURI.getCollectionPath());
                        return Sequence.EMPTY_SEQUENCE;
                    }
                    doc = collection.getDocumentWithLock(context.getBroker(), resourceURI.lastSegment(), Lock.READ_LOCK);
                    if (doc == null)
                        return Sequence.EMPTY_SEQUENCE;
                    if (doc.getResourceType() != DocumentImpl.BINARY_FILE ||
                            !doc.getMetadata().getMimeType().equals("application/xquery")) {
                        throw new XPathException(this, "XQuery resource: " + uri + " is not an XQuery or " +
                                "declares a wrong mime-type");
                    }
                    source = new DBSource(context.getBroker(), (BinaryDocument) doc, false);
                    name = doc.getFileURI().toString();
                } catch (URISyntaxException e) {
                    throw new XPathException(this, "invalid module uri: " + uri + ": " + e.getMessage(), e);
                } catch (LockException e) {
                    throw new XPathException(this, "internal lock error: " + e.getMessage());
                } catch(PermissionDeniedException pde) {
                    throw new XPathException(this, pde.getMessage(), pde);
                } finally {
                    if (doc != null)
                        doc.getUpdateLock().release(Lock.READ_LOCK);
                    if(collection != null)
                        collection.release(Lock.READ_LOCK);
                }
            } else {
                // first check if the URI points to a registered module
                String location = context.getModuleLocation(uri);
                if (location != null)
                    uri = location;
                try {
                    source = SourceFactory.getSource(context.getBroker(), context.getModuleLoadPath(), uri, false);
                    name = extractName(uri);
                } catch (IOException e) {
                    throw new XPathException(this, "failed to read module " + uri, e);
                } catch (PermissionDeniedException e) {
                    throw new XPathException(this, "permission denied to read module " + uri, e);
                }
            }
        }
        try {
            XQDocHelper helper = new XQDocHelper();
            String xml = helper.scan(source, name);
            NodeValue root = ModuleUtils.stringToXML(context, xml);
            if (root == null)
                return Sequence.EMPTY_SEQUENCE;
            return normalize((NodeValue) ((Document) root).getDocumentElement());
        } catch (XQDocException e) {
            throw new XPathException(this, "error while scanning module: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new XPathException(this, "IO error while scanning module: " + e.getMessage(), e);
        } catch (SAXException e) {
            throw new XPathException(this, "error while scanning module: " + e.getMessage(), e);
        }
    }

    private String extractName(String uri) {
        Matcher matcher = NAME_PATTERN.matcher(uri);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return uri;
    }

    private Sequence normalize(NodeValue input) throws IOException, XPathException {
        XQuery xquery = context.getBroker().getXQueryService();
        if (normalizeXQuery == null) {
            Source source = new ClassLoaderSource(NORMALIZE_XQUERY);
            XQueryContext xc = xquery.newContext(AccessContext.INITIALIZE);
            try {
                normalizeXQuery = xquery.compile(xc, source);
            } catch(final PermissionDeniedException e) {
                throw new XPathException(this, e);
            }
        }
        
        try {
            normalizeXQuery.getContext().declareVariable("xqdoc:doc", input);
            return xquery.execute(normalizeXQuery, Sequence.EMPTY_SEQUENCE);
        } catch(final PermissionDeniedException e) {
            throw new XPathException(this, e);
        }
    }
}
