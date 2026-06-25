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
package org.exist.xquery.functions.validation;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;

import javax.xml.transform.stream.StreamSource;

import com.evolvedbinary.j8fu.function.ConsumerE;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.persistent.NodeProxy;
import org.exist.dom.memtree.MemTreeBuilder;
import org.exist.dom.memtree.NodeImpl;
import org.exist.resolver.ResolverFactory;
import org.exist.security.Subject;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.serializers.Serializer;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.exist.util.Configuration;
import org.exist.util.XMLReaderObjectFactory;
import org.exist.validation.ValidationReport;
import org.exist.validation.ValidationReportItem;
import org.exist.validation.internal.node.NodeInputStream;
import org.exist.validation.resolver.SearchResourceResolver;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryDocument;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.JavaObjectValue;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceIterator;
import org.exist.xquery.value.Type;

import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.InputSource;
import org.xml.sax.helpers.AttributesImpl;
import org.xmlresolver.Resolver;

/**
 *  Shared methods for validation functions.
 *
 * @author dizzzz
 */
public class Shared {

    private final static Logger LOG = LogManager.getLogger(Shared.class);
    public final static String simplereportText = "true() if the " +
            "document is valid and no single problem occured, false() for " +
            "all other conditions. For detailed validation information " +
            "use the corresponding -report() function.";
    public final static String xmlreportText = "a validation report.";

    /**
     *  Get input stream for specified resource
     * @param s The item
     * @param context Xquery context
     * @return Inputstream containing the item
     * @throws XPathException An error occurred.
     * @throws IOException An I/O error occurred.
     */
    public static InputStream getInputStream(Item s, XQueryContext context) throws XPathException, IOException {
        final StreamSource streamSource = getStreamSource(s, context);
        return streamSource.getInputStream();
    }

    /**
     *  Get stream source for specified resource, containing InputStream and
     * location. Used by @see Jaxv.
     * @param s The sequence
     * @param context xquery context
     * @return Streamsources
     * @throws XPathException An error occurred.
     * @throws IOException An I/O error occurred.
     */
    public static StreamSource[] getStreamSource(Sequence s, XQueryContext context) throws XPathException, IOException {

        final ArrayList<StreamSource> sources = new ArrayList<>();

        final SequenceIterator i = s.iterate();

        while (i.hasNext()) {
            final Item next = i.nextItem();

            final StreamSource streamsource = getStreamSource(next, context);
            sources.add(streamsource);
        }

        StreamSource[] returnSources = new StreamSource[sources.size()];
        returnSources = sources.toArray(returnSources);
        return returnSources;
    }

    public static StreamSource getStreamSource(Item item, XQueryContext context) throws XPathException, IOException {

        final StreamSource streamSource = new StreamSource();
        if (item.getType() == Type.JAVA_OBJECT) {
            LOG.debug("Streaming Java object");

            final Object obj = ((JavaObjectValue) item).getObject();
            if (!(obj instanceof File inputFile)) {
                throw new XPathException(item, "Passed java object should be a File");
            }

            final InputStream is = new FileInputStream(inputFile);
            streamSource.setInputStream(is);
            streamSource.setSystemId(inputFile.toURI().toURL().toString());

        } else if (item.getType() == Type.ANY_URI) {
            LOG.debug("Streaming xs:anyURI");

            // anyURI provided
            String url = item.getStringValue();

            // Fix URL
            if (url.startsWith("/")) {
                url = "xmldb:exist://" + url;
            }

            final InputStream is = new URL(url).openStream();
            streamSource.setInputStream(is);
            streamSource.setSystemId(url);

        } else if (item.getType() == Type.ELEMENT || item.getType() == Type.DOCUMENT) {
            LOG.debug("Streaming element or document node");

            if (item instanceof NodeProxy np) {
                final String url = "xmldb:exist://" + np.getOwnerDocument().getBaseURI();
                LOG.debug("Document detected, adding URL {}", url);
                streamSource.setSystemId(url);
            }

            // Node provided

            final DBBroker broker = context.getBroker();
            final ConsumerE<ConsumerE<Serializer, IOException>, IOException> withSerializerFn = fn -> {
                final Serializer serializer = broker.borrowSerializer();
                try {
                    fn.accept(serializer);
                } finally {
                    broker.returnSerializer(serializer);
                }
            };

            final NodeValue node = (NodeValue) item;
            final InputStream is = new NodeInputStream(context.getBroker().getBrokerPool(), withSerializerFn, node);
            streamSource.setInputStream(is);

        } else if (item.getType() == Type.BASE64_BINARY || item.getType() == Type.HEX_BINARY) {
            LOG.debug("Streaming base64 binary");

            final BinaryValue binary = (BinaryValue) item;
            
            final byte[] data = binary.toJavaObject(byte[].class);
            final InputStream is = new UnsynchronizedByteArrayInputStream(data);
            streamSource.setInputStream(is);

            //TODO consider using BinaryValue.getInputStream()

            if (item instanceof Base64BinaryDocument b64doc) {
                final String url = "xmldb:exist://" + b64doc.getUrl();
                LOG.debug("Base64BinaryDocument detected, adding URL {}", url);
                streamSource.setSystemId(url);
            }

        } else {
            LOG.error("Wrong item type {}", Type.getTypeName(item.getType()));
            throw new XPathException(item, "wrong item type " + Type.getTypeName(item.getType()));
        }

        return streamSource;
    }

