/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2015 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.http;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerConfigurationException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.Namespaces;
import org.exist.collections.Collection;
import org.exist.collections.IndexInfo;
import org.exist.collections.triggers.TriggerException;
import org.exist.debuggee.DebuggeeFactory;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DefaultDocumentSet;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.persistent.DocumentMetadata;
import org.exist.dom.persistent.MutableDocumentSet;
import org.exist.dom.QName;
import org.exist.dom.persistent.XMLUtil;

import static org.exist.http.RESTServerParameter.*;

import org.exist.http.servlets.HttpRequestWrapper;
import org.exist.http.servlets.HttpResponseWrapper;
import org.exist.http.servlets.ResponseWrapper;
import org.exist.http.urlrewrite.XQueryURLRewrite;
import org.exist.dom.memtree.ElementImpl;
import org.exist.dom.memtree.NodeImpl;
import org.exist.dom.memtree.SAXAdapter;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.source.DBSource;
import org.exist.source.Source;
import org.exist.source.StringSource;
import org.exist.source.URLSource;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.storage.XQueryPool;
import org.exist.storage.lock.Lock;
import org.exist.storage.serializers.EXistOutputKeys;
import static org.exist.storage.serializers.EXistOutputKeys.YES;
import static org.exist.storage.serializers.EXistOutputKeys.NO;

import org.exist.storage.serializers.Serializer;
import org.exist.storage.serializers.Serializer.HttpContext;
import org.exist.storage.txn.Txn;
import org.exist.util.*;
import com.evolvedbinary.j8fu.Either;
import com.evolvedbinary.j8fu.tuple.Tuple2;
import com.evolvedbinary.j8fu.tuple.Tuple3;
import org.exist.util.io.FilterInputStreamCacheFactory.FilterInputStreamCacheConfiguration;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.util.serializer.json.*;
import org.exist.xmldb.XmldbURI;
import org.exist.xqj.Marshaller;
import org.exist.xquery.CompiledXQuery;
import org.exist.xquery.Constants;
import org.exist.xquery.NameTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQuery;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.request.RequestModule;
import org.exist.xquery.functions.response.ResponseModule;
import org.exist.xquery.functions.session.SessionModule;
import org.exist.xquery.value.*;
import org.exist.xupdate.Modification;
import org.exist.xupdate.XUpdateProcessor;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.AttributesImpl;
import org.xml.sax.helpers.XMLFilterImpl;

/**
 * @author wolf
 * @author ljo
 * @author adam
 * @author gev
 */
public class RESTServer {

    private final static Logger LOG = LogManager.getLogger(RESTServer.class);

    private final static int DEFAULT_HOW_MANY = 10;
    private final static int DEFAULT_START = 1;
    private final static boolean DEFAULT_TYPED = false;
    private final static boolean DEFAULT_RECEIVED_QUERY_WRAP = true; // whether to wrap queries that are sent to the REST Server
    private final static boolean DEFAULT_STORED_QUERY_WRAP = false;  // whether to wrap queries that are stored in the DB
    private final static boolean DEFAULT_SOURCE = false;
    private final static boolean DEFAULT_CACHE = false;
    private final static Charset DEFAULT_ENCODING = UTF_8;

    public final static String SERIALIZATION_METHOD_PROPERTY = "output-as";

