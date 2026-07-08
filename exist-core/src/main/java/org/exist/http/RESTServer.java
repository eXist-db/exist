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
package org.exist.http;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.collections.triggers.TriggerException;
import org.exist.debuggee.DebuggeeFactory;
import org.exist.dom.QName;
import org.exist.dom.memtree.ElementImpl;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.dom.persistent.*;
import org.exist.http.servlets.EXistServlet;
import org.exist.http.servlets.HttpRequestWrapper;
import org.exist.http.servlets.HttpResponseWrapper;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.http.urlrewrite.XQueryURLRewrite;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.internal.RealmImpl;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.source.URLSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.ExecutableResource;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.storage.lock.ManagedCollectionLock;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.storage.serializers.Serializer;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import org.exist.util.io.CachingFilterInputStream;
import org.exist.util.io.FilterInputStreamCache;
import org.exist.util.io.FilterInputStreamCacheFactory;
import org.exist.util.io.FilterInputStreamCacheFactory.FilterInputStreamCacheConfiguration;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.util.serializer.json.JSONNode;
import org.exist.util.serializer.json.JSONObject;
import org.exist.util.serializer.json.JSONSimpleProperty;
import org.exist.util.serializer.json.JSONValue;
import org.exist.xmldb.XmldbURI;
import org.exist.xqj.Marshaller;
import org.exist.xquery.*;
import org.exist.xquery.value.*;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.exquery.http.HttpRequest;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.annotation.Nullable;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;
import java.io.*;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.util.Properties;
import java.util.*;
import java.util.function.BiFunction;

import static java.lang.invoke.MethodType.methodType;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.exist.http.RESTServerParameter.*;

/**
 *
 * @author wolf
 * @author ljo
 * @author adam
 * @author gev
 */
public class RESTServer {

    protected final static Logger LOG = LogManager.getLogger(RESTServer.class);
    public final static String SERIALIZATION_METHOD_PROPERTY = "output-as";
    // Should we not obey the instance's defaults? /ljo
    protected final static Properties defaultProperties = new Properties();