    /**
     *  Get input source for item. Used by @see Jing.
     *
     *  @param s The item
     *  @param context xquery context
     *  @return Inputsource
     *  @throws XPathException An error occurred.
     *  @throws IOException An I/O error occurred.
     */
    public static InputSource getInputSource(Item s, XQueryContext context) throws XPathException, IOException {

        final StreamSource streamSource = getStreamSource(s, context);

        final InputSource inputSource = new InputSource();
        inputSource.setByteStream(streamSource.getInputStream());
        inputSource.setSystemId(streamSource.getSystemId());

        return inputSource;

    }

    public static StreamSource getStreamSource(InputSource in) throws XPathException, IOException {

        final StreamSource streamSource = new StreamSource();
        streamSource.setInputStream(in.getByteStream());
        streamSource.setSystemId(in.getSystemId());

        return streamSource;
    }

    /**
     *  Get URL value of item.
     * @param item Item
     * @return URL of item
     * @throws XPathException Item has no URL.
     */

    public static String getUrl(Item item) throws XPathException {

        String url = null;

        if (item.getType() == Type.ANY_URI /*|| item.getType() != Type.STRING */) {
            LOG.debug("Converting anyURI");
            url = item.getStringValue();

        } else if (item.getType() == Type.DOCUMENT || item.getType() == Type.NODE) {

            LOG.debug("Retreiving URL from (document) node");

            if (item instanceof NodeProxy np) {
                url = np.getOwnerDocument().getBaseURI();
                LOG.debug("Document detected, adding URL {}", url);
            }

        }

        if(url==null) {
            throw new XPathException(item, "Parameter should be of type xs:anyURI or document.");
        }
        
        if (url.startsWith("/")) {
            url = "xmldb:exist://" + url;
        }

        return url;
    }

    /**
     * Get URL values of sequence items.
     *
     * @param s Sequence
     * @return URLs of items in sequence
     * @throws XPathException Thrown when an item does not have an associated URL.
     */
    public static String[] getUrls(Sequence s) throws XPathException {

        final ArrayList<String> urls = new ArrayList<>();

        final SequenceIterator i = s.iterate();

        while (i.hasNext()) {
            final Item next = i.nextItem();

            final String url = getUrl(next);

            urls.add(url);
        }

        String[] returnUrls = new String[urls.size()];
        returnUrls = urls.toArray(returnUrls);

        return returnUrls;
    }