    // Should we not obey the instance's defaults? /ljo
    protected final static Properties defaultProperties = new Properties();
    static {
        defaultProperties.setProperty(OutputKeys.INDENT, YES);
        defaultProperties.setProperty(OutputKeys.ENCODING, DEFAULT_ENCODING.name());
        defaultProperties.setProperty(OutputKeys.MEDIA_TYPE, MimeType.XML_TYPE.getName());
        defaultProperties.setProperty(EXistOutputKeys.EXPAND_XINCLUDES, YES);
        defaultProperties.setProperty(EXistOutputKeys.HIGHLIGHT_MATCHES, "elements");
        defaultProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, YES);
    }

    public final static Properties defaultOutputKeysProperties = new Properties();
    static {
        defaultOutputKeysProperties.setProperty(OutputKeys.INDENT, YES);
        defaultOutputKeysProperties.setProperty(OutputKeys.ENCODING, DEFAULT_ENCODING.name());
        defaultOutputKeysProperties.setProperty(OutputKeys.MEDIA_TYPE, MimeType.XML_TYPE.getName());
    }

    private final static String QUERY_ERROR_HEAD = "<html>" + "<head>"
            + "<title>Query Error</title>" + "<style type=\"text/css\">"
            + ".errmsg {" + "  border: 1px solid black;" + "  padding: 15px;"
            + "  margin-left: 20px;" + "  margin-right: 20px;" + "}"
            + "h1 { color: #C0C0C0; }" + ".path {" + "  padding-bottom: 10px;"
            + "}" + ".high { " + "  color: #666699; " + "  font-weight: bold;"
            + "}" + "</style>" + "</head>" + "<body>" + "<h1>XQuery Error</h1>";

    private final String formEncoding; //TODO: we may be able to remove this
    // eventually, in favour of
    // HttpServletRequestWrapper being setup in
    // EXistServlet, currently used for doPost()
    // but perhaps could be used for other
    // Request Methods? - deliriumsky
    private final String containerEncoding;
    private final boolean useDynamicContentType;
    private final boolean safeMode;
    private final SessionManager sessionManager;

    //EXQuery Request Module details
    private String xqueryContextExqueryRequestAttribute = null;
    private Constructor cstrHttpServletRequestAdapter = null;

    // Constructor
    public <U> RESTServer(final BrokerPool pool, final String formEncoding,
                      final String containerEncoding, final boolean useDynamicContentType, final boolean safeMode) {
        this.formEncoding = formEncoding;
        this.containerEncoding = containerEncoding;
        this.useDynamicContentType = useDynamicContentType;
        this.safeMode = safeMode;
        this.sessionManager = new SessionManager(pool);

        //get (optional) EXQuery Request Module details
        try {
            Class clazz = Class.forName("org.exist.extensions.exquery.modules.request.RequestModule");
            if (clazz != null) {
                final Field fldExqRequestAttr = clazz.getDeclaredField("EXQ_REQUEST_ATTR");
                if (fldExqRequestAttr != null) {
                    this.xqueryContextExqueryRequestAttribute = (String) fldExqRequestAttr.get(null);

                    if (this.xqueryContextExqueryRequestAttribute != null) {
                        clazz = Class.forName("org.exist.extensions.exquery.restxq.impl.adapters.HttpServletRequestAdapter");
                        if (clazz != null) {
                            this.cstrHttpServletRequestAdapter = clazz.getConstructor(HttpServletRequest.class, FilterInputStreamCacheConfiguration.class);
                        }
                    }

                }
            }
        } catch (final ClassNotFoundException | NoSuchFieldException | IllegalAccessException | NoSuchMethodException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("EXQuery Request Module is not present: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Handle GET request. In the simplest case just returns the document or
     * binary resource specified in the path. If the path leads to a collection,
     * a listing of the collection contents is returned. If it resolves to a
     * binary resource with mime-type "application/xquery", this resource will
     * be loaded and executed by the XQuery engine.
     * 
     * <p>The method also recognizes a number of predefined parameters:
     * <ul>
     *  <li>_xpath or _query: if specified, the given query is executed on the current resource or collection.</li>
     *  <li>_howmany: defines how many items from the query result will be returned.</li>
     *  <li>_start: a start offset into the result set.</li>
     *  <li>_wrap: if set to YES, the query results will be wrapped into a exist:result element.</li>
     *  <li>_indent: if set to YES, the returned XML will be pretty-printed.</li>
     *  <li>_source: if set to YES and a resource with mime-type
     * "application/xquery" is requested then the xquery will not be executed,
     * instead the source of the document will be returned. Must be enabled in
     * descriptor.xml with the following syntax
     * &lt;xquery-app&gt;&lt;allow-source&gt;&lt;xquery
     * path="/db/mycollection/myquery.xql"/&gt;&lt;/allow-source&gt;&lt;/xquery-app&gt;</li>
     *  <li>_xsl: an URI pointing to an XSL stylesheet that will be applied to the returned XML.</li>
     * </ul>
     * </p>
     *
     * @param broker
     * @param transaction
     * @param request
     * @param response
     * @param path
     * @return Optionally an error encountered during processing
     */
    public Optional<Throwable> doGet(final DBBroker broker, final Txn transaction, final HttpServletRequest request,
                      final HttpServletResponse response, final String path) {
        
        //<editor-fold desc="Extract parameters from the GET request">
        
        final Properties outputProperties = new Properties(defaultOutputKeysProperties);

        final XmldbURI pathUri = XmldbURI.createInternal(path);

        try {
            // if required, set character encoding
            if (request.getCharacterEncoding() == null) {
                request.setCharacterEncoding(formEncoding);
            }
        } catch(final UnsupportedEncodingException e) {
            return Optional.of(e);
        }

        final Optional<Integer> sessionId = getQueryStringParameter(request, Release, Integer::parseInt);
        if(sessionId.isPresent()) {
            sessionManager.release(sessionId.get());
            if (LOG.isDebugEnabled()) {
                LOG.debug("Released session " + sessionId);
            }
            response.setStatus(HttpServletResponse.SC_OK);
            return Optional.empty();
        }

        final Optional<String> query = safeMode ? Optional.empty() : Optional.ofNullable(getQueryStringParameter(request, XPath).orElse(request.getParameter(Query.queryStringKey())));

        final List<Tuple2<QName, Sequence>> variables;
        final List<Namespace> namespaces;
        final Optional<String> variablesParam = getQueryStringParameter(request, Variables);
        try {
            if(variablesParam.isPresent()) {
                final NamespaceExtractor nsExtractor = new NamespaceExtractor();
                final Element variablesElem = parseXML(variablesParam.get(), nsExtractor);
                final Either<Throwable, List<Tuple2<QName, Sequence>>> variablesResult = extractVariables(variablesElem);
                if(variablesResult.isLeft()) {
                    return Optional.of(variablesResult.left().get());
                } else {
                    variables = variablesResult.right().get();
                }
                namespaces = nsExtractor.getNamespaces();
            } else {
                variables = Collections.EMPTY_LIST;
                namespaces = Collections.EMPTY_LIST;
            }
        } catch (final IOException | ParserConfigurationException | SAXException e) {
            return Optional.of(e);
        }

        final int start = getQueryStringParameter(request, Start, Integer::parseInt).orElse(DEFAULT_START);
        final int howmany = getQueryStringParameter(request, HowMany, Integer::parseInt).orElse(DEFAULT_HOW_MANY);
        final Optional<Boolean> wrap = getQueryStringParameter(request, Wrap).map(w -> {
            outputProperties.setProperty(Wrap.queryStringKey(), w);
            return parseOption(w);
        });
        final boolean typed = getQueryStringParameter(request, Typed, RESTServer::parseOption).orElse(DEFAULT_TYPED);

        final boolean cache = getQueryStringParameter(request, Cache, RESTServer::parseOption).orElse(DEFAULT_CACHE);
        getQueryStringParameter(request, Indent).map(indent -> outputProperties.setProperty(OutputKeys.INDENT, indent));
        final boolean source = getQueryStringParameter(request, Source, RESTServer::parseOption).orElse(DEFAULT_SOURCE);
        getQueryStringParameter(request, Session).map(id -> outputProperties.setProperty(Serializer.PROPERTY_SESSION_ID, id));

        final Optional<String> stylesheet = getQueryStringParameter(request, XSL).<Optional<String>>map(s -> {
            if (NO.equals(s)) {
                outputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, NO);
                outputProperties.remove(EXistOutputKeys.STYLESHEET);
                return Optional.empty();
            } else {
                outputProperties.setProperty(EXistOutputKeys.STYLESHEET, s);
                return Optional.of(s);
            }
        }).orElseGet(() -> {
            outputProperties.setProperty(EXistOutputKeys.PROCESS_XSL_PI, YES);
            return Optional.empty();
        });

        final Charset encoding = getQueryStringParameter(request, Encoding, Charset::forName).map(charset -> {
            outputProperties.setProperty(OutputKeys.ENCODING, charset.name());
            return charset;
        }).orElse(DEFAULT_ENCODING);


        final MimeType mimeType = Optional.ofNullable(outputProperties.getProperty(OutputKeys.MEDIA_TYPE))
                .flatMap(mime -> Optional.ofNullable(MimeTable.getInstance().getContentType(mime)))
                .orElse(MimeType.XML_TYPE);

        if(LOG.isDebugEnabled()) {
            LOG.debug("stylesheet = " + stylesheet.orElse("<<NONE>>"));
            LOG.debug("query = " + query.orElse("<<NONE>>"));
        }

        //</editor-fold>

        
        /**
         * In the type `Optional<Optional<Throwable>>` if the outer
         * Optional has a value then that indicates that the request was
         * processed. If it does not have a value, then the request has not been
         * processed.
         * 
         * It would be really nice if Java 8 had type aliases, e.g.
         * 
         * type Processed<Optional<Throwable>> := Optional<Optional<Throwable>>
         */
        final Optional<Optional<Throwable>> getResult;
        
        // 1) Attempt to process a submitted query from the querystring
        final Optional<Optional<Throwable>> getQueryResults = query.map(q -> {
            final Either<Throwable, Tuple3<Long, Long, Sequence>> searchResults = search(broker, q, pathUri, namespaces, variables, outputProperties, cache, request, response);
            return searchResults.map(timedSequence -> writeResults(response, broker, timedSequence._3, howmany, start, typed, outputProperties, wrap.orElse(DEFAULT_RECEIVED_QUERY_WRAP), timedSequence._1, timedSequence._2)).valueOr(Optional::of);
        });
        
        if(getQueryResults.isPresent()) {
            getResult = getQueryResults;
        } else {
            
            // 2) Attempt to process the resource/collection indicated by the pathUri
            final Optional<Optional<Throwable>> getUriResults = processGetUri(broker, transaction, pathUri, source, stylesheet, wrap.orElse(DEFAULT_STORED_QUERY_WRAP), encoding, request, outputProperties, response);
            if(getUriResults.isPresent()) {
                getResult = getUriResults;
            } else {
                
                // 3) Try and navigate up the pathUri to find a suitable stored executable
                final Either<Throwable, Optional<Optional<Throwable>>> getExecutableResults = this.<Optional<Optional<Throwable>>>findExecutable(broker, transaction, pathUri.removeLastSegment(), false, documentOpt ->
                        Either.Right(Optional.of(documentOpt.<Optional<Throwable>>map(document ->
                                processExecutable(broker, pathUri, source, document, stylesheet, wrap.orElse(DEFAULT_STORED_QUERY_WRAP), encoding, request, document.getURI(), outputProperties, response)
                        ).orElse(Optional.of(new NotFoundException("Document " + path + " not found"))))));
                
                //if there was an error, then we have processed the result
                getResult = getExecutableResults.valueOr(t -> Optional.of(Optional.of(t)));
            }
        }
        
        if(getResult.isPresent()) {
            //handle XPathException here if present, else return optional error (throwable) to caller
            final Optional<Throwable> processingErrorOpt = getResult.get();
            return handleXPathError(processingErrorOpt, mimeType, encoding, query, pathUri, response);
        } else {
            //none of our attempts were able to process the request
            return Optional.of(new NotFoundException("No database resource found for: " + pathUri.toString()));
        }
    }
    
    private Optional<Throwable> handleXPathError(final Optional<Throwable> errorOpt, final MimeType mimeType, final Charset encoding, final Optional<String> query, final XmldbURI path, final HttpServletResponse response) {
        return errorOpt.filter(t -> t instanceof XPathException).map(t -> (XPathException)t).<Optional<Throwable>>map(xpe -> {
                try {
                    if(MimeType.XML_TYPE.equals(mimeType)) {
                        writeXPathExceptionXml(xpe, encoding, query, path, response);
                    } else {
                        writeXPathExceptionHtml(xpe, encoding, query, path, response);
                    }
                    return Optional.empty();
                } catch(final IOException ioe) {
                    return Optional.of(ioe);
                }
            }).orElse(errorOpt);
    }
    
    private Optional<Optional<Throwable>> processGetUri(final DBBroker broker, final Txn transaction, final XmldbURI path, final boolean source, final Optional<String> stylesheet, final boolean wrap, final Charset encoding, final HttpServletRequest request, final Properties outputProperties, final HttpServletResponse response) {    
        //1) try and retrieve the URI as a document
        final Either<Throwable, Optional<Optional<Throwable>>> docResult = this.<Optional<Optional<Throwable>>>readDocument(broker, transaction, path).apply(documentOpt ->
            Either.Right(documentOpt.<Optional<Throwable>>map(document -> {
                if (isExecutableType(document)) {
                    //An executable e.g. an XQuery or XProc
                    return processExecutable(broker, path, source, document, stylesheet, wrap, encoding, request, document.getURI(), outputProperties, response);
                } else {
                    // return regular resource that is not an xquery and not is xproc
                    return writeResourceAs(document, broker, stylesheet, encoding, null, outputProperties, request, response);
                }
            }))
        );
        
        //2) try and retrieve the URI as a Collection listing
        final Either<Throwable, Optional<Optional<Throwable>>> collectionResult = docResult.<Throwable, Optional<Optional<Throwable>>>flatMap(optProcessed -> {
            if(optProcessed.isPresent()) {
                return Either.Right(optProcessed);
            } else {
                // no document found, try and process the URI as a collection...
                return this.<Optional<Optional<Throwable>>> readCollection(broker, transaction, path).apply(collectionOpt -> collectionOpt.<Either<Throwable, Optional<Optional<Throwable>>>>map(collection -> {
                    if (safeMode) {
                        return Either.Left(new PermissionDeniedException("Not allowed to read collection in safe mode!"));
                    } else {
                        // return a listing of the collection contents
                        return Either.Right(Optional.of(writeCollection(response, encoding, broker, transaction, collection)));
                    }
                }).<Either<Throwable, Optional<Optional<Throwable>>>>orElseGet(() -> {
                    if (source) {
                        // no document, and no collection and yet the user is 
                        // requesting `source`, so return an error as there is no resource
                        // to have the source of
                        return Either.Left(new NotFoundException("Document " + path + " not found"));
                    } else {
                        return Either.Right(Optional.empty());
                    }
                }));
            }
        });
        
        //if there was an error, then we have processed the result
        return collectionResult.valueOr(t -> Optional.of(Optional.of(t)));
    }
        
    /**
     * Attempts to find an Executable resource (i.e. XQuery or XProc)
     * by traversing up the given XmldbURI path
     * 
     * @param ignoreNonExecutableResource When true if we encounter a non-executable
     *  resource whilst traversing the URI space, then we exit. If false then
     *  we return a NotFoundException.
     */
    private <R> Either<Throwable, R> findExecutable(final DBBroker broker, final Txn transaction, final XmldbURI path, final boolean ignoreNonExecutableResource, final Function<Optional<DocumentImpl>, Either<Throwable, R>> readOp) {
        if(path == XmldbURI.EMPTY_URI) {
            return readOp.apply(Optional.empty());
        }
        
        final Optional<Either<Throwable, R>> readOpResult = this.<Optional<Either<Throwable, R>>>readDocument(broker, transaction, path).apply(documentOpt -> 
                Either.Right(documentOpt.<Optional<Either<Throwable, R>>>map(document -> {
                    if(isExecutableType(document)) {
                        // found executable resource, apply readOp
                        return Optional.of(readOp.apply(Optional.of(document))); 
                    } else if(ignoreNonExecutableResource) {
                        // found non-executable resource, exit
                        return Optional.empty();
                    } else {
                        // found non-executable resource, exit with error
                        return Optional.of(Either.Left(new NotFoundException("Could not find an executable resource at " + path)));
                    }
                }).orElse(Optional.empty()))
        ).valueOr(l -> Optional.of(Either.Left(l)));
        
        return readOpResult.orElse(findExecutable(broker, transaction, path.removeLastSegment(), ignoreNonExecutableResource, readOp)); //recurse if nessecary
    }
    
    /**
     * Processes an Executable Resource stored in the database
     * e.g. XQuery or XProc
     */
    private Optional<Throwable> processExecutable(final DBBroker broker, final XmldbURI path, final boolean source, final DocumentImpl resource, final Optional<String> stylesheet, final boolean wrap, final Charset encoding, final HttpServletRequest request, final XmldbURI servletPath, final Properties outputProperties, final HttpServletResponse response) {
        // Should we display the source of the XQuery or XProc or execute it
        if (source) {
            // show the source

            // check are we allowed to show the xquery source -
            // descriptor.xml
            final boolean descriptorPermit = Optional.of(Descriptor.getDescriptorSingleton()).filter(descriptor -> descriptor.allowSource(resource.getURI().toString())).isPresent();
            if (descriptorPermit && resource.getPermissions().validate(broker.getCurrentSubject(), Permission.READ)) {
                if (MimeType.XQUERY_TYPE.getName().equals(resource.getMetadata().getMimeType())) {
                    // Show the source of the XQuery
                    return writeResourceAs(resource, broker, stylesheet, encoding, MimeType.TEXT_TYPE.getName(), outputProperties, request, response);
                } else if (MimeType.XPROC_TYPE.getName().equals(resource.getMetadata().getMimeType())) {
                    // Show the source of the XProc
                    return writeResourceAs(resource, broker, stylesheet, encoding, MimeType.XML_TYPE.getName(), outputProperties, request, response);
                } else {
                    return Optional.of(new BadRequestException("Cannot view source of type: " + resource.getMetadata().getMimeType()));
                }
            } else {
                // we are not allowed to show the source - query not
                // allowed in descriptor.xml
                // or descriptor not found, so assume source view not
                // allowed
                return Optional.of(new PermissionDeniedException("Permission to view XQuery source for: " + path + " denied. Must be explicitly defined in descriptor.xml"));
            }
        } else {
            final XmldbURI pathInfo = path.trimFromBeginning(servletPath);
            if (MimeType.XQUERY_TYPE.getName().equals(resource.getMetadata().getMimeType())) {
                // Execute the XQuery
                final Source dbSource = new DBSource(broker, (BinaryDocument) resource, true);
                final Either<Throwable, Tuple3<Long, Long, Sequence>> dbResult =
                        compileQuery(broker, dbSource, Optional.empty(), Optional.of(XmldbURI.EMBEDDED_SERVER_URI.append(resource.getCollection().getURI())), Optional.of(new XmldbURI[] {resource.getCollection().getURI()}), Collections.EMPTY_LIST, Collections.EMPTY_LIST, request, Optional.of(servletPath), Optional.of(pathInfo), response)
                                .apply(timedCompiled -> executeQuery(broker, timedCompiled._2, outputProperties).map(timedExecuted -> timedExecuted.after(timedCompiled._1)));

                return dbResult.map(timedSequence -> writeResults(response, broker, timedSequence._3, -1, 1, false, outputProperties, wrap, timedSequence._1, timedSequence._2))
                        .valueOr(Optional::of);

            } else if (MimeType.XPROC_TYPE.getName().equals(resource.getMetadata().getMimeType())) {
                // Execute the XProc
                final URLSource uriSource = new URLSource(this.getClass().getResource("run-xproc.xq"));
                final List<Tuple2<QName, Sequence>> variables = new ArrayList<>();

                variables.add(new Tuple2<>(new QName("pipeline"), new StringValue(resource.getURI().toString())));

                final String stdin = Optional.ofNullable(request.getParameter("stdin")).orElse("");
                variables.add(new Tuple2<>(new QName("stdin"), new StringValue(stdin)));

                final String debug = Optional.ofNullable(request.getParameter("debug")).map(x -> "1").orElse("0");
                variables.add(new Tuple2<>(new QName("debug"), new StringValue(debug)));

                final String bindings = Optional.ofNullable(request.getParameter("bindings")).orElse("<bindings/>");
                variables.add(new Tuple2<>(new QName("bindings"), new StringValue(bindings)));

                final String autobind = Optional.ofNullable(request.getParameter("autobind")).map(x -> "1").orElse("0");
                variables.add(new Tuple2<>(new QName("autobind"), new StringValue(autobind)));

                final String options = Optional.ofNullable(request.getParameter("options")).orElse("<options/>");
                variables.add(new Tuple2<>(new QName("options"), new StringValue(options)));

                final Either<Throwable, Tuple3<Long, Long, Sequence>> dbResult = compileQuery(broker, uriSource, Optional.empty(), Optional.of(XmldbURI.EMBEDDED_SERVER_URI.append(resource.getCollection().getURI())), Optional.of(new XmldbURI[] {resource.getCollection().getURI()}), Collections.EMPTY_LIST, variables, request, Optional.of(servletPath), Optional.of(pathInfo), response)
                        .apply(timedCompiled -> executeQuery(broker, timedCompiled._2, outputProperties).map(timedExecuted -> timedExecuted.after(timedCompiled._1)));

                return dbResult.map(timedSequence -> writeResults(response, broker, timedSequence._3, -1, 1, false, outputProperties, wrap, timedSequence._1, timedSequence._2))
                        .valueOr(Optional::of);
            } else {
                return Optional.of(new BadRequestException("Cannot execute resource of type: " + resource.getMetadata().getMimeType()));
            }
        }
    }
    
    public Optional<Throwable> doHead(final DBBroker broker, final Txn transaction, final HttpServletRequest request,
                       final HttpServletResponse response, final String path) {
        //<editor-fold desc="Extract parameters from the HEAD request">
        
        final Properties outputProperties = new Properties(defaultOutputKeysProperties);
        
        final XmldbURI pathUri = XmldbURI.createInternal(path);

        final Charset encoding = getQueryStringParameter(request, Encoding, Charset::forName).map(charset -> {
            outputProperties.setProperty(OutputKeys.ENCODING, charset.name());
            return charset;
        }).orElse(DEFAULT_ENCODING);
        
        //</editor-fold>
        
        
        final Optional<Optional<Throwable>> headResult;
        
        // 1) Attempt to execute an execututable resource
        final Optional<Optional<Throwable>> headExecutableResults = conditionallyFindAndProcessExecutable(broker, transaction, pathUri, encoding, request, outputProperties, response);
        
        if(headExecutableResults.isPresent()) {
            headResult = headExecutableResults;
        } else {

            // 2) Attempt to get metadata from a resource
            final Optional<Optional<Throwable>> headDocResults = readDocument(broker, transaction, pathUri).apply(documentOpt -> {
                return Either.Right(documentOpt.<Optional<Throwable>>map(document -> {
                    if(!document.getPermissions().validate(broker.getCurrentSubject(), Permission.READ)) {
                        return Optional.of(new PermissionDeniedException("Permission to read resource " + path + " denied"));
                    } else {
                        final DocumentMetadata metadata = document.getMetadata();
                        response.setContentType(metadata.getMimeType());
                        // As HttpServletResponse.setContentLength is limited to integers,
                        // (see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4187336)
                        // next sentence:
                        //	response.setContentLength(resource.getContentLength());
                        // must be set so
                        response.addHeader("Content-Length", Long.toString(document.getContentLength()));
                        setCreatedAndLastModifiedHeaders(response, metadata.getCreated(), metadata.getLastModified());
                        return Optional.empty();
                    }
                }));
            }).valueOr(t -> Optional.of(Optional.of(t)));
            
            if(headDocResults.isPresent()) {
                headResult = headDocResults;
            } else {

                // 3) Attempt to get metadata from a collection
                final Optional<Optional<Throwable>> headColResults = readCollection(broker, transaction, pathUri).apply(collectionOpt ->
                        Either.Right(collectionOpt.<Optional<Throwable>>map(collection -> {
                            if(!collection.getPermissions().validate(broker.getCurrentSubject(), Permission.READ)) {
                                return Optional.of(new PermissionDeniedException("Permission to read collection " + path + " denied"));
                            } else {
                                response.setContentType(MimeType.XML_TYPE.getName() + "; charset=" + encoding.name());
                                setCreatedAndLastModifiedHeaders(response, collection.getCreationTime(), collection.getCreationTime());
                                return Optional.empty();
                            }
                        }))).valueOr(t -> Optional.of(Optional.of(t)));
                
                headResult = headColResults;
            }
        }
        
        if(headResult.isPresent()) {
            //handle XPathException here if present, else return optional error (throwable) to caller
            final Optional<Throwable> processingErrorOpt = headResult.get();
            return handleXPathError(processingErrorOpt, MimeType.HTML_TYPE, encoding, Optional.empty(), pathUri, response);
        } else {
            //none of our attempts were able to process the request
            return Optional.of(new NotFoundException("No database resource found for: " + pathUri.toString()));
        }
    }

    /**
     * Handles POST requests. If the path leads to a binary resource with
     * mime-type "application/xquery", that resource will be read and executed
     * by the XQuery engine. Otherwise, the request content is loaded and parsed
     * as XML. It may either contain an XUpdate or a query request.
     *
     * @param broker
     * @param transaction
     * @param request
     * @param response
     * @param path
     * @return Optionally an error encountered during processing
     */
    public Optional<Throwable> doPost(final DBBroker broker, final Txn transaction, final HttpServletRequest request, final HttpServletResponse response, final String path) {

        //<editor-fold desc="Extract minimal parameters from the POST request">
        
        final Properties outputProperties = new Properties(defaultOutputKeysProperties);
        
        final XmldbURI pathUri = XmldbURI.createInternal(path);
        try {
            // if required, set character encoding
            if(request.getCharacterEncoding() == null) {
                request.setCharacterEncoding(formEncoding);
            }
        } catch(final UnsupportedEncodingException e) {
            return Optional.of(e);
        }

        final Charset encoding = Optional.ofNullable(outputProperties.getProperty(OutputKeys.ENCODING)).map(Charset::forName).orElse(UTF_8);

        final MimeType mimeType = Optional.ofNullable(outputProperties.getProperty(OutputKeys.MEDIA_TYPE))
                .flatMap(mime -> Optional.ofNullable(MimeTable.getInstance().getContentType(mime)))
                .orElse(MimeType.XML_TYPE);

        // check the content type to see if its XML or a parameter string
        final Optional<String> requestType = Optional.ofNullable(request.getContentType()).map(contentType -> {
            final int semicolon = contentType.indexOf(';');
            if (semicolon > 0) {
                return contentType.substring(0, semicolon).trim();
            } else {
                return contentType;
            }
        });
        
        //</editor-fold>
        
        
        final Optional<Optional<Throwable>> postResult;
        
        // 1) Try and navigate up the pathUri to find a suitable executable 
        final Optional<Optional<Throwable>> postExecutableResults = this.<Optional<Optional<Throwable>>>findExecutable(broker, transaction, pathUri, true, documentOpt ->
                Either.Right(documentOpt.<Optional<Throwable>>map(document ->
                        processExecutable(broker, pathUri, false, document, Optional.empty(), DEFAULT_STORED_QUERY_WRAP, encoding, request, document.getURI(), outputProperties, response)))
        ).valueOr(t -> Optional.of(Optional.of(t)));

        if(postExecutableResults.isPresent()) {
            postResult = postExecutableResults;
        } else {
        
            // 2) Process the request
            if(requestType.filter(rt -> rt.equals(MimeType.URL_ENCODED_TYPE.getName())).isPresent()) {
                
                // 2.1) if URI encoded data just treat as a get
                postResult = Optional.of(doGet(broker, transaction, request, response, path));
            } else {
                
                // 2.2) Attempt to process as XML POST (e.g. XQuery or XUpdate)
                final String content;
                final NamespaceExtractor nsExtractor;
                final Element root; 
                try {
                    content = getRequestContent(request);
                    nsExtractor = new NamespaceExtractor();
                    root = parseXML(content, nsExtractor);
                } catch(final IOException | ParserConfigurationException | SAXException e) {
                    return Optional.of(e);
                }
                
                final String rootNS = root.getNamespaceURI();
                
                if (rootNS != null && rootNS.equals(Namespaces.EXIST_NS)) {
                    
                    // 2.2.1) Attempt to process an XQuery
                    if (Query.xmlKey().equals(root.getLocalName())) {
                        postResult = processPostXQuery(broker, transaction, pathUri, root, nsExtractor.getNamespaces(), request, outputProperties, response);
                    } else {
                        postResult = Optional.of(Optional.of(new BadRequestException("No query specified")));
                    }
                } else if (rootNS != null && rootNS.equals(XUpdateProcessor.XUPDATE_NS)) {
                    
                    // 2.2.2) Attempt to process an XUpdate
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("Got xupdate request: " + content);
                    }
                    postResult = processPostXUpdate(broker, transaction, pathUri, content);
                    
                } else {
                    postResult = Optional.of(Optional.of(new BadRequestException("Unknown XML root element: " + root.getNodeName())));
                }
            }
        }
        
        if(postResult.isPresent()) {
            //handle XPathException here if present, else return optional error (throwable) to caller
            final Optional<Throwable> processingErrorOpt = postResult.get();
            return handleXPathError(processingErrorOpt, mimeType, encoding, Optional.empty(), pathUri, response);
        } else {
            //none of our attempts were able to process the request
            return Optional.of(new NotFoundException("No database resource found for: " + pathUri.toString()));
        }
    }

    private Optional<Optional<Throwable>> processPostXQuery(final DBBroker broker, final Txn transaction, final XmldbURI path, final Element queryElem, final List<Namespace> namespaces, final HttpServletRequest request, final Properties outputProperties, final HttpServletResponse response) {

        //<editor-fold desc="Extract parameters from the XML body of the POST request">
        
        final int start = getBodyParameter(queryElem, Start, Integer::parseInt).orElse(DEFAULT_START);
        final int howmany = getBodyParameter(queryElem, Max, Integer::parseInt).orElse(DEFAULT_HOW_MANY);
        final boolean enclose = getBodyParameter(queryElem, Enclose, RESTServer::parseOption)
                .orElse(getBodyParameter(queryElem, Wrap, RESTServer::parseOption)
                        .orElse(DEFAULT_RECEIVED_QUERY_WRAP));

        final boolean typed = getBodyParameter(queryElem, Typed, RESTServer::parseOption).orElse(DEFAULT_TYPED);
        final boolean cache = getBodyParameter(queryElem, Cache, RESTServer::parseOption).orElse(DEFAULT_CACHE);
        getBodyParameter(queryElem, Session).filter(s -> !s.isEmpty()).map(id -> outputProperties.setProperty(Serializer.PROPERTY_SESSION_ID, id));
        getBodyParameter(queryElem, Method).filter(s -> !s.isEmpty()).map(method -> outputProperties.setProperty(SERIALIZATION_METHOD_PROPERTY, method));

        final MimeType mimeType = getBodyParameter(queryElem, Mime).filter(s -> s.isEmpty()).flatMap(mime -> Optional.ofNullable(MimeTable.getInstance().getContentType(mime))).orElse(MimeType.XML_TYPE);
        outputProperties.setProperty(OutputKeys.MEDIA_TYPE, mimeType.getName());
        
        final Tuple3<Optional<String>, Optional<Element>, Properties> queryBody = extractQueryBody(queryElem);
        
        final Optional<String> query = queryBody._1;
        
        final Either<Throwable, List<Tuple2<QName, Sequence>>> variablesResult = queryBody._2.map(elem -> extractVariables(elem)).orElse(Either.Right(Collections.EMPTY_LIST));
        final List<Tuple2<QName, Sequence>> variables;
        if(variablesResult.isLeft()) {
            return Optional.of(Optional.of(variablesResult.left().get()));
        } else {
            variables = variablesResult.right().get();
        }
        
        outputProperties.putAll(queryBody._3);

        //</editor-fold>
        
        // execute query
        if(query.isPresent()) { //TODO(AR) do a better functional approach here
            final Either<Throwable, Tuple3<Long, Long, Sequence>> searchResults = search(broker, query.get(), path, namespaces, variables, outputProperties, cache, request, response);
            final Either<Throwable, Optional<Throwable>> results = searchResults.map(timedSequence -> writeResults(response, broker, timedSequence._3, howmany, start, typed, outputProperties, enclose, timedSequence._1, timedSequence._2));
            return Optional.of(results.valueOr(Optional::of));
        } else {
            return Optional.of(Optional.of(new BadRequestException("No query specified")));
        }
    }
    
    private Optional<Optional<Throwable>> processPostXUpdate(final DBBroker broker, final Txn transaction, final XmldbURI path, final String xupdate) {
        final Either<Throwable, Optional<MutableDocumentSet>> collectionDocs = this.<Optional<Either<Throwable, MutableDocumentSet>>>readCollection(broker, transaction, path).apply(collectionOpt -> Either.Right(collectionOpt.<Either<Throwable, MutableDocumentSet>>map(collection -> {
            try {
                final MutableDocumentSet docs = new DefaultDocumentSet();
                collection.allDocs(broker, docs, true);
                return Either.Right(docs);
            } catch(final PermissionDeniedException e) {
                return Either.Left(e);
            }
        }))).map(o -> o.map(e -> e.map(Optional::of)))
                .flatMap(o -> o.orElse(Either.Right(Optional.empty())));
        
        final Either<Throwable, Long> xupdateResults = collectionDocs.flatMap(collectionDocsOpt -> {
            return collectionDocsOpt.map(docs -> performXUpdate(broker, transaction, docs, xupdate))
                    .orElseGet(() -> {
                        final Either<Throwable, Optional<MutableDocumentSet>> documentDocs = this.<Optional<MutableDocumentSet>>readDocument(broker, transaction, path).apply(documentOpt -> Either.Right(documentOpt.map(document -> {
                            final MutableDocumentSet docs = new DefaultDocumentSet();
                            docs.add(document);
                            return docs;
                        })));
                        return documentDocs.flatMap(documentDocsOpt -> {
                            return documentDocsOpt.map(docs -> performXUpdate(broker, transaction, docs, xupdate))
                                    .orElseGet(() -> {
                                        try {
                                            final MutableDocumentSet docs = new DefaultDocumentSet();
                                            broker.getAllXMLResources(docs);
                                            return performXUpdate(broker, transaction, docs, xupdate);
                                        } catch(final PermissionDeniedException e) {
                                            return Either.Left(e);
                                        }
                                    });
                        });
                    });
        });
        
        return Optional.of(xupdateResults.map(r -> {
                if(LOG.isDebugEnabled()) {
                    LOG.debug("XUpdate caused " + r + " modifications!");
                }
                return Optional.<Throwable>empty();
            }).valueOr(Optional::of));
    }
    
    private Either<Throwable, Long> performXUpdate(final DBBroker broker, final Txn transaction, final MutableDocumentSet docs, final String xupdate) {
        try {
            final XUpdateProcessor processor = new XUpdateProcessor(broker, docs);
            final Modification modifications[] = processor.parse(new InputSource(new StringReader(xupdate)));
            long mods = 0;
            for (final Modification modification : modifications) {
                mods += modification.process(transaction);
                broker.flush();
            }
            return Either.Right(mods);
        } catch(final EXistException | PermissionDeniedException | LockException | IOException | SAXException | ParserConfigurationException | XPathException e) {
            return Either.Left(e);
        }
    }
    
    private Tuple3<Optional<String>, Optional<Element>, Properties> extractQueryBody(final Element query) {
        Optional<String> xquery = Optional.empty();
        Optional<Element> variables = Optional.empty();
        final Properties properties = new Properties();

        final NodeList children = query.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            final Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNamespaceURI().equals(Namespaces.EXIST_NS)) {
                if (Text.xmlKey().equals(child.getLocalName())) {
                    final StringBuilder buf = new StringBuilder();
                    Node next = child.getFirstChild();
                    while (next != null) {
                        if (next.getNodeType() == Node.TEXT_NODE
                                || next.getNodeType() == Node.CDATA_SECTION_NODE) {
                            buf.append(next.getNodeValue());
                        }
                        next = next.getNextSibling();
                    }
                    xquery = Optional.of(buf.toString());

                } else if (Variables.xmlKey().equals(child.getLocalName())) {
                    variables = Optional.of((ElementImpl) child);
                } else if (Properties.xmlKey().equals(child.getLocalName())) {
                    Node node = child.getFirstChild();
                    while (node != null) {
                        if (node.getNodeType() == Node.ELEMENT_NODE
                                && node.getNamespaceURI().equals(Namespaces.EXIST_NS)
                                && Property.xmlKey().equals(node.getLocalName())) {

                            final Element property = (Element) node;
                            final String key = property.getAttribute("name");
                            final String value = property.getAttribute("value");
                            LOG.debug(key + " = " + value);

                            if (key != null && value != null) {
                                properties.setProperty(key, value);
                            }
                        }
                        node = node.getNextSibling();
                    }
                }
            }
        }

        return new Tuple3<>(xquery, variables, properties);
    }

    private Element parseXML(final String content, final NamespaceExtractor nsExtractor) throws SAXException, ParserConfigurationException, IOException {
        final SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);

        try(final Reader strReader = new StringReader(content)) {
            final InputSource src = new InputSource(strReader);
            final SAXParser parser = factory.newSAXParser();
            final XMLReader reader = parser.getXMLReader();
            final SAXAdapter adapter = new SAXAdapter();
            nsExtractor.setContentHandler(adapter);
            nsExtractor.setParent(reader);
            nsExtractor.parse(src);

            final Document doc = adapter.getDocument();
            return (Element) doc.getDocumentElement();
        }
    }

    private static class NamespaceExtractor extends XMLFilterImpl {
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

    private static class Namespace extends Tuple2<String, String> {
        public Namespace(final String prefix, final String uri) {
            super(prefix, uri);
        }

        public String getPrefix() {
            return _1;
        }

        public String getUri() {
            return _2;
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
     * @param broker
     * @param transaction
     * @param path     The path to which the file should be stored
     * @param request
     * @param response
     * @return Optionally an error encountered during processing
     */
    public Optional<Throwable> doPut(final DBBroker broker, final Txn transaction, final String path, final HttpServletRequest request, final HttpServletResponse response) {

        //<editor-fold desc="Extract parameters from the HEAD request">
        final Properties outputProperties = new Properties(defaultOutputKeysProperties);
        
        final XmldbURI pathUri = XmldbURI.createInternal(path);

        final Charset encoding = getQueryStringParameter(request, Encoding, Charset::forName).map(charset -> {
            outputProperties.setProperty(OutputKeys.ENCODING, charset.name());
            return charset;
        }).orElse(DEFAULT_ENCODING);
        
        //</editor-fold>
        
        
        final Optional<Optional<Throwable>> putResult;
        
        // 1) Do not allow PUT to be performed on a Collection
        final Optional<Optional<Throwable>> skipCollectionResult = this.<Boolean>readCollection(broker, transaction, pathUri).<Boolean>apply(collectionOpt -> Either.Right(collectionOpt.isPresent()))
                .map(isCollection -> {
                    if(isCollection) {
                        return Optional.of(Optional.of(new BadRequestException("A PUT request is not allowed against a plain collection path.")));
                    } else {
                        return Optional.empty();
                    }})
                .valueOr(l -> Optional.of(Optional.of(l)));
        
        if(skipCollectionResult.isPresent()) {
            putResult = skipCollectionResult;
        } else {
                        
            // 2) Attempt to execute an execututable resource
            final Optional<Optional<Throwable>> putExecutableResults = conditionallyFindAndProcessExecutable(broker, transaction, pathUri, encoding, request, outputProperties, response);

            if(putExecutableResults.isPresent()) {
                putResult = putExecutableResults;
            } else {
                
                // 3) Store the documemnt from the PUT body into the database
                final Optional<Optional<Throwable>> putDocumentResults = Optional.of(processPutDocument(broker, transaction, pathUri, request, response));
                putResult = putDocumentResults;
            }
        }

        if(putResult.isPresent()) {
            //handle XPathException here if present, else return optional error (throwable) to caller
            final Optional<Throwable> processingErrorOpt = putResult.get();
            return handleXPathError(processingErrorOpt, MimeType.HTML_TYPE, encoding, Optional.empty(), pathUri, response);
        } else {
            //none of our attempts were able to process the request
            return Optional.of(new NotFoundException("No database resource found for: " + pathUri.toString()));
        }
    }
    
    private Optional<Throwable> processPutDocument(final DBBroker broker, final Txn transaction, final XmldbURI path, final HttpServletRequest request, final HttpServletResponse response) {
        // put may send a lot of data, so save it
        // to a temporary file first.
        return copyBodyToTempFile(request).flatMap(vtempFile -> {
            try {
                return this.<Optional<Throwable>>alwaysWithCollection(Lock.WRITE_LOCK, broker, transaction, path.removeLastSegment()).apply(collection -> {
                    final Tuple3<String, Charset, MimeType> type = extractPutType(path, request);
                    try {
                        if (type._3.isXMLType()) {
                            // Store XML document
                            try(final EXistInputSource vtfis = new VirtualTempFileInputSource(vtempFile, type._2)) {
                                final IndexInfo info = collection.validateXMLResource(transaction, broker, path.lastSegment(), vtfis);
                                info.getDocument().getMetadata().setMimeType(type._1);
                                collection.store(transaction, broker, info, vtfis, false);
                            }
                        } else {
                            // Store Binary document
                            try (final InputStream is = vtempFile.getByteStream()) {
                                collection.addBinaryResource(transaction, broker, path.lastSegment(), is, type._1, vtempFile.length());
                            }
                        }
                        response.setStatus(HttpServletResponse.SC_CREATED);
                        return Either.Right(Optional.empty());
                    } catch(final EXistException | PermissionDeniedException | IOException | LockException | SAXException e) {
                        return Either.Left(e);
                    }
                });
            } finally {
                vtempFile.delete();
            }
        }).valueOr(Optional::of);
    }
    
    private Either<Throwable, VirtualTempFile> copyBodyToTempFile(final HttpServletRequest request) {
        //a bit nasty as VTempFile escapes from this function after it is closed
        //but well VTempFile is nasty and kinda encourages you to do this! (AR)
        try(final InputStream is = request.getInputStream();
                final VirtualTempFile vtempFile = new VirtualTempFile()) {
            final long len = Optional.ofNullable(request.getHeader("Content-Length")).map(Long::parseLong).orElse((long)request.getContentLength());

            vtempFile.setTempPrefix("existSRV");
            vtempFile.setTempPostfix(".tmp");
            vtempFile.write(is, len);

            return Either.Right(vtempFile);
        } catch(final IOException ioe) {
            return Either.Left(ioe);
        }
    }
    
    private Tuple3<String, Charset, MimeType> extractPutType(final XmldbURI path, final HttpServletRequest request) {
        final Tuple3<Optional<String>, Charset, Optional<MimeType>> type = Optional.ofNullable(request.getContentType()).map(contentType -> {
            final int semicolon = contentType.indexOf(';');
            String charset = null;
            if (semicolon > 0) {
                contentType = contentType.substring(0, semicolon).trim();
                final int equals = contentType.indexOf('=', semicolon);
                if (equals > 0) {
                    final String param = contentType.substring(semicolon + 1, equals).trim();
                    if (param.compareToIgnoreCase("charset=") == 0) {
                        charset = param.substring(equals + 1).trim();
                    }
                }
            }
            return new Tuple3<>(
                    Optional.of(contentType),
                    Optional.ofNullable(charset).map(Charset::forName).orElse(UTF_8),
                    Optional.ofNullable(MimeTable.getInstance().getContentType(contentType))
            );
        }).orElseGet(() -> {
            final Optional<MimeType> mime = Optional.ofNullable(MimeTable.getInstance().getContentTypeFor(path.lastSegment()));
            return new Tuple3<>(
                    mime.map(MimeType::getName),
                    UTF_8,
                    mime
            );
        });
        
        return new Tuple3<>(type._1.orElse(MimeType.BINARY_TYPE.getName()), type._2, type._3.orElse(MimeType.BINARY_TYPE));
    }
    
    public Optional<Throwable> doDelete(final DBBroker broker, final Txn transaction, final String path, final HttpServletRequest request, final HttpServletResponse response) {

        //<editor-fold desc="Extract parameters from the DELETE request">
        
        final Properties outputProperties = new Properties(defaultOutputKeysProperties);

        final XmldbURI pathUri = XmldbURI.createInternal(path);

        final Charset encoding = getQueryStringParameter(request, Encoding, Charset::forName).map(charset -> {
            outputProperties.setProperty(OutputKeys.ENCODING, charset.name());
            return charset;
        }).orElse(DEFAULT_ENCODING);
        
        //</editor-fold>
        
        final Optional<Optional<Throwable>> deleteResult;
        
        // 1) Attempt to execute an execututable resource
        final Optional<Optional<Throwable>> deleteExecutableResults = conditionallyFindAndProcessExecutable(broker, transaction, pathUri, encoding, request, outputProperties, response);
        
        if(deleteExecutableResults.isPresent()) {
            deleteResult = deleteExecutableResults;
        } else {

            // 2) Attempt to delete a collection
            final Optional<Optional<Throwable>> deleteCollectionResult = this.<Optional<Optional<Throwable>>>writeCollection(broker, transaction, pathUri).apply(collectionOpt ->
                    Either.Right(collectionOpt.<Optional<Throwable>>map(collection -> {
                        try {
                            final boolean result = broker.removeCollection(transaction, collection);
                            if(result) {
                                return Optional.empty();
                            } else {
                                return Optional.of(new EXistException("Unable to remove collection: " + collection.getURI()));
                            }
                        } catch(final PermissionDeniedException | IOException | TriggerException e) {
                            return Optional.of(e);
                        }
                    }))
            ).valueOr(l -> Optional.of(Optional.of(l)));
            
            if(deleteCollectionResult.isPresent()) {
                deleteResult = deleteCollectionResult;
            } else {
                
                // 3) Attempt to delete a document
                final Optional<Optional<Throwable>> deleteDocumentResult = this.<Optional<Optional<Throwable>>>writeDocument(broker, transaction, pathUri).apply(documentOpt ->
                    Either.Right(documentOpt.<Optional<Throwable>>map(document -> {
                        try {
                            if(LOG.isDebugEnabled()) {
                                LOG.debug("removing document " + path);
                            }
                    
                            if (document.getResourceType() == DocumentImpl.BINARY_FILE) {
                                broker.removeBinaryResource(transaction, (BinaryDocument)document);
                            } else {
                                broker.removeXMLResource(transaction, document);
                            }
                            return Optional.empty();
                        } catch(final PermissionDeniedException | IOException e) {
                            return Optional.of(e);
                        }
                    }))
                ).valueOr(l -> Optional.of(Optional.of(l)));
                
                deleteResult = deleteDocumentResult;
            }
        }

        if(deleteResult.isPresent()) {
            //handle XPathException here if present, else return optional error (throwable) to caller
            final Optional<Throwable> processingErrorOpt = deleteResult.get();
            return handleXPathError(processingErrorOpt, MimeType.HTML_TYPE, encoding, Optional.empty(), pathUri, response);
        } else {
            //none of our attempts were able to process the request
            return Optional.of(new NotFoundException("No database resource found for: " + pathUri.toString()));
        }
    }

    private Optional<Optional<Throwable>> conditionallyFindAndProcessExecutable(final DBBroker broker, final Txn transaction, final XmldbURI path, final Charset encoding, final HttpServletRequest request, final Properties outputProperties, final HttpServletResponse response) {
        if(request.getAttribute(XQueryURLRewrite.RQ_ATTR) == null) {
            return Optional.empty();
        } else {
            return this.<Optional<Optional<Throwable>>>findExecutable(broker, transaction, path, true, documentOpt ->
                    Either.Right(documentOpt.<Optional<Throwable>>map(document ->
                            processExecutable(broker, path, false, document, Optional.empty(), DEFAULT_STORED_QUERY_WRAP, encoding, request, document.getURI(), outputProperties, response)))
            ).valueOr(t -> Optional.of(Optional.of(t)));
        }
    }

    private String getRequestContent(final HttpServletRequest request) throws IOException {
        final Charset encoding = Optional.ofNullable(request.getCharacterEncoding()).map(Charset::forName).orElse(UTF_8);
        final InputStream is = request.getInputStream();  //TODO(AR) should we not close the inputstream?
        final Reader reader = new InputStreamReader(is, encoding);
        try(final StringWriter content = new StringWriter()) {
            final char ch[] = new char[4096];
            int len = -1;
            while ((len = reader.read(ch)) > -1) {
                content.write(ch, 0, len);
            }

            return content.toString();
        }
    }

    private <R> Function<Function<Optional<DocumentImpl>, Either<Throwable, R>>, Either<Throwable, R>> readDocument(final DBBroker broker, final Txn transaction, final XmldbURI uri) {
        return withDocument(Lock.READ_LOCK, broker, transaction, uri);
    }

    private <R> Function<Function<Optional<DocumentImpl>, Either<Throwable, R>>, Either<Throwable, R>> writeDocument(final DBBroker broker, final Txn transaction, final XmldbURI uri) {
        return withDocument(Lock.WRITE_LOCK, broker, transaction, uri);
    }
    
    private <R> Function<Function<Optional<DocumentImpl>, Either<Throwable, R>>, Either<Throwable, R>> withDocument(final int lockMode, final DBBroker broker, final Txn transaction, final XmldbURI uri) {
        return withOp ->
                this.<R>readCollection(broker, transaction, uri.removeLastSegment()).apply(optCollection ->
                        optCollection.map(collection ->
                                this.<R>withDocument(lockMode, broker, transaction, collection, uri))
                                .orElse(this.<R>withoutDocument())
                                .apply(withOp));

    }

    private <R> Function<Function<Optional<DocumentImpl>, Either<Throwable, R>>, Either<Throwable, R>> withoutDocument() {
        return withOp -> withOp.apply(Optional.empty());
    }

    private <R> Function<Function<Optional<DocumentImpl>, Either<Throwable, R>>, Either<Throwable, R>> withDocument(final int lockMode, final DBBroker broker, final Txn transaction, final Collection collection, final XmldbURI uri) {
        return withOp -> {
            Optional<DocumentImpl> document = Optional.empty();
            try {
                document = Optional.ofNullable(collection.getDocumentWithLock(broker, uri.lastSegment(), lockMode));
                return withOp.apply(document);
            } catch (final PermissionDeniedException | LockException e) {
                return Either.Left(e);
            } finally {
                if (document.isPresent()) {
                    collection.releaseDocument(document.get(), lockMode);
                }
            }
        };

    }

    private <R> Function<Function<Optional<Collection>, Either<Throwable, R>>, Either<Throwable, R>> readCollection(final DBBroker broker, final Txn transaction, final XmldbURI uri) {
        return withCollection(Lock.READ_LOCK, broker, transaction, uri);
    }

    private <R> Function<Function<Optional<Collection>, Either<Throwable, R>>, Either<Throwable, R>> writeCollection(final DBBroker broker, final Txn transaction, final XmldbURI uri) {
        return withCollection(Lock.WRITE_LOCK, broker, transaction, uri);
    }
    
    private <R> Function<Function<Optional<Collection>, Either<Throwable, R>>, Either<Throwable, R>> withCollection(final int lockMode, final DBBroker broker, final Txn transaction, final XmldbURI uri) {
        return withOp -> {
            Optional<Collection> collection = Optional.empty();
            try {
                collection = Optional.ofNullable(broker.openCollection(uri, lockMode));
                return withOp.apply(collection);
            } catch(final PermissionDeniedException e) {
                return Either.Left(e);
            } finally {
                if(collection.isPresent()) {
                    collection.get().release(lockMode);
                }
            }
        };
    }
    
    /**
     * Will always have a Collection
     * if the collection already exists in the database
     * we will hold the lock with lockMode
     * else we will create the collection and the transaction 
     * will hold a write lock
     */
    private <R> Function<Function<Collection, Either<Throwable, R>>, Either<Throwable, R>> alwaysWithCollection(final int lockMode, final DBBroker broker, final Txn transaction, final XmldbURI uri) {
        return withOp -> {
            Optional<Collection> optCollection = Optional.empty();
            try {
                optCollection = Optional.ofNullable(broker.openCollection(uri, lockMode));
                if(optCollection.isPresent()) {
                    return withOp.apply(optCollection.get());
                } else {
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("Creating collection " + uri);
                    }
                    final Collection collection = broker.getOrCreateCollection(transaction, uri); //Will hold a write lock in the transaction
                    broker.saveCollection(transaction, collection);
                    return withOp.apply(collection);
                }
            } catch(final IOException | TriggerException | PermissionDeniedException e) {
                return Either.Left(e);
            } finally {
                if(optCollection.isPresent()) {
                    optCollection.get().release(lockMode);
                }
            }
        };
    }
    
    private Either<Throwable, Tuple3<Long, Long, Sequence>> search(final DBBroker broker, final String query, final XmldbURI path, final List<Namespace> namespaces, final List<Tuple2<QName, Sequence>> variables, final Properties outputProperties, final boolean cache, final HttpServletRequest request, final HttpServletResponse response) {

        final Optional<Optional<Sequence>> cached = Optional.ofNullable(outputProperties.getProperty(Serializer.PROPERTY_SESSION_ID))
                .map(Integer::parseInt)
                .filter(sessionId -> sessionId > -1)
                .map(sessionId -> Optional.ofNullable(sessionManager.get(query, sessionId)));
        
        final Supplier<Either<Throwable, Tuple3<Long, Long, Sequence>>> timedNewSearch = () -> compileQuery(broker, new StringSource(query), Optional.of(path), Optional.empty(), Optional.of(new XmldbURI[] {path}), namespaces, variables, request, Optional.empty(), Optional.empty(), response).apply(timedCompiled -> executeQuery(broker, timedCompiled._2, outputProperties).map(timedExecuted -> timedExecuted.after(timedCompiled._1)));

        final Either<Throwable, Tuple3<Long, Long, Sequence>> result = cached.map(maybeResult -> maybeResult.<Either<Throwable, Tuple3<Long, Long, Sequence>>>map(r -> Either.Right(new Tuple3<>(0l, 0l, r))).orElseGet(() -> {
            LOG.debug("Cached query result not found. Probably timed out. Repeating query.");
            return timedNewSearch.get();
        })).orElseGet(timedNewSearch);

        if (cache && result.isRight()) {
            final int sessionId = sessionManager.add(query, result.right().get()._3);
            outputProperties.setProperty(Serializer.PROPERTY_SESSION_ID, Integer.toString(sessionId));
            if (!response.isCommitted()) {
                response.setIntHeader("X-Session-Id", sessionId);
            }
        }

        return result;
    }

    private Function<Function<Tuple2<Long, CompiledXQuery>, Either<Throwable, Tuple3<Long, Long, Sequence>>>, Either<Throwable, Tuple3<Long, Long, Sequence>>> compileQuery(final DBBroker broker, final Source source, final Optional<XmldbURI> baseUri, final Optional<XmldbURI> moduleLoadPath, final Optional<XmldbURI[]> staticallyKnownDocuments, final List<Namespace> namespaces, final List<Tuple2<QName, Sequence>> variables, final HttpServletRequest request, final Optional<XmldbURI> servletPath, final Optional<XmldbURI> pathInfo, final HttpServletResponse response) {
        return compiledOp -> {
            final XQuery xquery = broker.getBrokerPool().getXQueryService();
            final XQueryPool pool = broker.getBrokerPool().getXQueryPool();

            CompiledXQuery compiled = null;
            try {
                compiled = pool.borrowCompiledXQuery(broker, source);
                final XQueryContext context;
                response.setHeader("X-XQuery-Cached", Boolean.toString(compiled != null));
                if (compiled == null) {
                    context = new XQueryContext(broker.getBrokerPool());
                } else {
                    context = compiled.getContext();
                }

                if(baseUri.isPresent()) {
                    context.setBaseURI(new AnyURIValue(baseUri.get().toString()));
                }
                
                if(moduleLoadPath.isPresent()) {
                    context.setModuleLoadPath(moduleLoadPath.get().toString());
                }
                
                if(staticallyKnownDocuments.isPresent()) {
                    context.setStaticallyKnownDocuments(staticallyKnownDocuments.get());
                }

                for(final Namespace namespace : namespaces) {
                    context.declareNamespace(namespace.getPrefix(), namespace.getUri());
                }
                
                final HttpRequestWrapper reqw = declareVariables(context, variables, request, response);
                if(servletPath.isPresent()) {
                    reqw.setServletPath(servletPath.get().toString());
                }
                if(pathInfo.isPresent()) {
                    reqw.setPathInfo(pathInfo.get().toString());
                }

                final long compilationTime;
                if (compiled == null) {
                    final long compilationStart = System.currentTimeMillis();
                    compiled = xquery.compile(broker, context, source);
                    compilationTime = System.currentTimeMillis() - compilationStart;
                } else {
                    compiled.getContext().updateContext(context);
                    context.getWatchDog().reset();
                    compilationTime = 0;
                }

                DebuggeeFactory.checkForDebugRequest(request, context);
                
                return compiledOp.apply(new Tuple2<>(compilationTime, compiled));
            } catch(final IOException | PermissionDeniedException | XPathException e) {
                return Either.Left(e);
            } finally {
                if (compiled != null) {
                    compiled.getContext().runCleanupTasks();
                    pool.returnCompiledXQuery(source, compiled);
                }
            }
        };
    }

    private Either<Throwable, Tuple2<Long, Sequence>> executeQuery(final DBBroker broker, final CompiledXQuery compiled, final Properties outputProperties) {
        final XQuery xquery = broker.getBrokerPool().getXQueryService();

        Either<Throwable, Tuple2<Long, Sequence>> resultSequence;
        try {
            final long executeStart = System.currentTimeMillis();
            final Sequence queryResults = xquery.execute(broker, compiled, null, outputProperties);
            final long executionTime = System.currentTimeMillis() - executeStart;
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found " + queryResults.getItemCount() + " in " + executionTime + "ms.");
            }
            resultSequence = Either.Right(new Tuple2<>(executionTime, queryResults));
        } catch(final PermissionDeniedException | XPathException e) {
            resultSequence = Either.Left(e);
        }
        return resultSequence;
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
                                                final List<Tuple2<QName, Sequence>> variables, final HttpServletRequest request,
                                                final HttpServletResponse response) throws XPathException {

        final HttpRequestWrapper reqw = new HttpRequestWrapper(request, formEncoding, containerEncoding);
        final ResponseWrapper respw = new HttpResponseWrapper(response);

        context.declareVariable(RequestModule.PREFIX + ":request", reqw);
        context.declareVariable(ResponseModule.PREFIX + ":response", respw);
        context.declareVariable(SessionModule.PREFIX + ":session", reqw.getSession(false));

        //enable EXQuery Request Module (if present)
        try {
            if (xqueryContextExqueryRequestAttribute != null && cstrHttpServletRequestAdapter != null) {
                final Object exqueryRequestAdapter = cstrHttpServletRequestAdapter.newInstance(request, new FilterInputStreamCacheConfiguration() {
                    @Override
                    public String getCacheClass() {
                        return (String) context.getBroker().getConfiguration().getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY);
                    }
                });

                if (exqueryRequestAdapter != null) {
                    context.setAttribute(xqueryContextExqueryRequestAttribute, exqueryRequestAdapter);
                }
            }
        } catch (final InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("EXQuery Request Module is not present: " + e.getMessage(), e);
            }
        }

        for(final Tuple2<QName, Sequence> variable : variables) {
            final QName varName = variable._1;
            final Sequence varValue = variable._2;
            
            if (varName.getPrefix() != null && varName.getNamespaceURI() != null) {
                context.declareNamespace(varName.getPrefix(), varName.getNamespaceURI());
            }
            
            if(varName.getPrefix() != null) {
                context.declareVariable(varName.getPrefix() + ":" + varName.getLocalPart(), varValue);
            } else {
                context.declareVariable(varName.getLocalPart(), varValue);
            }
        }

        return reqw;
    }

    private Either<Throwable, List<Tuple2<QName, Sequence>>> extractVariables(final Element variables) {

        final List<Tuple2<QName, Sequence>> extractedVariables = new ArrayList<>();
        try {
            final ValueSequence varSeq = new ValueSequence();
            ((ElementImpl)variables).selectChildren(new NameTest(Type.ELEMENT, new QName(Variable.xmlKey(), Namespaces.EXIST_NS)), varSeq);
            for (final SequenceIterator i = varSeq.iterate(); i.hasNext(); ) {
                final ElementImpl variable = (ElementImpl) i.nextItem();
                // get the QName of the variable
                final ElementImpl qname = (ElementImpl) variable.getFirstChild(new NameTest(Type.ELEMENT, new QName("qname", Namespaces.EXIST_NS)));
                String localname = null, prefix = null, uri = null;
                NodeImpl child = (NodeImpl) qname.getFirstChild();
                while (child != null) {
                    switch (child.getLocalName()) {
                        case "localname":
                            localname = child.getStringValue();
                            break;
                        case "namespace":
                            uri = child.getStringValue();
                            break;
                        case "prefix":
                            prefix = child.getStringValue();
                            break;
                    }
                    child = (NodeImpl) child.getNextSibling();
                }

                if (localname == null) {
                    continue;
                }

                final QName name = new QName(localname, uri, prefix != null ? prefix : XMLConstants.DEFAULT_NS_PREFIX);

                // get serialized sequence
                final NodeImpl value = variable.getFirstChild(new NameTest(Type.ELEMENT, Marshaller.ROOT_ELEMENT_QNAME));
                final Sequence sequence = value == null ? Sequence.EMPTY_SEQUENCE : Marshaller.demarshall(value);
               
                // now declare variable
                extractedVariables.add(new Tuple2<>(name, sequence));
            }
        } catch (final XMLStreamException xe) {
            return Either.Left(new XPathException(xe.toString()));
        } catch(final XPathException e) {
            return Either.Left(e);
        }
        
        return Either.Right(extractedVariables);
    }

    public void setCreatedAndLastModifiedHeaders(
            final HttpServletResponse response, long created, long lastModified) {
        /**
         * Jetty ignores the milliseconds component -
         * https://bugs.eclipse.org/bugs/show_bug.cgi?id=342712 So lets work
         * around this by rounding up to the nearest whole second
         */
        final long lastModifiedMillisComp = lastModified % 1000;
        if (lastModifiedMillisComp > 0) {
            lastModified += 1000 - lastModifiedMillisComp;
        }
        final long createdMillisComp = created % 1000;
        if (createdMillisComp > 0) {
            created += 1000 - createdMillisComp;
        }

        response.addDateHeader("Last-Modified", lastModified);
        response.addDateHeader("Created", created);
    }

    // writes out a resource, uses asMimeType as the specified mime-type or if
    // null uses the type of the resource
    private Optional<Throwable> writeResourceAs(final DocumentImpl resource, final DBBroker broker, final Optional<String> stylesheet, final Charset encoding, String asMimeType, final Properties outputProperties, final HttpServletRequest request, final HttpServletResponse response) {

        // Do we have permission to read the resource
        if (!resource.getPermissions().validate(broker.getCurrentSubject(), Permission.READ)) {
            return Optional.of(new PermissionDeniedException("Not allowed to read resource"));
        }

        //get the document metadata
        final DocumentMetadata metadata = resource.getMetadata();
        final long lastModified = metadata.getLastModified();
        setCreatedAndLastModifiedHeaders(response, metadata.getCreated(), lastModified);

        /**
         * HTTP 1.1 RFC 2616 Section 14.25 *
         */
        //handle If-Modified-Since request header
        try {
            final long ifModifiedSince = request.getDateHeader("If-Modified-Since");
            if (ifModifiedSince > -1) {

                /*
                 a) A date which is later than the server's
                 current time is invalid.
                 */
                if (ifModifiedSince <= System.currentTimeMillis()) {

                    /*
                     b) If the variant has been modified since the If-Modified-Since
                     date, the response is exactly the same as for a normal GET.
                     */
                    if (lastModified <= ifModifiedSince) {

                        /*
                         c) If the variant has not been modified since a valid If-
                         Modified-Since date, the server SHOULD return a 304 (Not
                         Modified) response.
                         */
                        response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                        return Optional.empty();
                    }
                }
            }
        } catch (final IllegalArgumentException iae) {
            LOG.warn("Illegal If-Modified-Since HTTP Header sent on request, ignoring. " + iae.getMessage(), iae);
        }

        try {
            if (resource.getResourceType() == DocumentImpl.BINARY_FILE) {
                // binary resource

                if (asMimeType == null) { // wasn't a mime-type specified?
                    asMimeType = resource.getMetadata().getMimeType();
                }

                if (asMimeType.startsWith("text/")) {
                    response.setContentType(asMimeType + "; charset=" + encoding.name());
                } else {
                    response.setContentType(asMimeType);
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
            } else {
                // xml resource

                final Serializer serializer = broker.getSerializer();
                serializer.reset();

                //setup the http context
                final HttpContext httpContext = serializer.new HttpContext();
                final HttpRequestWrapper reqw = new HttpRequestWrapper(request, formEncoding, containerEncoding);
                httpContext.setRequest(reqw);
                httpContext.setSession(reqw.getSession(false));
                serializer.setHttpContext(httpContext);

                // Serialize the document
                SAXSerializer sax = null;
                try {
                    sax = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);

                    // use a stylesheet if specified in query parameters
                    if (stylesheet.isPresent()) {
                        serializer.setStylesheet(resource, stylesheet.get());
                    }
                    serializer.setProperties(outputProperties);
                    serializer.prepareStylesheets(resource);

                    if (asMimeType != null) { // was a mime-type specified?
                        response.setContentType(asMimeType + "; charset=" + encoding);
                    } else {
                        if (serializer.isStylesheetApplied()
                                || serializer.hasXSLPi(resource) != null) {

                            asMimeType = serializer.getStylesheetProperty(OutputKeys.MEDIA_TYPE);
                            if (!useDynamicContentType || asMimeType == null) {
                                asMimeType = MimeType.HTML_TYPE.getName();
                            }

                            if (LOG.isDebugEnabled()) {
                                LOG.debug(OutputKeys.MEDIA_TYPE + ": " + asMimeType);
                            }

                            response.setContentType(asMimeType + "; charset=" + encoding);
                        } else {
                            asMimeType = resource.getMetadata().getMimeType();
                            response.setContentType(asMimeType + "; charset=" + encoding);
                        }
                    }
                    if (asMimeType.equals(MimeType.HTML_TYPE.getName())) {
                        outputProperties.setProperty(OutputKeys.METHOD, "xhtml");
                        outputProperties.setProperty(OutputKeys.MEDIA_TYPE, "text/html; charset=" + encoding);
                        outputProperties.setProperty(OutputKeys.INDENT, YES);
                        outputProperties.setProperty(OutputKeys.OMIT_XML_DECLARATION, NO);
                    }

                    final Writer writer = new OutputStreamWriter(response.getOutputStream(), encoding);
                    sax.setOutput(writer, outputProperties);
                    serializer.setSAXHandlers(sax, sax);
                    serializer.toSAX(resource);
                    writer.flush();
                } catch (final SAXException | TransformerConfigurationException e) {
                    return Optional.of(e);
                } finally {
                    if (sax != null) {
                        SerializerPool.getInstance().returnObject(sax);
                    }
                }
            }

            return Optional.empty();
            
        } catch (final IOException e){
            return Optional.of(e);
        }
    }
    
    private void writeXPathExceptionHtml(final XPathException e, final Charset encoding, final Optional<String> query, final XmldbURI path, final HttpServletResponse response) throws IOException {
        if (!response.isCommitted()) {
            response.reset();
        }

        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MimeType.HTML_TYPE.getName() + "; charset=" + encoding);

        final Writer writer = new OutputStreamWriter(response.getOutputStream(), encoding);
        writer.write(QUERY_ERROR_HEAD);
        writer.write("<p class=\"path\"><span class=\"high\">Path</span>: ");
        writer.write("<a href=\"");
        writer.write(path.toString());
        writer.write("\">");
        writer.write(path.toString());
        writer.write("</a></p>");

        writer.write("<p class=\"errmsg\">");
        final String message = e.getMessage() == null ? e.toString() : e.getMessage();
        writer.write(XMLUtil.encodeAttrMarkup(message));
        writer.write("</p>");
        if (query.isPresent()) {
            writer.write("<p><span class=\"high\">Query</span>:</p><pre>");
            writer.write(XMLUtil.encodeAttrMarkup(query.get()));
            writer.write("</pre>");
        }
        writer.write("</body></html>");
    }

    private void writeXPathExceptionXml(final XPathException e, final Charset encoding, final Optional<String> query, final XmldbURI path, final HttpServletResponse response) throws IOException {
        if (!response.isCommitted()) {
            response.reset();
        }

        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.setContentType(MimeType.XML_TYPE.getName() + "; charset=" + encoding.name());

        final Writer writer = new OutputStreamWriter(response.getOutputStream(), encoding);
        writer.write("<?xml version=\"1.0\" ?>");
        writer.write("<exception><path>");
        writer.write(path.toString());
        writer.write("</path>");
        writer.write("<message>");
        final String message = Optional.of(e.getMessage()).orElse(e.toString());
        writer.write(XMLUtil.encodeAttrMarkup(message));
        writer.write("</message>");
        if (query.isPresent()) {
            writer.write("<query>");
            writer.write(XMLUtil.encodeAttrMarkup(query.get()));
            writer.write("</query>");
        }
        writer.write("</exception>");
    }

    /**
     * @param response
     * @param encoding
     * @param updateCount
     */
    private Optional<Throwable> writeXUpdateResult(final HttpServletResponse response, final Charset encoding, final long updateCount) {
        response.setContentType(MimeType.XML_TYPE.getName() + "; charset=" + encoding.name());
        try {
            final Writer writer = new OutputStreamWriter(response.getOutputStream(), encoding);
            writer.write("<?xml version=\"1.0\" ?>");
            writer.write("<exist:modifications xmlns:exist=\"" + Namespaces.EXIST_NS + "\" count=\"" + updateCount + "\">");
            writer.write(updateCount + " modifications processed.");
            writer.write("</exist:modifications>");
            writer.flush();
            return Optional.empty();
        } catch(final IOException e) {
            return Optional.of(e);
        }
    }

    /**
     * @param response
     * @param encoding
     * @param broker
     * @param transaction
     * @param collection
     * @return Optionally an error encountered during processing
     */
    private Optional<Throwable> writeCollection(final HttpServletResponse response, final Charset encoding, final DBBroker broker, final Txn transaction, final Collection collection) {

        response.setContentType(MimeType.XML_TYPE.getName() + "; charset=" + encoding.name());

        setCreatedAndLastModifiedHeaders(response, collection.getCreationTime(), collection.getCreationTime());

        final SAXSerializer serializer = (SAXSerializer) SerializerPool.getInstance().borrowObject(SAXSerializer.class);
        
        try {
            final Writer writer = new OutputStreamWriter(response.getOutputStream(), encoding);
            serializer.setOutput(writer, defaultProperties);

            serializer.startDocument();
            serializer.startPrefixMapping("exist", Namespaces.EXIST_NS);
            serializer.startElement(Namespaces.EXIST_NS, "result", "exist:result", new AttributesImpl());

            final Either<Throwable, Optional<AttributesImpl>> collectionAttrs = collectionAttributes(broker, transaction, collection.getURI(), true);
            if(collectionAttrs.isLeft()) {
                return Optional.of(collectionAttrs.left().get());
            } else {
                serializer.startElement(Namespaces.EXIST_NS, "collection", "exist:collection", collectionAttrs.right().get().get());
                
                //child collections
                for (final Iterator<XmldbURI> i = collection.collectionIterator(broker); i.hasNext(); ) {
                    final XmldbURI child = collection.getURI().append(i.next());
                    final Either<Throwable, Optional<AttributesImpl>> childCollectionAttrs = collectionAttributes(broker, transaction, child, false);
                    
                    if(childCollectionAttrs.isLeft()) {
                        return Optional.of(childCollectionAttrs.left().get());
                    } else {
                        final Optional<AttributesImpl> attrs = childCollectionAttrs.right().get();
                        if(attrs.isPresent()) {
                            serializer.startElement(Namespaces.EXIST_NS, "collection", "exist:collection", attrs.get());
                            serializer.endElement(Namespaces.EXIST_NS, "collection", "exist:collection");
                        }
                    }
                }
             
                //child documents
                for (final Iterator<DocumentImpl> i = collection.iterator(broker); i.hasNext(); ) {
                    final Optional<AttributesImpl> documentAttrs = documentAttributes(broker, transaction, i.next());
                    if(documentAttrs.isPresent()) {
                        serializer.startElement(Namespaces.EXIST_NS, "resource", "exist:resource", documentAttrs.get());
                        serializer.endElement(Namespaces.EXIST_NS, "resource", "exist:resource");
                    }
                }
                
                serializer.endElement(Namespaces.EXIST_NS, "collection", "exist:collection");
            }

            serializer.endElement(Namespaces.EXIST_NS, "result", "exist:result");
            serializer.endDocument();

            writer.flush();

            return Optional.empty();

        } catch (final IOException | PermissionDeniedException | SAXException e) {
            return Optional.of(e);
        } finally {
            if (serializer != null) {
                SerializerPool.getInstance().returnObject(serializer);
            }
        }
    }

    private Either<Throwable, Optional<AttributesImpl>> collectionAttributes(final DBBroker broker, final Txn transaction, final XmldbURI path, final boolean fullName) {
        return this.<Optional<AttributesImpl>>readCollection(broker, transaction, path).apply(collectionOpt ->     
            Either.Right(collectionOpt.filter(collection -> collection.getPermissionsNoLock().validate(broker.getCurrentSubject(), Permission.READ)).<AttributesImpl>map(collection -> {
                final AttributesImpl attrs = new AttributesImpl();
                
                attrs.addAttribute("", "name", "name", "CDATA", fullName ? path.toString() : path.lastSegment().toString());
                
                // add an attribute for the creation date as an xs:dateTime
                try {
                    final DateTimeValue dtCreated = new DateTimeValue(new Date(collection.getCreationTime()));
                    attrs.addAttribute("", "created", "created", "CDATA", dtCreated.getStringValue());
                } catch (final XPathException e) {
                    // fallback to long value
                    attrs.addAttribute("", "created", "created", "CDATA", String.valueOf(collection.getCreationTime()));
                }
                
                addPermissionAttributes(attrs, collection.getPermissionsNoLock());
                return attrs;
            }))
        );
    }

    private Optional<AttributesImpl> documentAttributes(final DBBroker broker, final Txn transaction, final DocumentImpl document) {
        if(document.getPermissions().validate(broker.getCurrentSubject(), Permission.READ)) {        
            final AttributesImpl attrs = new AttributesImpl();

            attrs.addAttribute("", "name", "name", "CDATA", document.getFileURI().toString());

            final DocumentMetadata metadata = document.getMetadata();
            // add an attribute for the creation date as an xs:dateTime
            try {
                final DateTimeValue dtCreated = new DateTimeValue(new Date(metadata.getCreated()));
                attrs.addAttribute("", "created", "created", "CDATA", dtCreated.getStringValue());
            } catch (final XPathException e) {
                // fallback to long value
                attrs.addAttribute("", "created", "created", "CDATA", String.valueOf(metadata.getCreated()));
            }

            // add an attribute for the last modified date as an
            // xs:dateTime
            try {
                final DateTimeValue dtLastModified = new DateTimeValue(new Date(metadata.getLastModified()));
                attrs.addAttribute("", "last-modified", "last-modified", "CDATA", dtLastModified.getStringValue());
            } catch (final XPathException e) {
                // fallback to long value
                attrs.addAttribute("", "last-modified", "last-modified", "CDATA", String.valueOf(metadata.getLastModified()));
            }

            addPermissionAttributes(attrs, document.getPermissions());
            return Optional.of(attrs);
        } else {
            return Optional.empty();
        }
    }

    private void addPermissionAttributes(final AttributesImpl attrs, final Permission perm) {
        attrs.addAttribute("", "owner", "owner", "CDATA", perm.getOwner().getName());
        attrs.addAttribute("", "group", "group", "CDATA", perm.getGroup().getName());
        attrs.addAttribute("", "permissions", "permissions", "CDATA", perm.toString());
    }

    private Optional<Throwable> writeResults(final HttpServletResponse response, final DBBroker broker, final Sequence results, int howmany, final int start, final boolean typed, final Properties outputProperties, final boolean wrap, final long compilationTime, final long executionTime) {
        // some xquery functions can write directly to the output stream
        // (response:stream-binary() etc...)
        // so if output is already written then dont overwrite here
        if (response.isCommitted()) {
            return Optional.empty();
        }

        // calculate number of results to return
        if (!results.isEmpty()) {
            final int rlen = results.getItemCount();
            if ((start < 1) || (start > rlen)) {
                return Optional.of(new IllegalArgumentException("Start parameter out of range"));
            }
            // FD : correct bound evaluation
            if (((howmany + start) > rlen) || (howmany <= 0)) {
                howmany = rlen - start + 1;
            }
        } else {
            howmany = 0;
        }
        
        final String method = outputProperties.getProperty(SERIALIZATION_METHOD_PROPERTY, "xml");
        if ("json".equals(method)) {
            return writeResultJSON(response, broker, results, howmany, start, outputProperties, wrap, compilationTime, executionTime);
        } else {
            return writeResultXML(response, broker, results, howmany, start, typed, outputProperties, wrap, compilationTime, executionTime);
        }
    }

    private Optional<Throwable> writeResultXML(final HttpServletResponse response, final DBBroker broker, final Sequence results, final int howmany, final int start, final boolean typed, final Properties outputProperties, final boolean wrap, final long compilationTime, final long executionTime) {
        // serialize the results to the response output stream
        outputProperties.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
        
        try {
            // set output headers
            final String encoding = outputProperties.getProperty(OutputKeys.ENCODING);
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
                outputProperties.setProperty(OutputKeys.METHOD, "xml");
            }

            final Writer writer = new OutputStreamWriter(response.getOutputStream(), encoding);
            final XQuerySerializer serializer = new XQuerySerializer(broker, outputProperties, writer);

            //Marshaller.marshall(broker, results, start, howmany, serializer.getContentHandler());
            serializer.serialize(results, start, howmany, wrap, typed, compilationTime, executionTime);
            writer.flush();
            return Optional.empty();
        }  catch (final IOException | SAXException | XPathException e) {
            return Optional.of(e);
        }
    }

    private Optional<Throwable> writeResultJSON(final HttpServletResponse response, final DBBroker broker, final Sequence results, int howmany, int start, final Properties outputProperties, final boolean wrap, final long compilationTime, final long executionTime) {

        // calculate number of results to return
        final int rlen = results.getItemCount();
        if (!results.isEmpty()) {
            if ((start < 1) || (start > rlen)) {
                return Optional.of(new IllegalArgumentException("Start parameter out of range"));
            }
            // FD : correct bound evaluation
            if (((howmany + start) > rlen) || (howmany <= 0)) {
                howmany = rlen - start + 1;
            }
        } else {
            howmany = 0;
        }

        final Serializer serializer = broker.getSerializer();
        serializer.reset();
        outputProperties.setProperty(Serializer.GENERATE_DOC_EVENTS, "false");
        try {
            serializer.setProperties(outputProperties);
            final JSONObject root = new JSONObject();
            root.addObject(new JSONSimpleProperty("start", Integer.toString(start), true));
            root.addObject(new JSONSimpleProperty("count", Integer.toString(howmany), true));
            root.addObject(new JSONSimpleProperty("hits", Integer.toString(results.getItemCount()), true));
            if (outputProperties.getProperty(Serializer.PROPERTY_SESSION_ID) != null) {
                root.addObject(new JSONSimpleProperty("session",
                        outputProperties.getProperty(Serializer.PROPERTY_SESSION_ID)));
            }
            root.addObject(new JSONSimpleProperty("compilationTime", Long.toString(compilationTime), true));
            root.addObject(new JSONSimpleProperty("executionTime", Long.toString(executionTime), true));

            final JSONObject data = new JSONObject("data");
            root.addObject(data);

            for (int i = --start; i < start + howmany; i++) {
                final Item item = results.itemAt(i);
                if (Type.subTypeOf(item.getType(), Type.NODE)) {
                    final NodeValue value = (NodeValue) item;
                    JSONValue json;
                    if ("json".equals(outputProperties.getProperty(OutputKeys.METHOD, "xml"))) {
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
            final Writer writer = new OutputStreamWriter(response.getOutputStream(), outputProperties.getProperty(OutputKeys.ENCODING));
            root.serialize(writer, true);
            writer.flush();
            return Optional.empty();
        } catch (final IOException | SAXException | XPathException e) {
            return Optional.of(e);
        }
    }

    private boolean isExecutableType(final DocumentImpl resource) {
        return resource != null && (
                MimeType.XQUERY_TYPE.getName().equals(resource.getMetadata().getMimeType())  // an xquery
                || MimeType.XPROC_TYPE.getName().equals(resource.getMetadata().getMimeType())  // an xproc
        );
    }
    
    private Optional<String> getQueryStringParameter(final ServletRequest request, final RESTServerParameter parameter) {
        return Optional.ofNullable(request.getParameter(parameter.queryStringKey()));
    }
    
    /**
     * Retrieves a parameter from the Query String of the request
     */
    private <T> Optional<T> getQueryStringParameter(final ServletRequest request, final RESTServerParameter parameter, final Function<String, T> transform) {
        return Optional.ofNullable(request.getParameter(parameter.queryStringKey())).map(transform);
    }

    private Optional<String> getBodyParameter(final Element element, final RESTServerParameter parameter) {
        return Optional.ofNullable(element.getAttribute(parameter.xmlKey()));
    }

    private <T> Optional<T> getBodyParameter(final Element element, final RESTServerParameter parameter, final Function<String, T> transform) {
        return Optional.ofNullable(element.getAttribute(parameter.xmlKey())).map(transform);
    }

    private static boolean parseOption(final String option) {
        return option.toLowerCase().equals(YES);
    }
}
