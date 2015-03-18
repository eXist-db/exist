package org.exist.versioning;

import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.NodeHandle;
import org.exist.dom.QName;
import org.exist.util.serializer.AttrList;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.serializers.CustomMatchListener;
import org.exist.xquery.XPathException;
import org.exist.xmldb.XmldbURI;
import org.xml.sax.SAXException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

public class VersioningFilter extends CustomMatchListener {

    private final static Logger LOG = LogManager.getLogger(VersioningFilter.class);

    public final static QName ATTR_REVISION = new QName("revision", StandardDiff.NAMESPACE, StandardDiff.PREFIX);
    public final static QName ATTR_KEY = new QName("key", StandardDiff.NAMESPACE, StandardDiff.PREFIX);
    public final static QName ATTR_PATH = new QName("path", StandardDiff.NAMESPACE, StandardDiff.PREFIX);

    private int elementStack = 0;

    public VersioningFilter() {
    }

    @Override
    public void startElement(QName qname, AttrList attribs) throws SAXException {
        if (elementStack == 0) {
            final NodeHandle node = getCurrentNode();
            if (node != null) {
                final DocumentImpl doc = node.getOwnerDocument();
                XmldbURI uri = doc.getURI();
                if (!uri.startsWith(XmldbURI.SYSTEM_COLLECTION_URI)) {
                    
                    if (doc.getCollection().getConfiguration(getBroker()).triggerRegistered(VersioningTrigger.class)) {
                        try {
                            long rev = VersioningHelper.getCurrentRevision(getBroker(), doc.getURI());
                            long time = System.currentTimeMillis();
                            String key = Long.toHexString(time) + Long.toHexString(rev);
                            attribs.addAttribute(ATTR_REVISION, rev == 0 ? "0" : Long.toString(rev));
                            attribs.addAttribute(ATTR_KEY, key);
                            attribs.addAttribute(ATTR_PATH, doc.getURI().toString());
                        } catch (XPathException e) {
                            LOG.error("Exception while retrieving versioning info: " + e.getMessage(), e);
                        } catch (IOException e) {
                            LOG.error("Exception while retrieving versioning info: " + e.getMessage(), e);
                        } catch (PermissionDeniedException e) {
                            LOG.error("Exception while retrieving versioning info: " + e.getMessage(), e);
						}
                    }
                }
            }
        }
        ++elementStack;
        nextListener.startElement(qname, attribs); 
    }

    public void endElement(QName qname) throws SAXException {
        --elementStack;
        nextListener.endElement(qname);
    }
}