    /**
     * Resolves the {@code catalogs} argument shared by {@code validation:jaxp()} and {@code
     * validation:jaxv()} into an {@link LSResourceResolver}, per the documented contract: an
     * empty sequence selects the system catalog; a URL ending in '/' is a directory-search
     * (collection) catalog; a URL ending in '.xml' is an explicit catalog document (which may be
     * stored in the database). Any other URL form is a caller error.
     *
     * @param caller the function requesting resolution, used to attribute a thrown {@link
     *               XPathException} to the right place.
     * @param brokerPool the broker pool, used for the system catalog and directory-search cases.
     * @param broker the broker to use for reading any '.xml' catalog stored in the database.
     * @param subject the subject to use for directory-search/database access.
     * @param catalogsArg the catalogs argument: an empty sequence (system catalog), or one or
     *                     more catalog URLs.
     *
     * @return the resolver for {@code catalogsArg}.
     *
     * @throws XPathException if a catalog URL doesn't end in '/' or '.xml'.
     * @throws IOException if a catalog stored in the database could not be read.
     * @throws URISyntaxException if a catalog URL is not a valid URI.
     */
    public static LSResourceResolver resolveCatalogArgument(final BasicFunction caller, final BrokerPool brokerPool,
            final DBBroker broker, final Subject subject, final Sequence catalogsArg)
            throws XPathException, IOException, URISyntaxException {

        if (catalogsArg.isEmpty()) {
            LOG.debug("Using system catalog.");
            final Configuration config = brokerPool.getConfiguration();
            return (Resolver) config.getProperty(XMLReaderObjectFactory.CATALOG_RESOLVER);
        }

        final String[] catalogUrls = getUrls(catalogsArg);
        final String singleUrl = catalogUrls[0];

        if (singleUrl.endsWith("/")) {
            LOG.debug("Search for grammar in {}", singleUrl);
            return new SearchResourceResolver(brokerPool, subject, singleUrl);

        } else if (singleUrl.endsWith(".xml")) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Using catalogs {}", String.join(" ", catalogUrls));
            }
            return ResolverFactory.resolveCatalogs(broker, catalogUrls);

        } else {
            throw new XPathException(caller, "Catalog URLs should end on / or .xml");
        }
    }

    /**
     * Create validation report.
     * @param report The validation report data.
     * @param builder Helperclass to create in memory XML.
     * @return Validation report as node.
     */
    static public NodeImpl writeReport(ValidationReport report, MemTreeBuilder builder) {

        // start root element
        final int nodeNr = builder.startElement("", "report", "report", null);

        // validation status: valid or invalid
        builder.startElement("", "status", "status", null);
        if (report.isValid()) {
            builder.characters("valid");
        } else {
            builder.characters("invalid");
        }
        builder.endElement();

        // namespace when available
        if (report.getNamespaceUri() != null) {
            builder.startElement("", "namespace", "namespace", null);
            builder.characters(report.getNamespaceUri());
            builder.endElement();
        }


        // validation duration
        final AttributesImpl durationAttribs = new AttributesImpl();
        durationAttribs.addAttribute("", "unit", "unit", "CDATA", "msec");

        builder.startElement("", "duration", "duration", durationAttribs);
        builder.characters("" + report.getValidationDuration());
        builder.endElement();

        // print exceptions if any
        if (report.getThrowable() != null) {
            builder.startElement("", "exception", "exception", null);

            final String className = report.getThrowable().getClass().getName();
            if (className != null) {
                builder.startElement("", "class", "class", null);
                builder.characters(className);
                builder.endElement();
            }

            final String message = report.getThrowable().getMessage();
            if (message != null) {
                builder.startElement("", "message", "message", null);
                builder.characters(message);
                builder.endElement();
            }

            final String stacktrace = report.getStackTrace();
            if (stacktrace != null) {
                builder.startElement("", "stacktrace", "stacktrace", null);
                builder.characters(stacktrace);
                builder.endElement();
            }

            builder.endElement();
        }

        // reusable attributes
        final AttributesImpl attribs = new AttributesImpl();

        // iterate validation report items, write message
        for (final ValidationReportItem vri : report.getValidationReportItemList()) {
            // construct attributes
            attribs.addAttribute("", "level", "level", "CDATA", vri.getTypeText());
            attribs.addAttribute("", "line", "line", "CDATA", Integer.toString(vri.getLineNumber()));
            attribs.addAttribute("", "column", "column", "CDATA", Integer.toString(vri.getColumnNumber()));

            if (vri.getRepeat() > 1) {
                attribs.addAttribute("", "repeat", "repeat", "CDATA", Integer.toString(vri.getRepeat()));
            }

            // write message
            builder.startElement("", "message", "message", attribs);
            builder.characters(vri.getMessage());
            builder.endElement();

            // Reuse attributes
            attribs.clear();
        }

        // finish root element
        builder.endElement();

        // return result
        return builder.getDocument().getNode(nodeNr);

    }

    /**
     * Safely close the input source and underlying inputstream.
     * @param source The inputsource.
     */
    public static void closeInputSource(InputSource source){

        if(source==null){
            return;
        }

        final InputStream is = source.getByteStream();
        if(is==null){
            return;
        }

        try {
            is.close();
        } catch (final Exception ex){
            LOG.error("Problem while closing inputstream. ({}) {}", getDetails(source), ex.getMessage(), ex);
        }

    }

    /**
     *  Safely close the stream source and underlying inputstream.
     * @param source The stream source.
     */
    public static void closeStreamSource(StreamSource source){

        if(source==null){
            return;
        }

        final InputStream is = source.getInputStream();
        if(is==null){
            return;
        }

        try {
            is.close();
        } catch (final Exception ex) {
            LOG.error("Problem while closing inputstream. ({}) {}", getDetails(source), ex.getMessage(), ex);
        }

    }

    /**
     * Safely close the stream sources and underlying inputstreams.
     * @param sources Streamsources.
     */
    public static void closeStreamSources(StreamSource[] sources){

        if(sources==null){
            return;
        }

        for(final StreamSource source : sources){
            closeStreamSource(source);
        }

    }

    private static String getDetails(InputSource source) {
        final StringBuilder sb = new StringBuilder();

        if(source.getPublicId()!=null){
            sb.append("PublicId='");
            sb.append(source.getPublicId());
            sb.append("'  ");
        }

        if(source.getSystemId()!=null){
            sb.append("SystemId='");
            sb.append(source.getSystemId());
            sb.append("'  ");
        }

        if(source.getEncoding()!=null){
            sb.append("Encoding='");
            sb.append(source.getEncoding());
            sb.append("'  ");
        }

        return sb.toString();
    }

    private static String getDetails(StreamSource source) {
        final StringBuilder sb = new StringBuilder();

        if(source.getPublicId()!=null){
            sb.append("PublicId='");
            sb.append(source.getPublicId());
            sb.append("'  ");
        }

        if(source.getSystemId()!=null){
            sb.append("SystemId='");
            sb.append(source.getSystemId());
            sb.append("'  ");
        }

        return sb.toString();
    }
}
