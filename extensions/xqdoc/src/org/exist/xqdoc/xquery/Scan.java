package org.exist.xqdoc.xquery;

import org.exist.collections.Collection;
import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.source.DBSource;
import org.exist.source.Source;
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

import java.io.IOException;
import java.net.URISyntaxException;

public class Scan extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("scan", XQDocModule.NAMESPACE_URI, XQDocModule.PREFIX),
            "",
            new SequenceType[] {
                new FunctionParameterSequenceType("uri", Type.STRING, Cardinality.EXACTLY_ONE,
                    "The URI from which to load the function module")
            },
            new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE,
                "the function docs.")
        );

    public Scan(XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        Source source = null;
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
            } catch (URISyntaxException e) {
                throw new XPathException(this, "invalid module uri: " + uri + ": " + e.getMessage(), e);
            } catch (LockException e) {
                throw new XPathException(this, "internal lock error: " + e.getMessage());
            } finally {
                if (doc != null)
                    doc.getUpdateLock().release(Lock.READ_LOCK);
                if(collection != null)
                    collection.release(Lock.READ_LOCK);
            }
        }
        try {
            XQDocHelper helper = new XQDocHelper();
            String xml = helper.scan(source);
            NodeValue root = ModuleUtils.stringToXML(context, xml);
            if (root == null)
                return Sequence.EMPTY_SEQUENCE;
            return (NodeValue) ((Document) root).getDocumentElement();
        } catch (XQDocException e) {
            throw new XPathException(this, "error while scanning module: " + uri + ": " + e.getMessage(), e);
        } catch (IOException e) {
            throw new XPathException(this, "IO error while scanning module: " + uri + ": " + e.getMessage(), e);
        } catch (SAXException e) {
            throw new XPathException(this, "error while scanning module: " + uri + ": " + e.getMessage(), e);
        }
    }
}