    static {
        defaultProperties.setProperty(OutputKeys.INDENT, "yes");
        defaultProperties.setProperty(OutputKeys.MEDIA_TYPE, MimeType.XML_TYPE.getName());
        defaultProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, "yes");
        defaultProperties.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, "elements");
        defaultProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "yes");
    }
    public final static Properties defaultOutputKeysProperties = new Properties();

    static {
        defaultOutputKeysProperties.setProperty(EXistOutputKeys.OMIT_ORIGINAL_XML_DECLARATION, "no");
        defaultOutputKeysProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        defaultOutputKeysProperties.setProperty(OutputKeys.INDENT, "yes");
        defaultOutputKeysProperties.setProperty(OutputKeys.MEDIA_TYPE,
                MimeType.XML_TYPE.getName());
    }
    private final static String QUERY_ERROR_HEAD = "<html>" + "<head>"
            + "<title>Query Error</title>" + "<style type=\"text/css\">"
            + ".errmsg {" + "  border: 1px solid black;" + "  padding: 15px;"
            + "  margin-left: 20px;" + "  margin-right: 20px;" + "}"
            + "h1 { color: #C0C0C0; }" + ".path {" + "  padding-bottom: 10px;"
            + "}" + ".high { " + "  color: #666699; " + "  font-weight: bold;"
            + "}" + "</style>" + "</head>" + "<body>" + "<h1>XQuery Error</h1>";

    private static final String DEFAULT_ENCODING = UTF_8.name();

    private final String formEncoding; // TODO: we may be able to remove this
    // eventually, in favour of
    // HttpServletRequestWrapper being setup in
    // EXistServlet, currently used for doPost()
    // but perhaps could be used for other
    // Request Methods? - deliriumsky
    private final String containerEncoding;
    private final boolean useDynamicContentType;
    private final boolean safeMode;
    private final SessionManager sessionManager;
    private final EXistServlet.FeatureEnabled xquerySubmission;
    private final EXistServlet.FeatureEnabled xupdateSubmission;

    //EXQuery Request Module details
    private String xqueryContextExqueryRequestAttribute = null;
    private BiFunction<HttpServletRequest, FilterInputStreamCacheConfiguration, HttpRequest> cstrHttpServletRequestAdapter = null;

    // Constructor
    public RESTServer(final String formEncoding,
                      final String containerEncoding, final boolean useDynamicContentType, final boolean safeMode, final EXistServlet.FeatureEnabled xquerySubmission, final EXistServlet.FeatureEnabled xupdateSubmission) {
        this.formEncoding = formEncoding;
        this.containerEncoding = containerEncoding;
        this.useDynamicContentType = useDynamicContentType;
        this.safeMode = safeMode;
        this.sessionManager = new SessionManager();
        this.xquerySubmission = xquerySubmission;
        this.xupdateSubmission = xupdateSubmission;

        //get (optiona) EXQuery Request Module details
        try {
            Class clazz = Class.forName("org.exist.extensions.exquery.modules.request.RequestModule");
            if(clazz != null) {
                final Field fldExqRequestAttr = clazz.getDeclaredField("EXQ_REQUEST_ATTR");
                if(fldExqRequestAttr != null) {
                    this.xqueryContextExqueryRequestAttribute = (String)fldExqRequestAttr.get(null);

                    if(this.xqueryContextExqueryRequestAttribute != null) {
                        clazz = Class.forName("org.exist.extensions.exquery.restxq.impl.adapters.HttpServletRequestAdapter");
                        if(clazz != null) {
                            final MethodHandles.Lookup lookup = MethodHandles.lookup();
                            final MethodHandle methodHandle = lookup.findConstructor(clazz, methodType(void.class, HttpServletRequest.class, FilterInputStreamCacheConfiguration.class));

                            this.cstrHttpServletRequestAdapter =
                                    (BiFunction<HttpServletRequest, FilterInputStreamCacheConfiguration, HttpRequest>)
                                            LambdaMetafactory.metafactory(
                                                    lookup, "apply", methodType(BiFunction.class),
                                                    methodHandle.type().erase(), methodHandle, methodHandle.type()).getTarget().invokeExact();
                        }
                    }

                }
            }
        } catch(final InterruptedException e) {
            // NOTE: must set interrupted flag
            Thread.currentThread().interrupt();

            if(LOG.isDebugEnabled()) {
                LOG.debug("EXQuery Request Module is not present: {}", e.getMessage(), e);
            }
        } catch(final Throwable e) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("EXQuery Request Module is not present: {}", e.getMessage(), e);
            }
        }
    }

    /**
     * Retrieves a parameter from the Query String of the request
     */
    private String getParameter(final HttpServletRequest request, final RESTServerParameter parameter) {
        return request.getParameter(parameter.queryStringKey());
    }

    /**
     * Handle GET request. In the simplest case just returns the document or
     * binary resource specified in the path. If the path leads to a collection,
     * a listing of the collection contents is returned. If it resolves to a
     * binary resource with mime-type "application/xquery", this resource will
     * be loaded and executed by the XQuery engine.
     *
     * The method also recognizes a number of predefined parameters:
     *
     * <ul> <li>_xpath or _query: if specified, the given query is executed on
     * the current resource or collection.</li>
     *
     * <li>_howmany: defines how many items from the query result will be
     * returned.</li>
     *
     * <li>_start: a start offset into the result set.</li>
     *
     * <li>_wrap: if set to "yes", the query results will be wrapped into a
     * exist:result element.</li>
     *
     * <li>_indent: if set to "yes", the returned XML will be pretty-printed.
     * </li>
     *
     * <li>_source: if set to "yes" and a resource with mime-type
     * "application/xquery" is requested then the xquery will not be executed,
     * instead the source of the document will be returned. Must be enabled in
     * descriptor.xml with the following syntax
     * <pre>{@code
     *     <xquery-app>
     *         <allow-source>
     *             <xquery path="/db/mycollection/myquery.xql"/>
     *         </allow-source>
     *     </xquery-app>
     * }</pre>
     * </li>
     *
     * <li>_xsl: an URI pointing to an XSL stylesheet that will be applied to
     * the returned XML.</li>
     *
     * <li>_output-doctype: if set to "yes", the returned XML will include
     * a Document Type Declaration if one is present, if "no" the Document Type Declaration will be omitted.</li>
     * </ul>
     *
     * @param broker the database broker
     * @param transaction the database transaction
     * @param request the request
     * @param response the response
     * @param path the path of the request
     *
     * @throws BadRequestException if a bad request is made
     * @throws PermissionDeniedException if the request has insufficient permissions
     * @throws NotFoundException if the request resource cannot be found
     * @throws IOException if an I/O error occurs
     */
    public void doGet(final DBBroker broker, final Txn transaction, final HttpServletRequest request,
            final HttpServletResponse response, final String path)
            throws BadRequestException, PermissionDeniedException,
            NotFoundException, IOException {

        // if required, set character encoding
        if (request.getCharacterEncoding() == null) {
            request.setCharacterEncoding(formEncoding);
        }

        if (handleSessionRelease(broker, request, response)) {
            return;
        }

        // Process special parameters
        final Properties outputProperties = new Properties(defaultOutputKeysProperties);
        final QueryRequestOptions options = parseGetRequestOptions(broker, request, response, path, outputProperties);

        final String mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE);

        if (options.query != null) {
            // query parameter specified, search method does all the rest of the work
            try {
                search(broker, transaction, path, options, outputProperties, request, response);

            } catch (final XPathException e) {
                writeQueryError(response, HttpServletResponse.SC_BAD_REQUEST, mimeType, options.encoding,
                        options.query, path, e);
            }
            return;
        }
        // Process the request
        serveResource(broker, transaction, request, response, path, options, outputProperties, mimeType);
    }

    /**
     * Handles the {@code _release} parameter of a GET request, i.e. a
     * request to release the cached results of a previously executed query.
     *
     * @param broker the database broker
     * @param request the request
     * @param response the response
     *
     * @return true if a session release was requested and handled, false otherwise
     *
     * @throws BadRequestException if an invalid session id is passed in the request
     * @throws PermissionDeniedException if the request has insufficient permissions
     */
    private boolean handleSessionRelease(final DBBroker broker, final HttpServletRequest request,
            final HttpServletResponse response) throws BadRequestException, PermissionDeniedException {
        final String option = getParameter(request, Release);
        if (option == null) {
            return false;
        }
        final Subject subject = broker.getCurrentSubject();
        // DBA-only "force reaper": evict every cached entry, bypassing
        // the subject-match check, so a DBA can recover from cases where
        // entries are stranded by failed client sessions and would
        // otherwise wait for the per-entry 2-minute timeout.
        if ("all".equalsIgnoreCase(option) || "*".equals(option)) {
            if (!subject.hasDbaRole()) {
                throw new PermissionDeniedException(
                        "Releasing all cached query results requires DBA privileges.");
            }
            final long evicted = sessionManager.releaseAll();
            LOG.info("DBA '{}' force-released all cached query results ({} entries).",
                    subject.getName(), evicted);
            response.setStatus(HttpServletResponse.SC_OK);
            return true;
        }
        final long sessionId;
        try {
            sessionId = Long.parseLong(option);
        } catch (final NumberFormatException e) {
            throw new BadRequestException("Invalid session id passed in release request: " + option);
        }
        // DBA callers may release any entry, regardless of which
        // subject created it; non-DBA callers are restricted to their
        // own entries by the subject-match check in release().
        if (subject.hasDbaRole()) {
            sessionManager.releaseAny(sessionId);
        } else {
            sessionManager.release(subject.getId(), sessionId);
        }
        if (LOG.isDebugEnabled()) {
            LOG.debug("Released session {}", sessionId);
        }
        response.setStatus(HttpServletResponse.SC_OK);
        return true;
    }

    /**
     * Holder for the scalar options controlling the processing
     * of a REST Server query request.
     */
    private static class QueryRequestOptions {
        String query = null;
        List<Namespace> namespaces = null;
        ElementImpl variables = null;
        int howmany = 10;
        int start = 1;
        boolean typed = false;
        boolean wrap = true;
        boolean source = false;
        boolean cache = false;
        String stylesheet = null;
        String encoding = null;
    }

    /**
     * Parses the predefined parameters from the query string of a GET request.
     *
     * Output related parameters are set on the given outputProperties,
     * the scalar options are returned in a {@link QueryRequestOptions} holder.
     *
     * @param broker the database broker
     * @param request the request
     * @param response the response
     * @param path the path of the request
     * @param outputProperties the serialization properties
     *
     * @return the parsed options
     *
     * @throws BadRequestException if a parameter has an invalid value
     * @throws IOException if an I/O error occurs
     */
    private QueryRequestOptions parseGetRequestOptions(final DBBroker broker, final HttpServletRequest request,
            final HttpServletResponse response, final String path, final Properties outputProperties)
            throws BadRequestException, IOException {
        final QueryRequestOptions options = new QueryRequestOptions();

        if (!safeMode) {
            options.query = getParameter(request, XPath);
            if (options.query == null) {
                options.query = getParameter(request, Query);
            }
        }
        parseVariablesParameter(broker, request, response, path, options);

        options.howmany = parseIntParameter(request, HowMany, options.howmany);
        options.start = parseIntParameter(request, Start, options.start);

        String option;
        if ((option = getParameter(request, Typed)) != null && "yes".equalsIgnoreCase(option)) {
            options.typed = true;
        }
        if ((option = getParameter(request, Wrap)) != null) {
            options.wrap = "yes".equals(option);
            outputProperties.setProperty("_wrap", option);
        }
        if ((option = getParameter(request, Cache)) != null) {
            options.cache = "yes".equals(option);
        }
        setOutputPropertyIfPresent(request, outputProperties, Indent, OutputKeys.INDENT);
        setOutputProperty(broker, request, outputProperties, Output_Doctype,
                EXistOutputKeys.OUTPUT_DOCTYPE, Serializer.PROPERTY_OUTPUT_DOCTYPE, "yes");
        setOutputProperty(broker, request, outputProperties, Omit_Xml_Declaration,
                OutputKeys.OMIT_XML_DECLARATION, Serializer.PROPERTY_OMIT_XML_DECLARATION, "yes");
        setOutputProperty(broker, request, outputProperties, Omit_Original_Xml_Declaration,
                EXistOutputKeys.OMIT_ORIGINAL_XML_DECLARATION, Serializer.PROPERTY_OMIT_ORIGINAL_XML_DECLARATION, "no");
        if ((option = getParameter(request, Source)) != null && !safeMode) {
            options.source = "yes".equals(option);
        }
        setOutputPropertyIfPresent(request, outputProperties, Session, Serializer.PROPERTY_SESSION_ID);

        options.stylesheet = parseStylesheetParameter(request, outputProperties);
        LOG.debug("stylesheet = {}", options.stylesheet);
        LOG.debug("query = {}", options.query);

        options.encoding = parseEncodingParameter(request, outputProperties);

        return options;
    }

    /**
     * Parses the {@code _variables} parameter from the query string
     * of a GET request.
     *
     * Sets both the variables and the namespaces members of the
     * given options.
     *
     * @param broker the database broker
     * @param request the request
     * @param response the response
     * @param path the path of the request
     * @param options the options to store the parsed variables in
     *
     * @throws IOException if an I/O error occurs
     */
    private void parseVariablesParameter(final DBBroker broker, final HttpServletRequest request,
            final HttpServletResponse response, final String path, final QueryRequestOptions options)
            throws IOException {
        final String _var = getParameter(request, Variables);
        try {
            if (_var != null) {
                final NamespaceExtractor nsExtractor = new NamespaceExtractor();
                options.variables = parseXML(broker.getBrokerPool(), _var, nsExtractor);
                options.namespaces = nsExtractor.getNamespaces();
            }
        } catch (final SAXException e) {
            final XPathException x = new XPathException(options.variables != null ? options.variables.getExpression() : null, e.toString());
            writeXPathException(response, HttpServletResponse.SC_BAD_REQUEST, DEFAULT_ENCODING, options.query, path, x);
        }
    }

    /**
     * Parses an integer parameter from the query string of a GET request.
     *
     * @param request the request
     * @param parameter the parameter to parse
     * @param defaultValue the value to return if the parameter is not present
     *
     * @return the parsed value, or the default value if the parameter is not present
     *
     * @throws BadRequestException if the parameter value is not an int
     */
    private int parseIntParameter(final HttpServletRequest request, final RESTServerParameter parameter,
            final int defaultValue) throws BadRequestException {
        final String option = getParameter(request, parameter);
        if (option == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(option);
        } catch (final NumberFormatException nfe) {
            throw new BadRequestException(
                    "Parameter " + parameter.queryStringKey() + " should be an int");
        }
    }

    /**
     * Sets an output property from a parameter in the query string of a
     * GET request, falling back to the configured default if the parameter
     * is not present.
     *
     * @param broker the database broker
     * @param request the request
     * @param outputProperties the serialization properties
     * @param parameter the parameter to read
     * @param outputKey the key of the output property to set
     * @param configKey the key of the configured default value
     * @param configDefault the default value if nothing is configured
     */
    private void setOutputProperty(final DBBroker broker, final HttpServletRequest request,
            final Properties outputProperties, final RESTServerParameter parameter,
            final String outputKey, final String configKey, final String configDefault) {
        final String option = getParameter(request, parameter);
        if (option != null) {
            // take the user query-string specified setting
            outputProperties.setProperty(outputKey, option);
        } else {
            // set the property by configuration
            outputProperties.setProperty(outputKey, broker.getConfiguration().getProperty(configKey, configDefault));
        }
    }

    /**
     * Sets an output property from a parameter in the query string
     * of a GET request, if the parameter is present.
     *
     * @param request the request
     * @param outputProperties the serialization properties
     * @param parameter the parameter to read
     * @param outputKey the key of the output property to set
     */
    private void setOutputPropertyIfPresent(final HttpServletRequest request, final Properties outputProperties,
            final RESTServerParameter parameter, final String outputKey) {
        final String option = getParameter(request, parameter);
        if (option != null) {
            outputProperties.setProperty(outputKey, option);
        }
    }

    /**
     * Parses the {@code _xsl} parameter from the query string of a GET request.
     *
     * @param request the request
     * @param outputProperties the serialization properties
     *
     * @return the stylesheet to apply, or null if none was requested
     */
    private String parseStylesheetParameter(final HttpServletRequest request, final Properties outputProperties) {
        String stylesheet;
        if ((stylesheet = getParameter(request, XSL)) != null) {
            if ("no".equals(stylesheet)) {
                outputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "no");
                outputProperties.remove(EXistOutputKeys.STYLESHEET);
                stylesheet = null;
            } else {
                outputProperties.setProperty(EXistOutputKeys.STYLESHEET, stylesheet);
            }
        } else {
            outputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, "yes");
        }
        return stylesheet;
    }

    /**
     * Parses the {@code _encoding} parameter from the query string of a GET request.
     *
     * @param request the request
     * @param outputProperties the serialization properties
     *
     * @return the character encoding to use for the response
     */
    private String parseEncodingParameter(final HttpServletRequest request, final Properties outputProperties) {
        String encoding;
        if ((encoding = getParameter(request, Encoding)) != null) {
            outputProperties.setProperty(OutputKeys.ENCODING, encoding);
        } else {
            encoding = DEFAULT_ENCODING;
        }
        return encoding;
    }

    /**
     * Serves the resource or collection addressed by the path of a GET
     * request, or executes it if it addresses an XQuery or XProc resource.
     *
     * @param broker the database broker
     * @param transaction the database transaction
     * @param request the request
     * @param response the response
     * @param path the path of the request
     * @param options the parsed options of the request
     * @param outputProperties the serialization properties
     * @param mimeType the media type of the response
     *
     * @throws BadRequestException if a bad request is made
     * @throws PermissionDeniedException if the request has insufficient permissions
     * @throws NotFoundException if the request resource cannot be found
     * @throws IOException if an I/O error occurs
     */
    private void serveResource(final DBBroker broker, final Txn transaction, final HttpServletRequest request,
            final HttpServletResponse response, final String path, final QueryRequestOptions options,
            final Properties outputProperties, final String mimeType)
            throws BadRequestException, PermissionDeniedException, NotFoundException, IOException {
        LockedDocument lockedDocument = null;
        final XmldbURI pathUri = XmldbURI.create(path);
        try {
            // check if path leads to an XQuery resource
            lockedDocument = getResourceForRequest(broker, pathUri);
            DocumentImpl resource = lockedDocument == null ? null : lockedDocument.getDocument();

            if (null != resource && !isExecutableType(resource)) {
                // return regular resource that is not an xquery and not is xproc
                writeResourceAs(resource, broker, options.stylesheet, options.encoding, null,
                        outputProperties, request, response);
                return;
            }

            XmldbURI servletPath = pathUri;
            if (resource == null) { // could be request for a Collection
                if (serveCollection(broker, response, path, pathUri, options, mimeType)) {
                    return;
                }

                // work up the url path to find an xquery or xproc resource
                final ResolvedExecutable resolved = resolveExecutable(broker, pathUri, path);
                lockedDocument = resolved.lockedDocument;
                resource = resolved.resource;
                servletPath = resolved.servletPath;
            }

            if (null == resource) { // path search failed
                throw new NotFoundException("Document " + path + " not found");
            }

            // found an XQuery or XProc resource, fixup request values
            final String pathInfo = pathUri.trimFromBeginning(servletPath).toString();

            // reset any output-doctype, omit-xml-declaration, or omit-original-xml-declaration properties, as these can conflict with others set via XQuery Serialization settings
            outputProperties.setProperty(EXistOutputKeys.OUTPUT_DOCTYPE, "no");
            outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            outputProperties.setProperty(EXistOutputKeys.OMIT_ORIGINAL_XML_DECLARATION, "yes");

            // Should we display the source of the XQuery or XProc or execute it
            final Descriptor descriptor = Descriptor.getDescriptorSingleton();
            if (options.source) {
                serveSource(broker, resource, descriptor, request, response, path, options,
                        outputProperties);
            } else {
                executeResource(broker, transaction, resource, request, response, path, options,
                        outputProperties, servletPath, pathInfo);
            }
        } finally {
            if (lockedDocument != null) {
                lockedDocument.close();
            }
        }
    }

    /**
     * Serves a listing of the contents of the collection addressed by
     * the path of a GET request, if any.
     *
     * @param broker the database broker
     * @param response the response
     * @param path the path of the request
     * @param pathUri the path of the request as an URI
     * @param options the parsed options of the request
     * @param mimeType the media type of the response
     *
     * @return true if a collection listing was written to the response, false otherwise
     *
     * @throws NotFoundException if the source view of a non-existent resource was requested
     * @throws PermissionDeniedException if the request has insufficient permissions
     * @throws IOException if an I/O error occurs
     */
    private boolean serveCollection(final DBBroker broker, final HttpServletResponse response,
            final String path, final XmldbURI pathUri, final QueryRequestOptions options, final String mimeType)
            throws NotFoundException, PermissionDeniedException, IOException {
        // no document: check if path points to a collection
        try(final Collection collection = broker.openCollection(pathUri, LockMode.READ_LOCK)) {
            if (collection != null) {
                if (safeMode || !collection.getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
                    throw new PermissionDeniedException("Not allowed to read collection");
                }
                // return a listing of the collection contents
                try {
                    writeCollection(response, options.encoding, broker, collection);
                    return true;
                } catch (final LockException le) {
                    writeQueryError(response, HttpServletResponse.SC_BAD_REQUEST, mimeType, options.encoding,
                            options.query, path, new XPathException((Expression) null, le.getMessage(), le));
                }

            } else if (options.source) {
                // didn't find regular resource, or user wants source
                // on a possible xquery resource that was not found
                throw new NotFoundException("Document " + path + " not found");
            }
        }
        return false;
    }

    /**
     * Holder for the result of resolving the executable resource
     * addressed by the path of a request.
     */
    private static class ResolvedExecutable {
        final LockedDocument lockedDocument;
        final DocumentImpl resource;
        final XmldbURI servletPath;

        ResolvedExecutable(final LockedDocument lockedDocument, final DocumentImpl resource,
                final XmldbURI servletPath) {
            this.lockedDocument = lockedDocument;
            this.resource = resource;
            this.servletPath = servletPath;
        }
    }

    /**
     * Works up the url path of a GET request to find an XQuery
     * or XProc resource.
     *
     * The locked document of the returned result, if any, must be
     * closed by the caller.
     *
     * @param broker the database broker
     * @param pathUri the path of the request as an URI
     * @param path the path of the request
     *
     * @return the resolution result, its members are null if no executable resource was found
     *
     * @throws NotFoundException if the path leads to a resource that is not executable
     * @throws PermissionDeniedException if the request has insufficient permissions
     */
    private ResolvedExecutable resolveExecutable(final DBBroker broker, final XmldbURI pathUri, final String path)
            throws NotFoundException, PermissionDeniedException {
        XmldbURI servletPath = pathUri;
        LockedDocument lockedDocument = null;
        DocumentImpl resource = null;
        while (null == resource) {
            // traverse up the path looking for xquery objects
            servletPath = servletPath.removeLastSegment();
            if (servletPath == XmldbURI.EMPTY_URI) {
                break;
            }

            lockedDocument = getResourceForRequest(broker, servletPath);
            resource = lockedDocument == null ? null : lockedDocument.getDocument();
            if (null != resource && isExecutableType(resource)) {
                break;

            } else if (null != resource) {
                // not an xquery resource. This means we have a path
                // that cannot contain an xquery object even if we keep
                // moving up the path, so bail out now
                lockedDocument.close();
                throw new NotFoundException("Document " + path + " not found");
            }
        }
        return new ResolvedExecutable(lockedDocument, resource, servletPath);
    }

    /**
     * Serves the source of the XQuery or XProc resource addressed by
     * the path of a GET request, if allowed by the descriptor.
     *
     * @param broker the database broker
     * @param resource the resource to serve the source of
     * @param descriptor the descriptor, or null if there is none
     * @param request the request
     * @param response the response
     * @param path the path of the request
     * @param options the parsed options of the request
     * @param outputProperties the serialization properties
     *
     * @throws BadRequestException if a bad request is made
     * @throws PermissionDeniedException if the request has insufficient permissions
     * @throws IOException if an I/O error occurs
     */
    private void serveSource(final DBBroker broker, final DocumentImpl resource,
            final Descriptor descriptor, final HttpServletRequest request, final HttpServletResponse response,
            final String path, final QueryRequestOptions options, final Properties outputProperties)
            throws BadRequestException, PermissionDeniedException, IOException {
        // show the source

        // check are we allowed to show the xquery source -
        // descriptor.xml
        if ((null != descriptor)
                && descriptor.allowSource(path)
                && resource.getPermissions().validate(
                broker.getCurrentSubject(), Permission.READ)) {

            // TODO: change writeResourceAs to use a serializer
            // that will serialize xquery to syntax coloured
            // xhtml, replace the asMimeType parameter with a
            // method for specifying the serializer, or split
            // the code into two methods. - deliriumsky

            if (MimeType.XQUERY_TYPE.getName().equals(resource.getMimeType())) {
                // Show the source of the XQuery
                writeResourceAs(resource, broker, options.stylesheet, options.encoding,
                        MimeType.TEXT_TYPE.getName(), outputProperties,
                        request, response);
            } else if (MimeType.XPROC_TYPE.getName().equals(resource.getMimeType())) {
                // Show the source of the XProc
                writeResourceAs(resource, broker, options.stylesheet, options.encoding,
                        MimeType.XML_TYPE.getName(), outputProperties,
                        request, response);
            }
        } else {
            // we are not allowed to show the source - query not
            // allowed in descriptor.xml
            // or descriptor not found, so assume source view not
            // allowed
            response
                    .sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Permission to view XQuery source for: "
                    + path
                    + " denied. Must be explicitly defined in descriptor.xml");
        }
    }

    /**
     * Executes the XQuery or XProc resource addressed by the path of a
     * GET request and writes its results to the response.
     *
     * @param broker the database broker
     * @param transaction the database transaction
     * @param resource the resource to execute
     * @param request the request
     * @param response the response
     * @param path the path of the request
     * @param options the parsed options of the request
     * @param outputProperties the serialization properties
     * @param servletPath the path of the executable resource
     * @param pathInfo the remainder of the request path
     *
     * @throws BadRequestException if a bad request is made
     * @throws PermissionDeniedException if the request has insufficient permissions
     * @throws IOException if an I/O error occurs
     */
    private void executeResource(final DBBroker broker, final Txn transaction, final DocumentImpl resource,
            final HttpServletRequest request, final HttpServletResponse response, final String path,
            final QueryRequestOptions options, final Properties outputProperties,
            final XmldbURI servletPath, final String pathInfo)
            throws BadRequestException, PermissionDeniedException, IOException {
        try {
            if (MimeType.XQUERY_TYPE.getName().equals(resource.getMimeType())) {
                // Execute the XQuery
                executeXQuery(broker, transaction, resource, request, response,
                        outputProperties, servletPath.toString(), pathInfo);
            } else if (MimeType.XPROC_TYPE.getName().equals(resource.getMimeType())) {
                // Execute the XProc
                executeXProc(broker, transaction, resource, request, response,
                        outputProperties, servletPath.toString(), pathInfo);
            }
        } catch (final XPathException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug(e.getMessage(), e);
            }
            final String mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE);
            writeQueryError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, mimeType, options.encoding,
                    options.query, path, e);
        }
    }

    public void doHead(final DBBroker broker, final Txn transaction, final HttpServletRequest request,
            final HttpServletResponse response, final String path)
            throws BadRequestException, PermissionDeniedException,
            NotFoundException, IOException {

        final XmldbURI pathUri = XmldbURI.create(path);
        if (checkForXQueryTarget(broker, transaction, pathUri, request, response)) {
            return;
        }

        final Properties outputProperties = new Properties(defaultOutputKeysProperties);

        String encoding;
        if ((encoding = getParameter(request, Encoding)) != null) {
            outputProperties.setProperty(OutputKeys.ENCODING, encoding);
        } else {
            encoding = DEFAULT_ENCODING;
        }

        try(final LockedDocument lockedDocument = broker.getXMLResource(pathUri, LockMode.READ_LOCK)) {
            final DocumentImpl resource = lockedDocument == null ? null : lockedDocument.getDocument();

            if (resource != null) {
                if (!resource.getPermissions().validate(broker.getCurrentSubject(), Permission.READ)) {
                    throw new PermissionDeniedException(
                            "Permission to read resource " + path + " denied");
                }
                response.setContentType(resource.getMimeType());
                // As HttpServletResponse.setContentLength is limited to integers,
                // (see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4187336)
                // next sentence:
                //	response.setContentLength(resource.getContentLength());
                // must be set so
                response.addHeader("Content-Length", Long.toString(resource.getContentLength()));
                setCreatedAndLastModifiedHeaders(response, resource.getCreated(), resource.getLastModified());
            } else {
                try(final Collection col = broker.openCollection(pathUri, LockMode.READ_LOCK)) {
                    //no resource or collection
                    if (col == null) {
                        response.sendError(HttpServletResponse.SC_NOT_FOUND, "No resource at location: " + path);

                        return;
                    }

                    if (!col.getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
                        throw new PermissionDeniedException(
                                "Permission to read resource " + path + " denied");
                    }
                    response.setContentType(MimeType.XML_TYPE.getName() + "; charset=" + encoding);
                    setCreatedAndLastModifiedHeaders(response, col.getCreated(), col.getCreated());
                }
            }
        }
    }

    /**
     * Handles POST requests. If the path leads to a binary resource with
     * mime-type "application/xquery", that resource will be read and executed
     * by the XQuery engine. Otherwise, the request content is loaded and parsed
     * as XML. It may either contain an XUpdate or a query request.
     *
     * @param broker the database broker
     * @param transaction the database transaction
     * @param request the request
     * @param response the response
     * @param path the path of the request
     *
     * @throws BadRequestException if a bad request is made
     * @throws PermissionDeniedException if the request has insufficient permissions
     * @throws NotFoundException if the request resource cannot be found
     * @throws IOException if an I/O error occurs
     */
    public void doPost(final DBBroker broker, final Txn transaction, final HttpServletRequest request,
            final HttpServletResponse response, final String path)
            throws BadRequestException, PermissionDeniedException, IOException,
            NotFoundException {

        // if required, set character encoding
        if (request.getCharacterEncoding() == null) {
            request.setCharacterEncoding(formEncoding);
        }

        final Properties outputProperties = new Properties(defaultOutputKeysProperties);
        final XmldbURI pathUri = XmldbURI.create(path);

        final String encoding = getEncoding(outputProperties);
        final String mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE);

        // check if path leads to an XQuery resource.
        // if yes, the resource is loaded and the XQuery executed.
        if (executePostExecutable(broker, transaction, request, response, path, pathUri,
                outputProperties, encoding, mimeType)) {
            return;
        }

        // check the content type to see if its XML or a parameter string
        String requestType = request.getContentType();
        if (requestType != null) {
            final int semicolon = requestType.indexOf(';');
            if (semicolon > 0) {
                requestType = requestType.substring(0, semicolon).trim();
            }
        }

        // content type != application/x-www-form-urlencoded
        if (requestType == null || !requestType.equals(MimeType.URL_ENCODED_TYPE.getName())) {
            // third, normal POST: read the request content and check if
            // it is an XUpdate or a query request.
            try {
                final String content = getRequestContent(request);
                final NamespaceExtractor nsExtractor = new NamespaceExtractor();
                final ElementImpl root = parseXML(broker.getBrokerPool(), content, nsExtractor);
                final String rootNS = root.getNamespaceURI();

                if (rootNS != null && rootNS.equals(Namespaces.EXIST_NS)) {
                    processQueryDocument(broker, transaction, request, response, path, root, nsExtractor,
                            outputProperties, encoding, mimeType);
                } else if (rootNS != null && rootNS.equals(XUpdateProcessor.XUPDATE_NS)) {
                    processXUpdate(broker, transaction, response, pathUri, encoding, content);
                } else {
                    throw new BadRequestException("Unknown XML root element: " + root.getNodeName());
                }

            } catch (final SAXException e) {
                Exception cause = e;
                if (e.getException() != null) {
                    cause = e.getException();
                }
                LOG.debug("SAX exception while parsing request: {}", cause.getMessage(), cause);
                throw new BadRequestException("SAX exception while parsing request: " + cause.getMessage());

            } catch (final ParserConfigurationException e) {
                throw new BadRequestException("Parser exception while parsing request: " + e.getMessage());
            } catch (final XPathException e) {
                throw new BadRequestException("Query exception while parsing request: " + e.getMessage());
            } catch (final IOException e) {
                throw new BadRequestException("IO exception while parsing request: " + e.getMessage());
            } catch (final EXistException e) {
                throw new BadRequestException(e.getMessage());
            } catch (final LockException e) {
                throw new PermissionDeniedException(e.getMessage());
            }

            // content type = application/x-www-form-urlencoded
        } else {
            doGet(broker, transaction, request, response, path);
        }
    }

    /**
     * Determines whether the resource is executable by a POST request,
     * i.e. either a binary document with the XQuery mime-type, or an
     * XML document with the XProc mime-type.
     *
     * @param resource the resource to check
     *
     * @return true if the resource is executable
     */
    private boolean isPostExecutableType(final DocumentImpl resource) {
        return resource.getResourceType() == DocumentImpl.BINARY_FILE
                && MimeType.XQUERY_TYPE.getName().equals(resource.getMimeType())
                || resource.getResourceType() == DocumentImpl.XML_FILE
                && MimeType.XPROC_TYPE.getName().equals(resource.getMimeType());
    }

    /**
     * Works up the url path of a POST request to find an executable
     * XQuery or XProc resource.
     *
     * The locked document of the returned result, if any, must be
     * closed by the caller.
     *
     * @param broker the database broker
     * @param pathUri the path of the request as an URI
     *
     * @return the resolution result, its members are null if no executable resource was found
     *
     * @throws PermissionDeniedException if the request has insufficient permissions
     */
    private ResolvedExecutable resolvePostExecutable(final DBBroker broker, final XmldbURI pathUri)
            throws PermissionDeniedException {
        LockedDocument lockedDocument = getResourceForRequest(broker, pathUri);
        DocumentImpl resource = lockedDocument == null ? null : lockedDocument.getDocument();

        XmldbURI servletPath = pathUri;

        // if resource is still null, work up the url path to find an
        // xquery resource
        while (null == resource) {
            // traverse up the path looking for xquery objects
            servletPath = servletPath.removeLastSegment();
            if (servletPath == XmldbURI.EMPTY_URI) {
                break;
            }

            lockedDocument = getResourceForRequest(broker, servletPath);
            resource = lockedDocument == null ? null : lockedDocument.getDocument();
            if (null != resource && isPostExecutableType(resource)) {
                break; // found a binary file with mime-type xquery or XML file with mime-type xproc

            } else if (null != resource) {

                // not an xquery or xproc resource. This means we have a path
                // that cannot contain an xquery or xproc object even if we keep
                // moving up the path, so bail out now
                lockedDocument.close();
                lockedDocument = null;
                resource = null;
                break;
            }
        }
        return new ResolvedExecutable(lockedDocument, resource, servletPath);
    }

    /**
     * Executes the XQuery or XProc resource addressed by the path of a
     * POST request, if any, and writes its results to the response.
     *
     * @param broker the database broker
     * @param transaction the database transaction
     * @param request the request
     * @param response the response
     * @param path the path of the request
     * @param pathUri the path of the request as an URI
     * @param outputProperties the serialization properties
     * @param encoding the character encoding
     * @param mimeType the media type of the response
     *
     * @return true if an executable resource was found and executed, false otherwise
     *
     * @throws BadRequestException if a bad request is made
     * @throws PermissionDeniedException if the request has insufficient permissions
     * @throws IOException if an I/O error occurs
     */
    private boolean executePostExecutable(final DBBroker broker, final Txn transaction,
            final HttpServletRequest request, final HttpServletResponse response, final String path,
            final XmldbURI pathUri, final Properties outputProperties, final String encoding, final String mimeType)
            throws BadRequestException, PermissionDeniedException, IOException {
        final ResolvedExecutable resolved = resolvePostExecutable(broker, pathUri);
        final DocumentImpl resource = resolved.resource;
        try {
            // either xquery binary file or xproc xml file
            if (resource != null && isPostExecutableType(resource)) {
                // found an XQuery resource, fixup request values
                final String pathInfo = pathUri.trimFromBeginning(resolved.servletPath).toString();
                try {
                    if (MimeType.XQUERY_TYPE.getName().equals(resource.getMimeType())) {
                        // Execute the XQuery
                        executeXQuery(broker, transaction, resource, request, response,
                                outputProperties, resolved.servletPath.toString(), pathInfo);
                    } else {
                        // Execute the XProc
                        executeXProc(broker, transaction, resource, request, response,
                                outputProperties, resolved.servletPath.toString(), pathInfo);
                    }

                } catch (final XPathException e) {
                    writeQueryError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, mimeType, encoding, null, path, e);
                }
                return true;
            }
            return false;
        } finally {
            if (resolved.lockedDocument != null) {
                resolved.lockedDocument.close();
            }
        }
    }

    /**
     * Processes an {@code <exist:query>} document submitted in the body
     * of a POST request, and executes the query it contains.
     *
     * @param broker the database broker
     * @param transaction the database transaction
     * @param request the request
     * @param response the response
     * @param path the path of the request
     * @param root the root element of the submitted query document
     * @param nsExtractor the namespace extractor that parsed the query document
     * @param outputProperties the serialization properties
     * @param encoding the character encoding
     * @param defaultMimeType the media type of the response, if not overridden by the query document
     *
     * @throws BadRequestException if no query is specified
     * @throws PermissionDeniedException if the request has insufficient permissions
     * @throws IOException if an I/O error occurs
     */
    private void processQueryDocument(final DBBroker broker, final Txn transaction,
            final HttpServletRequest request, final HttpServletResponse response, final String path,
            final ElementImpl root, final NamespaceExtractor nsExtractor,
            final Properties outputProperties, final String encoding, final String defaultMimeType)
            throws BadRequestException, PermissionDeniedException, IOException {
        String mimeType = defaultMimeType;
        final QueryRequestOptions options = new QueryRequestOptions();
        options.namespaces = nsExtractor.getNamespaces();

        if (Query.xmlKey().equals(root.getLocalName())) {
            // process <query>xpathQuery</query>
            options.start = parseIntAttribute(root, Start, options.start);
            options.howmany = parseIntAttribute(root, Max, options.howmany);
            options.wrap = parseEncloseAttribute(root);

            String option = root.getAttribute(Method.xmlKey());
            if (!option.isEmpty()) {
                outputProperties.setProperty(SERIALIZATION_METHOD_PROPERTY, option);
            }

            option = root.getAttribute(Typed.xmlKey());
            if (!option.isEmpty() && "yes".equals(option)) {
                options.typed = true;
            }

            option = root.getAttribute(Mime.xmlKey());
            if (!option.isEmpty()) {
                mimeType = option;
            }

            if (!(option = root.getAttribute(Cache.xmlKey())).isEmpty()) {
                options.cache = "yes".equals(option);
            }

            if (!(option = root.getAttribute(Session.xmlKey())).isEmpty()) {
                outputProperties.setProperty(
                        Serializer.PROPERTY_SESSION_ID, option);
            }

            parseQueryChildren(root, outputProperties, options);
        }

        // execute query
        if (options.query != null) {

            try {
                search(broker, transaction, path, options, outputProperties, request, response);
            } catch (final XPathException e) {
                writeQueryError(response, HttpServletResponse.SC_BAD_REQUEST, mimeType,
                        encoding, null, path, e);
            }

        } else {
            throw new BadRequestException("No query specified");
        }
    }

    /**
     * Parses an integer attribute of an {@code <exist:query>} element.
     *
     * @param root the query element
     * @param parameter the attribute to parse
     * @param defaultValue the value to use if the attribute value cannot be parsed
     *
     * @return the parsed value, or the default value
     */
    private int parseIntAttribute(final ElementImpl root, final RESTServerParameter parameter,
            final int defaultValue) {
        final String option = root.getAttribute(parameter.xmlKey());
        if (option.isEmpty()) {
            try {
                return Integer.parseInt(option);
            } catch (final NumberFormatException e) {
                //
            }
        }
        return defaultValue;
    }

    /**
     * Parses the enclose (or legacy wrap) attribute of an
     * {@code <exist:query>} element.
     *
     * @param root the query element
     *
     * @return false if wrapping the results in an exist:result element was disabled, true otherwise
     */
    private boolean parseEncloseAttribute(final ElementImpl root) {
        String option = root.getAttribute(Enclose.xmlKey());
        if (!option.isEmpty()) {
            if ("no".equals(option)) {
                return false;
            }
        } else {
            option = root.getAttribute(Wrap.xmlKey());
            if (!option.isEmpty() && "no".equals(option)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses the child elements of an {@code <exist:query>} element,
     * i.e. the query text, variables and output properties.
     *
     * @param root the query element
     * @param outputProperties the serialization properties
     * @param options the options to store the parsed query and variables in
     */
    private void parseQueryChildren(final ElementImpl root, final Properties outputProperties,
            final QueryRequestOptions options) {
        final NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {

            final Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE
                    && child.getNamespaceURI().equals(Namespaces.EXIST_NS)) {

                if (Text.xmlKey().equals(child.getLocalName())) {
                    options.query = extractQueryText(child);

                } else if (Variables.xmlKey().equals(child.getLocalName())) {
                    options.variables = (ElementImpl) child;

                } else if (Properties.xmlKey().equals(child.getLocalName())) {
                    parseQueryProperties(child, outputProperties);
                }
            }
        }
    }

    /**
     * Extracts the query text from an {@code <exist:text>} element.
     *
     * @param child the text element
     *
     * @return the query text
     */
    private String extractQueryText(final Node child) {
        final StringBuilder buf = new StringBuilder();
        Node next = child.getFirstChild();
        while (next != null) {
            if (next.getNodeType() == Node.TEXT_NODE
                    || next.getNodeType() == Node.CDATA_SECTION_NODE) {
                buf.append(next.getNodeValue());
            }
            next = next.getNextSibling();
        }
        return buf.toString();
    }

    /**
     * Parses the {@code <exist:properties>} element of an
     * {@code <exist:query>} element into output properties.
     *
     * @param child the properties element
     * @param outputProperties the serialization properties
     */
    private void parseQueryProperties(final Node child, final Properties outputProperties) {
        Node node = child.getFirstChild();
        while (node != null) {
            if (node.getNodeType() == Node.ELEMENT_NODE
                    && node.getNamespaceURI().equals(Namespaces.EXIST_NS)
                    && Property.xmlKey().equals(node.getLocalName())) {

                final Element property = (Element) node;
                final String key = property.getAttribute("name");
                final String value = property.getAttribute("value");
                LOG.debug("{} = {}", key, value);

                if (!key.isEmpty() && !value.isEmpty()) {
                    outputProperties.setProperty(key, value);
                }
            }
            node = node.getNextSibling();
        }
    }

    /**
     * Processes an XUpdate document submitted in the body of a POST
     * request, and applies its modifications to the database.
     *
     * @param broker the database broker
     * @param transaction the database transaction
     * @param response the response
     * @param pathUri the path of the request as an URI
     * @param encoding the character encoding
     * @param content the XUpdate document
     *
     * @throws PermissionDeniedException if the request has insufficient permissions
     * @throws SAXException if the XUpdate document is invalid
     * @throws ParserConfigurationException if the XUpdate parser cannot be configured
     * @throws XPathException if an XUpdate modification raises an error
     * @throws EXistException if the database raises an error
     * @throws LockException if a lock error occurs
     * @throws IOException if an I/O error occurs
     */
    private void processXUpdate(final DBBroker broker, final Txn transaction, final HttpServletResponse response,
            final XmldbURI pathUri, final String encoding, final String content)
            throws PermissionDeniedException, SAXException, ParserConfigurationException, XPathException,
            EXistException, LockException, IOException {
        if(LOG.isDebugEnabled()) {
            LOG.debug("Got xupdate request: {}", content);
        }

        if (rejectForbiddenXUpdate(broker, response)) {
            return;
        }

        final MutableDocumentSet docs = new DefaultDocumentSet();

        final boolean isCollection;
        try(final Collection collection = broker.openCollection(pathUri, LockMode.READ_LOCK)) {
            if (collection != null) {
                isCollection = true;
                collection.allDocs(broker, docs, true);
            } else {
                isCollection = false;
            }
        }

        if(!isCollection) {
            final DocumentImpl xupdateDoc = broker.getResource(pathUri, Permission.READ);
            if (xupdateDoc != null) {
                docs.add(xupdateDoc);
            } else {
                broker.getAllXMLResources(docs);
            }
        }

        final XUpdateProcessor processor = new XUpdateProcessor(broker, docs);
        long mods = 0;
        try(final Reader reader = new StringReader(content)) {
            final Modification modifications[] = processor.parse(new InputSource(reader));
            for (Modification modification : modifications) {
                mods += modification.process(transaction);
                broker.flush();
            }
        }

        // FD : Returns an XML doc
        writeXUpdateResult(response, encoding, mods);
        // END FD
    }

    /**
     * Checks whether XUpdate submissions are forbidden for the current
     * subject.
     *
     * If they are forbidden, the response status is set to 403 Forbidden.
     *
     * @param broker the database broker
     * @param response the response
     *
     * @return true if the XUpdate submission was rejected, false otherwise
     */
    private boolean rejectForbiddenXUpdate(final DBBroker broker, final HttpServletResponse response) {
        if(xupdateSubmission == EXistServlet.FeatureEnabled.FALSE) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return true;
        } else if(xupdateSubmission == EXistServlet.FeatureEnabled.AUTHENTICATED_USERS_ONLY) {
            final Subject currentSubject = broker.getCurrentSubject();
            if(!currentSubject.isAuthenticated() || currentSubject.getId() == RealmImpl.GUEST_ACCOUNT_ID) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return true;
            }
        }
        return false;
    }

    private ElementImpl parseXML(final BrokerPool pool, final String content,
            final NamespaceExtractor nsExtractor)
            throws SAXException, IOException {
        final InputSource src = new InputSource(new StringReader(content));
        final XMLReaderPool parserPool = pool.getParserPool();
        XMLReader reader = null;
        try {
            reader = parserPool.borrowXMLReader();
            final SAXAdapter adapter = new SAXAdapter((Expression) null);
            nsExtractor.setContentHandler(adapter);
            reader.setProperty(Namespaces.SAX_LEXICAL_HANDLER, adapter);
            nsExtractor.setParent(reader);
            nsExtractor.parse(src);

            final Document doc = adapter.getDocument();

            return (ElementImpl) doc.getDocumentElement();
        } finally {
            if (reader != null) {
                parserPool.returnXMLReader(reader);
            }
        }
    }

    private class NamespaceExtractor extends XMLFilterImpl {

        final List<Namespace> namespaces = new ArrayList<>();

        @Override
        public void startPrefixMapping(final String prefix, final String uri)
            throws SAXException {
            if (!Namespaces.EXIST_NS.equals(uri)) {
                final Namespace ns = new Namespace(prefix, uri);
                namespaces.add(ns);
            }
            super.startPrefixMapping(prefix, uri);
        }

        public List<Namespace> getNamespaces() {
            return namespaces;
        }
    }

    public static class Namespace {

        private final String prefix;
        private final String uri;

        public Namespace(final String prefix, final String uri) {
            this.prefix = prefix;
            this.uri = uri;
        }

        public String getPrefix() {
            return prefix;
        }

        public String getUri() {
            return uri;
        }
    }

    /**
     * Handles PUT requests. The request content is stored as a new resource at
     * the specified location. If the resource already exists, it is overwritten
     * if the user has write permissions.
     *
     * The resource type depends on the content type specified in the HTTP
     * header. The content type will be looked up in the global mime table. If
     * the corresponding mime type is not a know XML mime type, the resource
     * will be stored as a binary resource.
     *
     * @param broker the database broker
     * @param transaction the database transaction
     * @param request the request
     * @param response the response
     * @param path the path of the request
     *
     * @throws BadRequestException if a bad request is made
     * @throws PermissionDeniedException if the request has insufficient permissions
     * @throws NotFoundException if the request resource cannot be found
     * @throws IOException if an I/O error occurs
     */
    public void doPut(final DBBroker broker, final Txn transaction, final XmldbURI path,
            final HttpServletRequest request, final HttpServletResponse response)
            throws BadRequestException, PermissionDeniedException, IOException,
            NotFoundException {

        if (checkForXQueryTarget(broker, transaction, path, request, response)) {
            return;
        }

        // fourth, process the request

        final XmldbURI docUri = path.lastSegment();
        final XmldbURI collUri = path.removeLastSegment();

        if (docUri == null || collUri == null) {
            throw new BadRequestException("Bad path: " + path);
        }
        // TODO : use getOrCreateCollection() right now ?
        try {
            // acquired inside the try so that a LockException during acquisition
            // is translated by the catch clauses below; released in the finally
            final ManagedCollectionLock managedCollectionLock = broker.getBrokerPool().getLockManager().acquireCollectionWriteLock(collUri);
            try {
                final Collection collection = broker.getOrCreateCollection(transaction, collUri);

                final MimeType mime;
                String contentType = request.getContentType();
                if (contentType != null) {
                    final int semicolon = contentType.indexOf(';');
                    if (semicolon > 0) {
                        contentType = contentType.substring(0, semicolon).trim();
                    }
                    mime = MimeTable.getInstance().getContentType(contentType);
                } else {
                    mime = MimeTable.getInstance().getContentTypeFor(docUri);
                }

                // TODO(AR) in storeDocument need to handle mime == null and use MimeType.BINARY_TYPE
                // TODO(AR) in storeDocument, if the input source has an InputStream (but is not a subclass: FileInputSource or ByteArrayInputSource), need to handle caching and reusing the input stream between validate and store
                try (final FilterInputStreamCache cache = FilterInputStreamCacheFactory.getCacheInstance(()
                        -> (String) broker.getConfiguration().getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY), request.getInputStream());
                    final CachingFilterInputStream cfis = new CachingFilterInputStream(cache)) {
                    broker.storeDocument(transaction, docUri, new CachingFilterInputStreamInputSource(cfis), mime, collection);
                }
                response.setStatus(HttpServletResponse.SC_CREATED);
            } finally {
                managedCollectionLock.close();
            }

        } catch (final SAXParseException e) {
            throw new BadRequestException("Parsing exception at "
                    + e.getLineNumber() + "/" + e.getColumnNumber() + ": "
                    + e);
        } catch (final TriggerException | LockException e) {
            throw new PermissionDeniedException(e.getMessage());
        } catch (final SAXException e) {
            Exception o = e.getException();
            if (o == null) {
                o = e;
            }
            throw new BadRequestException("Parsing exception: " + o.getMessage());
        } catch (final EXistException e) {
            throw new BadRequestException("Internal error: " + e.getMessage());
        }
    }


    /**
     * Handles PATCH requests. Only XQuery modules are allowed as targets
     * otherwise it is unclear how to handle the request and a method not allowed
     * is returned.
     *
     * @param broker the database broker
     * @param transaction the database transaction
     * @param request the request
     * @param response the response
     * @param path the path of the request
     *
     * @throws BadRequestException if a bad request is made
     * @throws PermissionDeniedException if the request has insufficient permissions
     * @throws NotFoundException if the request resource cannot be found
     * @throws IOException if an I/O error occurs
     * @throws MethodNotAllowedException if the patch request is not permitted for the resource indicated
     */
    public void doPatch(final DBBroker broker, final Txn transaction, final XmldbURI path,
                      final HttpServletRequest request, final HttpServletResponse response)
            throws BadRequestException, PermissionDeniedException, IOException,
            NotFoundException, MethodNotAllowedException {

        if (checkForXQueryTarget(broker, transaction, path, request, response)) {
            return;
        }

        throw new MethodNotAllowedException("No xquery found to handle patch request: " + path);
    }

    public void doDelete(final DBBroker broker, final Txn transaction, final String path, final HttpServletRequest request, final HttpServletResponse response)
            throws PermissionDeniedException, NotFoundException, IOException, BadRequestException {
        final XmldbURI pathURI = XmldbURI.create(path);
        if (checkForXQueryTarget(broker, transaction, pathURI, request, response)) {
            return;
        }

        try {
            try(final Collection collection = broker.openCollection(pathURI, LockMode.WRITE_LOCK)) {
                if (collection != null) {
                    // remove the collection
                    LOG.debug("removing collection {}", path);

                    broker.removeCollection(transaction, collection);

                    response.setStatus(HttpServletResponse.SC_OK);

                } else {
                    try(final LockedDocument lockedDocument = broker.getXMLResource(pathURI, LockMode.WRITE_LOCK)) {
                        final DocumentImpl doc = lockedDocument == null ? null : lockedDocument.getDocument();
                        if (doc == null) {
                            throw new NotFoundException("No document or collection found for path: " + path);
                        } else {
                            if (!doc.getPermissions().validate(broker.getCurrentSubject(), Permission.WRITE)) {
                                throw new PermissionDeniedException("Account '" + broker.getCurrentSubject().getName() + "' not allowed requested access to document '" + pathURI + "'");
                            }

                            // remove the document
                            if(LOG.isDebugEnabled()) {
                                LOG.debug("removing document {}", path);
                            }

                            if (doc.getResourceType() == DocumentImpl.BINARY_FILE) {
                                doc.getCollection().removeBinaryResource(transaction, broker, pathURI.lastSegment());
                            } else {
                                doc.getCollection().removeXMLResource(transaction, broker, pathURI.lastSegment());
                            }

                            response.setStatus(HttpServletResponse.SC_OK);
                        }
                    }
                }
            }

        } catch (final TriggerException e) {
            throw new PermissionDeniedException("Trigger failed: " + e.getMessage());
        } catch (final LockException e) {
            throw new PermissionDeniedException("Could not acquire lock: " + e.getMessage());
        }
    }

    private boolean checkForXQueryTarget(final DBBroker broker, final Txn transaction,
        final XmldbURI path, final HttpServletRequest request,
        final HttpServletResponse response) throws PermissionDeniedException, IOException, BadRequestException {

        if (request.getAttribute(XQueryURLRewrite.RQ_ATTR) == null) {
            return false;
        }

        // a collection is not executable
        if (broker.getCollection(path) != null) {
            return false;
        }

        final ResolvedExecutable resolved = resolveXQueryTarget(broker, path);
        if (resolved.resource == null) {
            return false;
        }

        // found an XQuery resource, fixup request values
        final String pathInfo = path.trimFromBeginning(resolved.servletPath).toString();
        final Properties outputProperties = new Properties(defaultOutputKeysProperties);
        try {
            // Execute the XQuery
            executeXQuery(broker, transaction, resolved.resource, request, response,
                    outputProperties, resolved.servletPath.toString(), pathInfo);
        } catch (final XPathException e) {
            writeXPathExceptionHtml(response, HttpServletResponse.SC_BAD_REQUEST, DEFAULT_ENCODING, null, path.toString(), e);
        } finally {
            resolved.lockedDocument.close();
        }
        return true;
    }

    /**
     * Works up the url path to find a binary XQuery resource that can
     * handle the request.
     *
     * The locked document of the returned result, if any, must be closed
     * by the caller.
     *
     * @param broker the database broker
     * @param path the path of the request as an URI
     *
     * @return the resolution result; its members are null if no XQuery resource was found
     *
     * @throws PermissionDeniedException if the request has insufficient permissions
     */
    private ResolvedExecutable resolveXQueryTarget(final DBBroker broker, final XmldbURI path)
            throws PermissionDeniedException {
        final String xqueryType = MimeType.XQUERY_TYPE.getName();
        XmldbURI servletPath = path;
        LockedDocument lockedDocument = null;
        DocumentImpl resource = null;
        // work up the url path to find an
        // xquery resource
        while (resource == null) {
            // traverse up the path looking for xquery objects
            lockedDocument = getResourceForRequest(broker, servletPath);
            resource = lockedDocument == null ? null : lockedDocument.getDocument();
            if (resource != null
                    && resource.getResourceType() == DocumentImpl.BINARY_FILE
                    && xqueryType.equals(resource.getMimeType())) {
                break; // found a binary file with mime-type xquery
            } else if (resource != null) {
                // not an xquery resource. This means we have a path
                // that cannot contain an xquery object even if we keep
                // moving up the path, so bail out now
                lockedDocument.close();
                return new ResolvedExecutable(null, null, servletPath);
            }
            servletPath = servletPath.removeLastSegment();
            if (servletPath == XmldbURI.EMPTY_URI) {
                // no resource and no path segments left
                return new ResolvedExecutable(null, null, servletPath);
            }
        }
        return new ResolvedExecutable(lockedDocument, resource, servletPath);
    }

    private String getRequestContent(final HttpServletRequest request) throws IOException {

        String encoding = request.getCharacterEncoding();
        if (encoding == null) {
            encoding = DEFAULT_ENCODING;
        }

        final InputStream is = request.getInputStream();
        final Reader reader = new InputStreamReader(is, encoding);
        final StringWriter content = new StringWriter();
        final char ch[] = new char[4096];
        int len = 0;
        while ((len = reader.read(ch)) > -1) {
            content.write(ch, 0, len);
        }

        final String xml = content.toString();
        return xml;
    }

    /**
     * Compiles and executes an ad-hoc XQuery against the database and writes
     * its results to the response.
     *
     * TODO: pass request and response objects to XQuery.
     *
     * @param broker the database broker
     * @param transaction the database transaction
     * @param path the path of the request
     * @param options the parsed options of the request (query, variables, paging, etc.)
     * @param outputProperties the serialization properties
     * @param request the request
     * @param response the response
     *
     * @throws BadRequestException if a bad request is made
     * @throws PermissionDeniedException if the request has insufficient permissions
     * @throws XPathException if the XQuery raises an error
     */
    protected void search(final DBBroker broker, final Txn transaction, final String path,
        final QueryRequestOptions options, final Properties outputProperties,
        final HttpServletRequest request,
        final HttpServletResponse response) throws BadRequestException,
        PermissionDeniedException, XPathException {

        if (rejectForbiddenXQuery(broker, response)) {
            return;
        }

        tryWriteCachedResult(broker, transaction, options, outputProperties, response);

        final XmldbURI pathUri = XmldbURI.create(path);
        final Source source = new StringSource(options.query);
        final XQueryPool pool = broker.getBrokerPool().getXQueryPool();
        CompiledXQuery compiled = null;
        try {
            final XQuery xquery = broker.getBrokerPool().getXQueryService();
            compiled = pool.borrowCompiledXQuery(broker, source);

            final XQueryContext context = prepareSearchContext(broker, compiled, pathUri, options, request, response);

            final long compilationTime;
            if (compiled == null) {
                final long compilationStart = System.currentTimeMillis();
                compiled = xquery.compile(context, source);
                compilationTime = System.currentTimeMillis() - compilationStart;
            } else {
                compiled.getContext().updateContext(context);
                context.getWatchDog().reset();
                compilationTime = 0;
            }

            try {
                final long executeStart = System.currentTimeMillis();
                final Sequence resultSequence = xquery.execute(broker, compiled, null, outputProperties);
                final long executionTime = System.currentTimeMillis() - executeStart;

                if (LOG.isDebugEnabled()) {
                    LOG.debug("Found {} in {}ms.", resultSequence.getItemCount(), executionTime);
                }

                if (options.cache) {
                    final long sessionId = sessionManager.add(broker.getCurrentSubject().getId(), options.query, resultSequence);
                    outputProperties.setProperty(Serializer.PROPERTY_SESSION_ID, Long.toString(sessionId));
                    if (!response.isCommitted()) {
                        response.setHeader("X-Session-Id", Long.toString(sessionId));
                    }
                }

                writeResults(response, broker, transaction, resultSequence, options.howmany, options.start,
                        options.typed, outputProperties, options.wrap, new QueryTimings(compilationTime, executionTime));

            } finally {
                context.runCleanupTasks();
            }

        } catch (final IOException e) {
            throw new BadRequestException(e.getMessage(), e);
        } finally {
            if (compiled != null) {
                pool.returnCompiledXQuery(source, compiled);
            }
        }
    }

    /**
     * Checks whether XQuery submissions are forbidden for the current subject.
     *
     * If they are forbidden, the response status is set to 403 Forbidden.
     *
     * @param broker the database broker
     * @param response the response
     *
     * @return true if the XQuery submission was rejected, false otherwise
     */
    private boolean rejectForbiddenXQuery(final DBBroker broker, final HttpServletResponse response) {
        if (xquerySubmission == EXistServlet.FeatureEnabled.FALSE) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return true;
        } else if (xquerySubmission == EXistServlet.FeatureEnabled.AUTHENTICATED_USERS_ONLY) {
            final Subject currentSubject = broker.getCurrentSubject();
            if (!currentSubject.isAuthenticated() || currentSubject.getId() == RealmImpl.GUEST_ACCOUNT_ID) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return true;
            }
        }
        return false;
    }

    /**
     * If the request carries a session id for a previously cached query result,
     * writes that cached result to the response.
     *
     * The subsequent (re)execution of the query in {@link #search} then becomes
     * a no-op, as {@link #writeResults} does not overwrite an already committed
     * response.
     *
     * @param broker the database broker
     * @param transaction the database transaction
     * @param options the parsed options of the request
     * @param outputProperties the serialization properties
     * @param response the response
     *
     * @throws BadRequestException if an invalid session id is passed in the request
     */
    private void tryWriteCachedResult(final DBBroker broker, final Txn transaction,
            final QueryRequestOptions options, final Properties outputProperties,
            final HttpServletResponse response) throws BadRequestException {
        final String sessionIdParam = outputProperties.getProperty(Serializer.PROPERTY_SESSION_ID);
        if (sessionIdParam == null) {
            return;
        }
        try {
            final long sessionId = Long.parseLong(sessionIdParam);
            if (sessionId > -1) {
                final Sequence cached = sessionManager.get(broker.getCurrentSubject().getId(), options.query, sessionId);
                if (cached != null) {
                    LOG.debug("Returning cached query result");
                    writeResults(response, broker, transaction, cached, options.howmany, options.start,
                            options.typed, outputProperties, options.wrap, new QueryTimings(0, 0));
                } else {
                    LOG.debug("Cached query result not found. Probably timed out. Repeating query.");
                }
            }
        } catch (final NumberFormatException e) {
            throw new BadRequestException("Invalid session id passed in query request: " + sessionIdParam);
        }
    }

    /**
     * Prepares the XQuery context for an ad-hoc search query, reusing the
     * context of a cached compiled query where possible.
     *
     * @param broker the database broker
     * @param compiled the cached compiled query, or null if none was cached
     * @param pathUri the path of the request as an URI
     * @param options the parsed options of the request
     * @param request the request
     * @param response the response
     *
     * @return the prepared XQuery context
     *
     * @throws XPathException if the namespaces or variables cannot be declared
     */
    private XQueryContext prepareSearchContext(final DBBroker broker, final CompiledXQuery compiled,
            final XmldbURI pathUri, final QueryRequestOptions options,
            final HttpServletRequest request, final HttpServletResponse response) throws XPathException {
        final XQueryContext context;
        if (compiled == null) {
            context = new XQueryContext(broker.getBrokerPool());
        } else {
            context = compiled.getContext();
            context.prepareForReuse();
        }

        context.setStaticallyKnownDocuments(new XmldbURI[]{pathUri});
        context.setBaseURI(new AnyURIValue(pathUri.toString()));

        declareNamespaces(context, options.namespaces);
        declareVariables(context, options.variables, request, response);
        return context;
    }

    private void declareNamespaces(final XQueryContext context,
        final List<Namespace> namespaces) throws XPathException {

        if (namespaces == null) {
            return;
        }

        for (final Namespace ns : namespaces) {
            context.declareNamespace(ns.getPrefix(), ns.getUri());
        }
    }

    /**
     * Pass the request, response and session objects to the XQuery context.
     *
     * @param context
     * @param request
     * @param response
     * @throws XPathException
     */
    private HttpRequestWrapper declareVariables(final XQueryContext context,
        final ElementImpl variables, final HttpServletRequest request,
        final HttpServletResponse response) throws XPathException {

        final HttpRequestWrapper reqw = new HttpRequestWrapper(request, formEncoding, containerEncoding);
        final ResponseWrapper respw = new HttpResponseWrapper(response);
        context.setHttpContext(new XQueryContext.HttpContext(reqw, respw));

        //enable EXQuery Request Module (if present)
        try {
            if(xqueryContextExqueryRequestAttribute != null && cstrHttpServletRequestAdapter != null) {
                final HttpRequest exqueryRequestAdapter = cstrHttpServletRequestAdapter.apply(request, () -> (String)context.getBroker().getConfiguration().getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY));

                if(exqueryRequestAdapter != null) {
                    context.setAttribute(xqueryContextExqueryRequestAttribute, exqueryRequestAdapter);
                }
            }
        } catch(final Exception e) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("EXQuery Request Module is not present: {}", e.getMessage(), e);
            }
        }

        if (variables != null) {
            declareExternalAndXQJVariables(context, variables);
        }

        return reqw;
    }

    private void declareExternalAndXQJVariables(final XQueryContext context,
        final ElementImpl variables) throws XPathException {

        final ValueSequence varSeq = new ValueSequence();
        variables.selectChildren(new NameTest(Type.ELEMENT, new QName(Variable.xmlKey(), Namespaces.EXIST_NS)), varSeq);
        for (final SequenceIterator i = varSeq.iterate(); i.hasNext();) {
            final ElementImpl variable = (ElementImpl) i.nextItem();
            declareExternalVariable(context, variable);
        }
    }

    /**
     * Get the document a request addresses.
     *
     * A regular resource requires READ, as it always did. A stored XQuery is additionally resolved
     * on EXECUTE via {@link DBBroker#getResourceForExecution(XmldbURI)}, so that a caller
     * which may run it but not read it gets to run it. Reading a query as data — the {@code ?_source}
     * view — is unaffected and still validates READ separately.
     *
     * @return the locked document, or null if there is none at that path
     *
     * @throws PermissionDeniedException if the caller may neither read the resource nor, when it is a
     *     stored query, execute it
     */
    private @Nullable LockedDocument getResourceForRequest(final DBBroker broker, final XmldbURI uri) throws PermissionDeniedException {
        try {
            return broker.getXMLResource(uri, LockMode.READ_LOCK);
        } catch (final PermissionDeniedException readDenied) {
            final ExecutableResource executable;
            try {
                executable = broker.getResourceForExecution(uri);
            } catch (final PermissionDeniedException executeDenied) {
                // neither readable nor executable, so report the failure to read it
                throw readDenied;
            }

            if (executable == null) {
                return null;
            }

            // only a stored XQuery may be reached without READ; anything else is a data read. Require
            // the binary resource type as well as the mime type, so an XML resource merely labelled
            // application/xquery is not admitted (it would fail the BinaryDocument cast downstream)
            final DocumentImpl document = executable.document().getDocument();
            if (document.getResourceType() != DocumentImpl.BINARY_FILE
                    || !MimeType.XQUERY_TYPE.getName().equals(document.getMimeType())) {
                executable.close();
                throw readDenied;
            }

            return executable.document();
        }
    }

    /**
     * Holder for the parts of a variable {@code <qname>} element.
     */
    private record ParsedVariableQName(String localname, String prefix, String uri) {
    }

    /**
     * Declares a single external (XQJ) variable in the XQuery context from
     * its {@code <variable>} element.
     *
     * @param context the XQuery context to declare the variable in
     * @param variable the variable element
     *
     * @throws XPathException if the variable cannot be declared
     */
    private void declareExternalVariable(final XQueryContext context, final ElementImpl variable)
            throws XPathException {
        // get the QName of the variable
        final ElementImpl qname = (ElementImpl) variable.getFirstChild(new NameTest(Type.ELEMENT, new QName("qname", Namespaces.EXIST_NS)));
        final ParsedVariableQName parsed = parseVariableQName(qname);
        final String localname = parsed.localname();
        final String prefix = parsed.prefix();
        final String uri = parsed.uri();

        if (uri != null && prefix != null) {
            context.declareNamespace(prefix, uri);
        }

        if (localname == null) {
            return;
        }

        final QName q;
        if (prefix != null) {
            q = new QName(localname, uri, prefix);
        } else {
            q = new QName(localname, uri, XMLConstants.DEFAULT_NS_PREFIX);
        }

        // get serialized sequence
        final NodeImpl value = variable.getFirstChild(new NameTest(Type.ELEMENT, Marshaller.ROOT_ELEMENT_QNAME));
        final Sequence sequence;
        try {
            sequence = value == null ? Sequence.EMPTY_SEQUENCE : Marshaller.demarshall(value);
        } catch (final XMLStreamException xe) {
            throw new XPathException((Expression) null, xe.toString());
        }

        // now declare variable
        if (prefix != null) {
            context.declareVariable(q.getPrefix() + ":" + q.getLocalPart(), sequence);
        } else {
            context.declareVariable(q.getLocalPart(), sequence);
        }
    }

    /**
     * Parses the {@code <localname>}, {@code <namespace>} and {@code <prefix>}
     * children of a variable {@code <qname>} element.
     *
     * @param qname the qname element
     *
     * @return the parsed parts, any of which may be null if absent
     */
    private ParsedVariableQName parseVariableQName(final ElementImpl qname) {
        String localname = null;
        String prefix = null;
        String uri = null;
        NodeImpl child = (NodeImpl) qname.getFirstChild();
        while (child != null) {
            if ("localname".equals(child.getLocalName())) {
                localname = child.getStringValue();

            } else if ("namespace".equals(child.getLocalName())) {
                uri = child.getStringValue();

            } else if ("prefix".equals(child.getLocalName())) {
                prefix = child.getStringValue();

            }
            child = (NodeImpl) child.getNextSibling();
        }
        return new ParsedVariableQName(localname, prefix, uri);
    }

    /**
     * Directly execute an XQuery stored as a binary document in the database.
     *
     * @throws PermissionDeniedException
     */
    private void executeXQuery(final DBBroker broker, final Txn transaction, final DocumentImpl resource,
            final HttpServletRequest request, final HttpServletResponse response,
            final Properties outputProperties, final String servletPath, final String pathInfo)
            throws XPathException, BadRequestException, PermissionDeniedException {

        final Source source = new DBSource(broker.getBrokerPool(), (BinaryDocument) resource, true);
        final XQueryPool pool = broker.getBrokerPool().getXQueryPool();
        CompiledXQuery compiled = null;
        try {
            final XQuery xquery = broker.getBrokerPool().getXQueryService();
            compiled = pool.borrowCompiledXQuery(broker, source);

            final XQueryContext context;
            if (compiled == null) {
                context = new XQueryContext(broker.getBrokerPool());
            } else {
                context = compiled.getContext();
                context.prepareForReuse();
            }

            // a caller which may execute but not read the query must not learn anything about its
            // source from a failure. Recomputed per request from the current subject, as the compiled
            // query is pooled and shared between users, and set before the query is compiled, since a
            // compile error never reaches XQuery#execute (which computes the level for runtime errors)
            final ErrorDisclosure disclosure = ErrorDisclosure.of(source, broker.getCurrentSubject());
            context.setErrorDisclosure(disclosure);

            // X-XQuery-Cached reveals whether another user recently executed this shared query; do not
            // disclose it to a read-blind caller (plan §4.6 — suppress X-XQuery-* for GENERIC)
            if (disclosure == ErrorDisclosure.FULL) {
                response.setHeader("X-XQuery-Cached", Boolean.toString(compiled != null));
            }

            // TODO: don't hardcode this?
            context.setModuleLoadPath(
                    XmldbURI.EMBEDDED_SERVER_URI.append(
                            resource.getCollection().getURI()).toString());

            context.setStaticallyKnownDocuments(
                    new XmldbURI[]{resource.getCollection().getURI()});

            final HttpRequestWrapper reqw = declareVariables(context, null, request, response);
            reqw.setServletPath(servletPath);
            reqw.setPathInfo(pathInfo);

            try {
                final long compilationTime;
                if (compiled == null) {
                    try {
                        final long compilationStart = System.currentTimeMillis();
                        compiled = xquery.compile(context, source);
                        compilationTime = System.currentTimeMillis() - compilationStart;
                    } catch (final IOException e) {
                        throw new BadRequestException("Failed to read query from " + resource.getURI(), e);
                    }
                } else {
                    compilationTime = 0;
                }

                DebuggeeFactory.checkForDebugRequest(request, context);

                boolean wrap = outputProperties.getProperty("_wrap") != null
                        && "yes".equals(outputProperties.getProperty("_wrap"));

                try {
                    final long executeStart = System.currentTimeMillis();
                    final Sequence result = xquery.execute(broker, compiled, null, outputProperties);
                    writeResults(response, broker, transaction, result, -1, 1, false, outputProperties, wrap, new QueryTimings(compilationTime, System.currentTimeMillis() - executeStart));

                } finally {
                    context.runCleanupTasks();
                }
            } catch (final XPathException e) {
                // compile and runtime failures alike are filtered: a read-blind caller learns only
                // that the execution failed, the real error is logged with a correlation id
                throw ErrorDisclosure.disclose(context, e);
            } catch (final BadRequestException | PermissionDeniedException | RuntimeException e) {
                // a failure of an authorized query which is not an XPathException — serialization
                // (BadRequestException), a runtime permission failure, or any RuntimeException — still
                // carries source-derived detail, so branch on the disclosure level, not the Java type
                final XPathException generic = ErrorDisclosure.discloseGeneric(context, e);
                if (generic != null) {
                    throw generic;
                }
                throw e;
            }
        } finally {
            if (compiled != null) {
                pool.returnCompiledXQuery(source, compiled);
            }
        }
    }

    /**
     * Directly execute an XProc stored as a XML document in the database.
     *
     * @throws PermissionDeniedException
     */
    private void executeXProc(final DBBroker broker, final Txn transaction, final DocumentImpl resource,
            final HttpServletRequest request, final HttpServletResponse response,
            final Properties outputProperties, final String servletPath, final String pathInfo)
            throws XPathException, BadRequestException, PermissionDeniedException {

        final URLSource source = new URLSource(this.getClass().getResource("run-xproc.xq"));
        final XQueryPool pool = broker.getBrokerPool().getXQueryPool();
        CompiledXQuery compiled = null;

        try {
            final XQuery xquery = broker.getBrokerPool().getXQueryService();
            compiled = pool.borrowCompiledXQuery(broker, source);

            XQueryContext context;
            if (compiled == null) {
                context = new XQueryContext(broker.getBrokerPool());
            } else {
                context = compiled.getContext();
                context.prepareForReuse();
            }

            declareXProcVariables(context, request, resource);

            // TODO: don't hardcode this?
            context.setModuleLoadPath(
                    XmldbURI.EMBEDDED_SERVER_URI.append(
                            resource.getCollection().getURI()).toString());

            context.setStaticallyKnownDocuments(
                    new XmldbURI[]{resource.getCollection().getURI()});

            final HttpRequestWrapper reqw = declareVariables(context, null, request, response);
            reqw.setServletPath(servletPath);
            reqw.setPathInfo(pathInfo);

            final long compilationTime;
            if (compiled == null) {
                try {
                    final long compilationStart = System.currentTimeMillis();
                    compiled = xquery.compile(context, source);
                    compilationTime = System.currentTimeMillis() - compilationStart;
                } catch (final IOException e) {
                    throw new BadRequestException("Failed to read query from "
                            + source.getURL(), e);
                }
            } else {
                compilationTime = 0;
            }

            try {
                final long executeStart = System.currentTimeMillis();
                final Sequence result = xquery.execute(broker, compiled, null, outputProperties);
                writeResults(response, broker, transaction, result, -1, 1, false, outputProperties, false, new QueryTimings(compilationTime, System.currentTimeMillis() - executeStart));
            } finally {
                context.runCleanupTasks();

            }
        } finally {
            if (compiled != null) {
                pool.returnCompiledXQuery(source, compiled);
            }
        }
    }

    /**
     * Declares the input variables of the {@code run-xproc.xq} driver from the
     * request parameters.
     *
     * @param context the XQuery context to declare the variables in
     * @param request the request
     * @param resource the XProc pipeline resource
     *
     * @throws XPathException if a variable cannot be declared
     */
    private void declareXProcVariables(final XQueryContext context, final HttpServletRequest request,
            final DocumentImpl resource) throws XPathException {
        context.declareVariable("pipeline", resource.getURI().toString());

        final String stdin = request.getParameter("stdin");
        context.declareVariable("stdin", stdin == null ? "" : stdin);

        final String debug = request.getParameter("debug");
        context.declareVariable("debug", debug == null ? "0" : "1");

        final String bindings = request.getParameter("bindings");
        context.declareVariable("bindings", bindings == null ? "<bindings/>" : bindings);

        final String autobind = request.getParameter("autobind");
        context.declareVariable("autobind", autobind == null ? "0" : "1");

        final String options = request.getParameter("options");
        context.declareVariable("options", options == null ? "<options/>" : options);
    }

    public void setCreatedAndLastModifiedHeaders(
        final HttpServletResponse response, final long created, final long lastModified) {

        response.addDateHeader("Last-Modified", roundUpToWholeSecond(lastModified));
        response.addDateHeader("Created", roundUpToWholeSecond(created));
    }

    /**
     * Jetty ignores the milliseconds component -
     * https://bugs.eclipse.org/bugs/show_bug.cgi?id=342712 So lets work
     * around this by rounding up to the nearest whole second
     *
     * @param time the time in milliseconds
     *
     * @return the time rounded up to the nearest whole second
     */
    private static long roundUpToWholeSecond(final long time) {
        final long millisComp = time % 1000;
        if (millisComp > 0) {
            return time + 1000 - millisComp;
        }
        return time;
    }

    // writes out a resource, uses asMimeType as the specified mime-type or if
    // null uses the type of the resource
    private void writeResourceAs(final DocumentImpl resource, final DBBroker broker,
        final String stylesheet, final String encoding, final String asMimeType,
        final Properties outputProperties, final HttpServletRequest request,
        final HttpServletResponse response) throws BadRequestException,
        PermissionDeniedException, IOException {

        // Do we have permission to read the resource
        if (!resource.getPermissions().validate(broker.getCurrentSubject(), Permission.READ)) {
            throw new PermissionDeniedException("Not allowed to read resource");
        }

        //get the document metadata
        final long lastModified = resource.getLastModified();
        setCreatedAndLastModifiedHeaders(response, resource.getCreated(), lastModified);

        // HTTP 1.1 RFC 2616 Section 14.25 - handle If-Modified-Since request header
        if (isNotModifiedSince(request, lastModified)) {
            response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
            return;
        }

        if (resource.getResourceType() == DocumentImpl.BINARY_FILE) {
            writeBinaryResource(resource, broker, encoding, asMimeType, response);
        } else {
            writeXmlResource(resource, broker, stylesheet, encoding, asMimeType, outputProperties, request, response);
        }
    }

    /**
     * Determines whether the resource should be reported as unmodified in
     * response to the {@code If-Modified-Since} request header
     * (HTTP 1.1 RFC 2616 Section 14.25).
     *
     * @param request the request
     * @param lastModified the last modified time of the resource
     *
     * @return true if a 304 (Not Modified) response should be sent
     */
    private static boolean isNotModifiedSince(final HttpServletRequest request, final long lastModified) {
        try {
            final long ifModifiedSince = request.getDateHeader("If-Modified-Since");
            // a) A date which is later than the server's current time is invalid.
            // b) If the variant has been modified since the If-Modified-Since date,
            //    the response is exactly the same as for a normal GET.
            // c) If the variant has not been modified since a valid If-Modified-Since
            //    date, the server SHOULD return a 304 (Not Modified) response.
            return ifModifiedSince > -1
                    && ifModifiedSince <= System.currentTimeMillis()
                    && lastModified <= ifModifiedSince;
        } catch (final IllegalArgumentException iae) {
            LOG.warn("Illegal If-Modified-Since HTTP Header sent on request, ignoring. {}", iae.getMessage(), iae);
            return false;
        }
    }

    /**
     * Writes a binary resource to the response.
     *
     * @param resource the binary resource
     * @param broker the database broker
     * @param encoding the character encoding
     * @param asMimeType the mime-type to use, or null to use the resource's own mime-type
     * @param response the response
     *
     * @throws IOException if an I/O error occurs
     */
    private void writeBinaryResource(final DocumentImpl resource, final DBBroker broker,
            final String encoding, final String asMimeType, final HttpServletResponse response) throws IOException {
        final String mimeType = asMimeType == null ? resource.getMimeType() : asMimeType;

        if (mimeType.startsWith("text/")) {
            response.setContentType(mimeType + "; charset=" + encoding);
        } else {
            response.setContentType(mimeType);
        }

        // As HttpServletResponse.setContentLength is limited to integers,
        // (see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4187336)
        // next sentence:
        //	response.setContentLength(resource.getContentLength());
        // must be set so
        response.addHeader("Content-Length", Long.toString(resource.getContentLength()));
        final OutputStream os = response.getOutputStream();
        broker.readBinaryResource((BinaryDocument) resource, os);
        os.flush();
    }

    /**
     * Serializes an XML resource to the response.
     *
     * @param resource the XML resource
     * @param broker the database broker
     * @param stylesheet the stylesheet to apply, or null
     * @param encoding the character encoding
     * @param asMimeType the mime-type to use, or null to derive it
     * @param outputProperties the serialization properties
     * @param request the request
     * @param response the response
     *
     * @throws BadRequestException if serialization fails
     * @throws IOException if an I/O error occurs
     */
    private void writeXmlResource(final DocumentImpl resource, final DBBroker broker, final String stylesheet,
            final String encoding, final String asMimeType, final Properties outputProperties,
            final HttpServletRequest request, final HttpServletResponse response)
            throws BadRequestException, IOException {

        SAXSerializer sax = null;
        final Serializer serializer = broker.borrowSerializer();

        //setup the http context
        final HttpRequestWrapper reqw = new HttpRequestWrapper(request, formEncoding, containerEncoding);
        final HttpResponseWrapper resw = new HttpResponseWrapper(response);
        serializer.setHttpContext(new XQueryContext.HttpContext(reqw, resw));

        // Serialize the document
        try {
            sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);

            // use a stylesheet if specified in query parameters
            if (stylesheet != null) {
                serializer.setStylesheet(resource, stylesheet);
            }
            serializer.setProperties(outputProperties);

            final String mimeType = resolveXmlContentType(resource, serializer, asMimeType, encoding, response);
            if (mimeType.equals(MimeType.HTML_TYPE.getName())) {
                outputProperties.setProperty("method", "xhtml");
                outputProperties.setProperty("media-type", "text/html; charset=" + encoding);
                outputProperties.setProperty("indent", "yes");
                outputProperties.setProperty("omit-xml-declaration", "no");
            }

            final OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), encoding);
            sax.setOutput(writer, outputProperties);
            serializer.setSAXHandlers(sax, sax);

            serializer.toSAX(resource);

            writer.flush();
            writer.close(); // DO NOT use in try-write-resources, otherwise ther response stream is always closed, and we can't report the errors
        } catch (final SAXException saxe) {
            LOG.warn(saxe);
            throw new BadRequestException("Error while serializing XML: " + saxe.getMessage());
        } catch (final TransformerConfigurationException e) {
            LOG.warn(e);
            throw new BadRequestException(e.getMessageAndLocation());
        } finally {
            if (sax != null) {
                SerializerPool.getInstance().returnObject(sax);
            }
            broker.returnSerializer(serializer);
        }
    }

    /**
     * Determines the response Content-Type for an XML resource, sets it on the
     * response, and returns the resolved media type.
     *
     * @param resource the XML resource
     * @param serializer the serializer that will serialize the resource
     * @param asMimeType the mime-type to use, or null to derive it
     * @param encoding the character encoding
     * @param response the response
     *
     * @return the resolved media type
     */
    private String resolveXmlContentType(final DocumentImpl resource, final Serializer serializer,
            final String asMimeType, final String encoding, final HttpServletResponse response) {
        if (asMimeType != null) { // was a mime-type specified?
            response.setContentType(asMimeType + "; charset=" + encoding);
            return asMimeType;
        }

        final String mimeType;
        if (serializer.isStylesheetApplied() || serializer.hasXSLPi(resource) != null) {
            final String stylesheetMediaType = serializer.getStylesheetProperty(OutputKeys.MEDIA_TYPE);
            if (!useDynamicContentType || stylesheetMediaType == null) {
                mimeType = MimeType.HTML_TYPE.getName();
            } else {
                mimeType = stylesheetMediaType;
            }

            if (LOG.isDebugEnabled()) {
                LOG.debug("media-type: {}", mimeType);
            }
        } else {
            mimeType = resource.getMimeType();
        }
        response.setContentType(mimeType + "; charset=" + encoding);
        return mimeType;
    }

    /**
     * Writes an XPathException to the http response.
     *
     * The exception is written as XML or HTML depending on the
     * given media type of the response.
     *
     * @param response the response
     * @param httpStatusCode the HTTP status code to set on the response
     * @param mimeType the media type of the response
     * @param encoding the character encoding
     * @param query the query that caused the exception, or null
     * @param path the path of the request
     * @param e the exception to write
     *
     * @throws IOException if an I/O error occurs
     */
    private void writeQueryError(final HttpServletResponse response, final int httpStatusCode,
        final String mimeType, final String encoding, final String query,
        final String path, final XPathException e) throws IOException {
        if (MimeType.XML_TYPE.getName().equals(mimeType)) {
            writeXPathException(response, httpStatusCode, encoding, query, path, e);
        } else {
            writeXPathExceptionHtml(response, httpStatusCode, encoding, query, path, e);
        }
    }

    private void writeXPathExceptionHtml(final HttpServletResponse response,
        final int httpStatusCode, final String encoding, final String query,
        final String path, final XPathException e) throws IOException {

        if (!response.isCommitted()) {
            response.reset();
        }

        response.setStatus(httpStatusCode);

        response.setContentType(MimeType.HTML_TYPE.getName() + "; charset=" + encoding);

        final OutputStreamWriter writer = new OutputStreamWriter(response.getOutputStream(), encoding);
        writer.write(QUERY_ERROR_HEAD);
        writer.write("<p class=\"path\"><span class=\"high\">Path</span>: ");
        writer.write("<a href=\"");
        writer.write(path);
        writer.write("\">");
        writer.write(path);
        writer.write("</a>");

        writer.write("<p class=\"errmsg\">");
        final String message = e.getMessage() == null ? e.toString() : e.getMessage();
        writer.write(XMLUtil.encodeAttrMarkup(message));
        writer.write("");
        if (query != null) {
            writer.write("<span class=\"high\">Query</span>:<pre>");
            writer.write(XMLUtil.encodeAttrMarkup(query));
            writer.write("</pre>");
        }
        writer.write("</body></html>");

        writer.flush();
        writer.close();
    }

    /**
     * @param response
     * @param encoding
     * @param query
     * @param path
     * @param e
     */
    private void writeXPathException(final HttpServletResponse response,
        final int httpStatusCode, final String encoding, final String query,
        final String path, final XPathException e) throws IOException {

        if (!response.isCommitted()) {
            response.reset();
        }

        response.setStatus(httpStatusCode);

        response.setContentType(MimeType.XML_TYPE.getName() + "; charset=" + encoding);

        try(final OutputStreamWriter writer =
                new OutputStreamWriter(response.getOutputStream(), encoding)) {

            writer.write("<?xml version=\"1.0\" ?>");
            writer.write("<exception><path>");
            writer.write(XMLUtil.encodeAttrMarkup(path));
            writer.write("</path>");
            writer.write("<message>");
            final String message = e.getMessage() == null ? e.toString() : e.getMessage();
            writer.write(XMLUtil.encodeAttrMarkup(message));
            writer.write("</message>");
            if (query != null) {
                writer.write("<query>");
                writer.write(XMLUtil.encodeAttrMarkup(query));
                writer.write("</query>");
            }
            writer.write("</exception>");
        }
    }

    /**
     * Writes the XUpdate results to the http response.
     *
     * @param response the http response to write the result to
     * @param encoding the character encoding
     * @param updateCount the number of updates performed
     *
     * @throws IOException if an I/O error occurs
     */
    private void writeXUpdateResult(final HttpServletResponse response,
        final String encoding, final long updateCount) throws IOException {

        response.setContentType(MimeType.XML_TYPE.getName() + "; charset=" + encoding);

        final OutputStreamWriter writer =
                new OutputStreamWriter(response.getOutputStream(), encoding);

        writer.write("<?xml version=\"1.0\" ?>");
        writer.write("<exist:modifications xmlns:exist=\""
                + Namespaces.EXIST_NS + "\" count=\"" + updateCount + "\">");
        writer.write(updateCount + " modifications processed.");
        writer.write("</exist:modifications>");

        writer.flush();
        writer.close();
    }

    /**
     * Write the details of a Collection to the http response.
     *
     * @param response the http response to write the result to
     * @param encoding the character encoding
     * @param broker the database broker
     * @param collection the collection to write
     *
     * @throws IOException if an I/O error occurs
     * @throws PermissionDeniedException if there are insufficient privildged for the caller
     * @throws LockException if a lock error occurs
     */
    protected void writeCollection(final HttpServletResponse response,
        final String encoding, final DBBroker broker, final Collection collection)
            throws IOException, PermissionDeniedException, LockException {

        response.setContentType(MimeType.XML_TYPE.getName() + "; charset=" + encoding);

        setCreatedAndLastModifiedHeaders(response, collection.getCreated(), collection.getCreated());

        final OutputStreamWriter writer =
                new OutputStreamWriter(response.getOutputStream(), encoding);

        SAXSerializer serializer = null;

        try {
            serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);

            serializer.setOutput(writer, defaultProperties);
            final AttributesImpl attrs = new AttributesImpl();

            serializer.startDocument();
            serializer.startPrefixMapping("exist", Namespaces.EXIST_NS);
            serializer.startElement(Namespaces.EXIST_NS, "result",
                    "exist:result", attrs);

            attrs.addAttribute("", "name", "name", "CDATA", collection.getURI()
                    .toString());
            // add an attribute for the creation date as an xs:dateTime
            try {
                final DateTimeValue dtCreated =
                        new DateTimeValue(new Date(collection.getCreated()));
                attrs.addAttribute("", "created", "created", "CDATA",
                        dtCreated.getStringValue());
            } catch (final XPathException e) {
                // fallback to long value
                attrs.addAttribute("", "created", "created", "CDATA",
                        String.valueOf(collection.getCreated()));
            }

            addPermissionAttributes(attrs, collection.getPermissionsNoLock());

            serializer.startElement(Namespaces.EXIST_NS, "collection",
                    "exist:collection", attrs);

            for (final Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext();) {
                final XmldbURI child = i.next();
                final Collection childCollection = broker.getCollection(collection
                        .getURI().append(child));
                if (childCollection != null
                        && childCollection.getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)) {
                    attrs.clear();
                    attrs.addAttribute("", "name", "name", "CDATA", child.toString());

                    // add an attribute for the creation date as an xs:dateTime
                    try {
                        final DateTimeValue dtCreated =
                                new DateTimeValue(new Date(childCollection.getCreated()));
                        attrs.addAttribute("", "created", "created", "CDATA", dtCreated.getStringValue());
                    } catch (final XPathException e) {
                        // fallback to long value
                        attrs.addAttribute("", "created", "created", "CDATA",
                                String.valueOf(childCollection.getCreated()));
                    }

                    addPermissionAttributes(attrs, childCollection.getPermissionsNoLock());
                    serializer.startElement(Namespaces.EXIST_NS, "collection", "exist:collection", attrs);
                    serializer.endElement(Namespaces.EXIST_NS, "collection", "exist:collection");
                }
            }

            for (final Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext();) {
                final DocumentImpl doc = i.next();
                if (doc.getPermissions().validate(broker.getCurrentSubject(), Permission.READ)) {
                    final XmldbURI resource = doc.getFileURI();
                    attrs.clear();
                    attrs.addAttribute("", "name", "name", "CDATA", resource.toString());

                    // add an attribute for the creation date as an xs:dateTime
                    try {
                        final DateTimeValue dtCreated =
                                new DateTimeValue(new Date(doc.getCreated()));
                        attrs.addAttribute("", "created", "created", "CDATA",
                                dtCreated.getStringValue());
                    } catch (final XPathException e) {
                        // fallback to long value
                        attrs.addAttribute("", "created", "created", "CDATA",
                                String.valueOf(doc.getCreated()));
                    }

                    // add an attribute for the last modified date as an
                    // xs:dateTime
                    try {
                        final DateTimeValue dtLastModified = new DateTimeValue(null,
                                new Date(doc.getLastModified()));
                        attrs.addAttribute("", "last-modified",
                                "last-modified", "CDATA", dtLastModified.getStringValue());
                    } catch (final XPathException e) {
                        // fallback to long value
                        attrs.addAttribute("", "last-modified",
                                "last-modified", "CDATA", String.valueOf(doc.getLastModified()));
                    }

                    addPermissionAttributes(attrs, doc.getPermissions());
                    serializer.startElement(Namespaces.EXIST_NS, "resource", "exist:resource", attrs);
                    serializer.endElement(Namespaces.EXIST_NS, "resource", "exist:resource");
                }
            }

            serializer.endElement(Namespaces.EXIST_NS, "collection", "exist:collection");
            serializer.endElement(Namespaces.EXIST_NS, "result", "exist:result");

            serializer.endDocument();

            writer.flush();
            writer.close();

        } catch (final SAXException e) {
            // should never happen
            LOG.warn("Error while serializing collection contents: {}", e.getMessage(), e);
        } finally {
            if (serializer != null) {
                SerializerPool.getInstance().returnObject(serializer);
            }
        }
    }

    protected void addPermissionAttributes(final AttributesImpl attrs, final Permission perm) {
        attrs.addAttribute("", "owner", "owner", "CDATA", perm.getOwner().getName());
        attrs.addAttribute("", "group", "group", "CDATA", perm.getGroup().getName());
        attrs.addAttribute("", "permissions", "permissions", "CDATA", perm.toString());
    }

    /**
     * Holder for the compilation and execution times of a query, in milliseconds.
     */
    private record QueryTimings(long compilation, long execution) {
    }

    protected void writeResults(final HttpServletResponse response, final DBBroker broker, final Txn transaction,
            final Sequence results, final int howmany, final int start, final boolean typed,
            final Properties outputProperties, final boolean wrap, final QueryTimings timings)
            throws BadRequestException {

        // some xquery functions can write directly to the output stream
        // (response:stream-binary() etc...)
        // so if output is already written then dont overwrite here
        if (response.isCommitted()) {
            return;
        }

        // calculate number of results to return
        final int effectiveHowmany;
        if (!results.isEmpty()) {
            final int rlen = results.getItemCount();
            if ((start < 1) || (start > rlen)) {
                throw new BadRequestException("Start parameter out of range");
            }
            // FD : correct bound evaluation
            if (((howmany + start) > rlen) || (howmany <= 0)) {
                effectiveHowmany = rlen - start + 1;
            } else {
                effectiveHowmany = howmany;
            }
        } else {
            effectiveHowmany = 0;
        }
        final String method = outputProperties.getProperty(SERIALIZATION_METHOD_PROPERTY, "xml");

        if ("json".equals(method)) {
            writeResultJSON(response, broker, results, effectiveHowmany, start, outputProperties, timings.compilation(), timings.execution());
        } else {
            writeResultXML(response, broker, results, effectiveHowmany, start, typed, outputProperties, wrap, timings.compilation(), timings.execution());
        }

    }

    private static String getEncoding(final Properties outputProperties) {
        return outputProperties.getProperty(OutputKeys.ENCODING, DEFAULT_ENCODING);
    }

    private void writeResultXML(final HttpServletResponse response,
        final DBBroker broker, final Sequence results, final int howmany,
        final int start, final boolean typed, final Properties outputProperties,
        final boolean wrap, final long compilationTime, final long executionTime) throws BadRequestException {

        // serialize the results to the response output stream
        outputProperties.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
        try {

            // set output headers
            final String encoding = getEncoding(outputProperties);
            if (!response.containsHeader("Content-Type")) {
                String mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE);
                if (mimeType != null) {
                    final int semicolon = mimeType.indexOf(';');
                    if (semicolon != Constants.STRING_NOT_FOUND) {
                        mimeType = mimeType.substring(0, semicolon);
                    }
                    if (wrap) {
                        mimeType = "application/xml";
                    }
                    response.setContentType(mimeType + "; charset=" + encoding);
                }
            }
            if (wrap) {
                outputProperties.setProperty("method", "xml");
            }
            final Writer writer = new OutputStreamWriter(response.getOutputStream(), encoding);
            final XQuerySerializer serializer = new XQuerySerializer(broker, outputProperties, writer);

            //Marshaller.marshall(broker, results, start, howmany, serializer.getContentHandler());
            serializer.serialize(results, start, howmany, wrap, typed, compilationTime, executionTime);

            writer.flush();
            writer.close();

        } catch (final SAXException e) {
            LOG.warn(e);
            throw new BadRequestException("Error while serializing xml: "
                    + e, e);
        } catch (final Exception e) {
            LOG.warn(e.getMessage(), e);
            throw new BadRequestException("Error while serializing xml: "
                    + e, e);
        }
    }

    /**
     * Set the response Content-Type for a JSON REST result, unless one has
     * already been set. The default media-type carried in the serialization
     * properties is the XML type, so for a JSON result fall back to
     * application/json unless the query requested a different (non-XML-default)
     * media-type via output:media-type.
     *
     * @param response the HTTP response
     * @param outputProperties the serialization properties
     */
    private static void setJsonResponseContentType(final HttpServletResponse response, final Properties outputProperties) {
        if (response.containsHeader("Content-Type")) {
            return;
        }
        String mimeType = outputProperties.getProperty(OutputKeys.MEDIA_TYPE);
        if (mimeType == null || MimeType.XML_TYPE.getName().equals(mimeType)) {
            mimeType = MimeType.JSON_TYPE.getName();
        } else {
            final int semicolon = mimeType.indexOf(';');
            if (semicolon != Constants.STRING_NOT_FOUND) {
                mimeType = mimeType.substring(0, semicolon);
            }
        }
        response.setContentType(mimeType + "; charset=" + getEncoding(outputProperties));
    }

    private void writeResultJSON(final HttpServletResponse response,
        final DBBroker broker, final Sequence results, final int howmany,
        final int start, final Properties outputProperties, final long compilationTime, final long executionTime)
            throws BadRequestException {

        // calculate number of results to return
        final int rlen = results.getItemCount();
        final int effectiveHowmany;
        if (!results.isEmpty()) {
            if ((start < 1) || (start > rlen)) {
                throw new BadRequestException("Start parameter out of range");
            }
            // FD : correct bound evaluation
            if (((howmany + start) > rlen) || (howmany <= 0)) {
                effectiveHowmany = rlen - start + 1;
            } else {
                effectiveHowmany = howmany;
            }
        } else {
            effectiveHowmany = 0;
        }

        // The XML path honors output:media-type, but the JSON path historically
        // left Content-Type unset, so output:media-type was silently ignored for
        // JSON results. Set it here, before the output stream is opened.
        setJsonResponseContentType(response, outputProperties);

        final Serializer serializer = broker.borrowSerializer();
        outputProperties.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
        try {
            serializer.setProperties(outputProperties);
            try (Writer writer = new OutputStreamWriter(response.getOutputStream(), getEncoding(outputProperties))) {
                final JSONObject root = new JSONObject();
                root.addObject(new JSONSimpleProperty("start", Integer.toString(start), true));
                root.addObject(new JSONSimpleProperty("count", Integer.toString(effectiveHowmany), true));
                root.addObject(new JSONSimpleProperty("hits", Integer.toString(results.getItemCount()), true));
                if (outputProperties.getProperty(Serializer.PROPERTY_SESSION_ID) != null) {
                    root.addObject(new JSONSimpleProperty("session",
                            outputProperties.getProperty(Serializer.PROPERTY_SESSION_ID)));
                }
                root.addObject(new JSONSimpleProperty("compilationTime", Long.toString(compilationTime), true));
                root.addObject(new JSONSimpleProperty("executionTime", Long.toString(executionTime), true));

                final JSONObject data = new JSONObject("data");
                root.addObject(data);

                final int startIndex = start - 1;
                Item item;
                for (int i = startIndex; i < startIndex + effectiveHowmany; i++) {
                    item = results.itemAt(i);
                    if (Type.subTypeOf(item.getType(), Type.NODE)) {
                        final NodeValue value = (NodeValue) item;
                        JSONValue json;
                        if ("json".equals(outputProperties.getProperty("method", "xml"))) {
                            json = new JSONValue(serializer.serialize(value), false);
                            json.setSerializationDataType(JSONNode.SerializationDataType.AS_LITERAL);
                        } else {
                            json = new JSONValue(serializer.serialize(value));
                            json.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
                        }
                        data.addObject(json);
                    } else {
                        final JSONValue json = new JSONValue(item.getStringValue());
                        json.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
                        data.addObject(json);
                    }
                }

                root.serialize(writer, true);

                writer.flush();
            }
        } catch (final IOException | XPathException | SAXException e) {
            throw new BadRequestException("Error while serializing xml: " + e, e);
        } finally {
            broker.returnSerializer(serializer);
        }
    }

    private boolean isExecutableType(final DocumentImpl resource) {
        return (
            resource != null
            && (
                    MimeType.XQUERY_TYPE.getName().equals(resource.getMimeType()) // xquery
                    || MimeType.XPROC_TYPE.getName().equals(resource.getMimeType()) // xproc
            )
        );
    }
}
