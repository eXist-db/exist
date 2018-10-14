package org.exist.xquery.modules.exi;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.storage.serializers.Serializer;
import org.exist.validation.internal.node.NodeInputStream;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Type;

public class EXIUtils {
	
	private static final Logger LOG = LogManager.getLogger(EXIUtils.class);
	
	protected static InputStream getInputStream(Item item, XQueryContext context) throws XPathException, MalformedURLException, IOException {
        switch (item.getType()) {
            case Type.ANY_URI:
                LOG.debug("Streaming xs:anyURI");

                // anyURI provided
                String url = item.getStringValue();

                // Fix URL
                if (url.startsWith("/")) {
                    url = "xmldb:exist://" + url;
                }

                return new URL(url).openStream();
            case Type.ELEMENT:
            case Type.DOCUMENT:
                LOG.debug("Streaming element or document node");

            /*
            if (item instanceof NodeProxy) {
                NodeProxy np = (NodeProxy) item;
                String url = "xmldb:exist://" + np.getDocument().getBaseURI();
                LOG.debug("Document detected, adding URL " + url);
                streamSource.setSystemId(url);
            }
            */

                // Node provided
                Serializer serializer = context.getBroker().newSerializer();

                NodeValue node = (NodeValue) item;
                return new NodeInputStream(context.getBroker().getBrokerPool(), serializer, node);
            default:
                LOG.error("Wrong item type " + Type.getTypeName(item.getType()));
                throw new XPathException("wrong item type " + Type.getTypeName(item.getType()));
        }
    }

}