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
package org.exist.http.servlets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.debuggee.DebuggeeFactory;
import org.exist.dom.persistent.XMLUtil;
import org.exist.http.Descriptor;
import org.exist.security.AuthenticationException;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.security.internal.web.HttpAccount;
import org.exist.source.*;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exist.util.MimeTable;
import org.exist.util.serializer.XQuerySerializer;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.xml.sax.SAXException;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;

import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import javax.annotation.Nullable;
import javax.xml.transform.OutputKeys;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Serial;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

/**
 * Servlet to generate HTML output from an XQuery file.
 * <p>
 * The servlet responds to an URL pattern as specified in the
 * WEB-INF/web.xml configuration file of the application. It will
 * interpret the path with which it is called as leading to a valid
 * XQuery file. The XQuery file is loaded, compiled and executed.
 * Any output of the script is sent back to the client.
 * <p>
 * The servlet accepts the following initialization parameters in web.xml:
 *
 * <table style="border: 0">
 *  <caption>Initialization Parameters</caption>
 * 	<tr><td>user</td><td>The user identity with which the script is executed.</td></tr>
 * 	<tr><td>password</td><td>Password for the user.</td></tr>
 * 	<tr><td>uri</td><td>A valid XML:DB URI leading to the root collection used to
 * 	process the request.</td></tr>
 * 	<tr><td>encoding</td><td>The character encoding used for XQuery files.</td></tr>
 * 	<tr><td>container-encoding</td><td>The character encoding used by the servlet
 * 	container.</td></tr>
 * 	<tr><td>form-encoding</td><td>The character encoding used by parameters posted
 * 	from HTML for
 * ms.</td></tr>
 * </table>
 * <p>
 * User identity and password may also be specified through the HTTP session attributes
 * "user" and "password". These attributes will overwrite any other settings.
 *
 * @author <a href="mailto:wolfgang@exist-db.org">Wolfgang Meier</a>
 */
public class XQueryServlet extends AbstractExistHttpServlet {

    @Serial
    private static final long serialVersionUID = 5266794852401553015L;

    private static final Logger LOG = LogManager.getLogger(XQueryServlet.class);

    // Request attributes
    public static final String ATTR_XQUERY_USER = "xquery.user";
    public static final String ATTR_XQUERY_PASSWORD = "xquery.password";
    public static final String ATTR_XQUERY_SOURCE = "xquery.source";
    public static final String ATTR_XQUERY_URL = "xquery.url";
    public static final String ATTR_XQUERY_REPORT_ERRORS = "xquery.report-errors";
    public static final String ATTR_XQUERY_ATTRIBUTE = "xquery.attribute";
    public static final String ATTR_TIMEOUT = "xquery.timeout";
    public static final String ATTR_MAX_NODES = "xquery.max-nodes";
    public static final String ATTR_MODULE_LOAD_PATH = "xquery.module-load-path";

    public final static XmldbURI DEFAULT_URI = XmldbURI.EMBEDDED_SERVER_URI.append(XmldbURI.ROOT_COLLECTION_URI);
    public final static String DEFAULT_CONTENT_TYPE = "text/html";

    public final static String DRIVER = "org.exist.xmldb.DatabaseImpl";

    private XmldbURI collectionURI = null;

    private String encoding = null;
    private String contentType = null;
    private boolean hideErrorMessages = false;

    @Override
    public Logger getLog() {
        return LOG;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        final String confCollectionURI = config.getInitParameter("uri");
        if (confCollectionURI == null) {
            collectionURI = DEFAULT_URI;

        } else {
            try {
                collectionURI = XmldbURI.xmldbUriFor(confCollectionURI);

            } catch (final URISyntaxException e) {
                throw new ServletException("Invalid XmldbURI for parameter 'uri': " + e.getMessage(), e);
            }
        }

        encoding = config.getInitParameter("encoding");
        if (encoding == null) {
            encoding = DEFAULT_ENCODING;
        }
        getLog().info("encoding = {}", encoding);

        contentType = config.getInitParameter("content-type");
        if (contentType == null) {
            contentType = DEFAULT_CONTENT_TYPE;
        }

        hideErrorMessages = Optional.ofNullable(config.getInitParameter("hide-error-messages")).map(Boolean::parseBoolean)
                .orElse(false);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        process(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
        processWithLoggingWrapper(req, response);
    }

    //-------------------------------
    // doPut and doDelete added by Andrzej Taramina (andrzej@chaeron.com)
    // Date: Sept/05/2007
    //
    // These methods were added so that you can issue an HTTP PUT or DELETE request and have it serviced by an XQuery.
    // NOTE: The XQuery referenced in the target URL of the request will be executed and the PUT/DELETE request will be passed to it
    //
    //-------------------------------

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
        processWithLoggingWrapper(req, response);
    }

    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        process(request, response);
    }

    /**
     * Processes a request with a body (POST, PUT). If we are logging the requests we must wrap the
     * HttpServletRequest in an HttpServletRequestWrapper, otherwise we cannot access the POST
     * parameters from the content body of the request!!! - deliriumsky
     */
    private void processWithLoggingWrapper(final HttpServletRequest req, final HttpServletResponse response) throws ServletException, IOException {
        HttpServletRequest request = null;
        try {
            final Descriptor descriptor = Descriptor.getDescriptorSingleton();
            if (descriptor != null && descriptor.allowRequestLogging()) {
                request = new HttpServletRequestWrapper(() -> (String) getPool().getConfiguration().getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY), req, getFormEncoding());
            } else {
                request = req;
            }

            process(request, response);
        } finally {
            if (request instanceof HttpServletRequestWrapper wrapper) {
                wrapper.close();
            }
        }
    }

    /**
     * Processes incoming HTTP requests for XQuery.
     *
     * @param request  the http request
     * @param response the http response
     * @throws ServletException if the servlet raises an exception
     * @throws IOException      if an I/O error occurs
     */
    protected void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // the request path relative to the context, e.g. "/db/apps/optimize.xql" -- kept around
        // (regardless of whether pathTranslated already gave us a disk path) so that a request
        // which cannot be resolved to a file on disk can still be tried against the database.
        final String contextRelativePath = contextRelativePath(request);
        final Descriptor descriptor = Descriptor.getDescriptorSingleton();
        final String path = translatePath(request, contextRelativePath, descriptor);

        final ServletOutputStream sout = response.getOutputStream();
        final PrintWriter output = new PrintWriter(new OutputStreamWriter(sout, getFormEncoding()));
        response.addHeader("pragma", "no-cache");
        response.addHeader("Cache-Control", "no-cache");

        final String moduleLoadPath = moduleLoadPath(request);
        final HttpSession session = request.getSession(false);
        final Subject user = resolveUser(request, response, session);

        final Source source = resolveSource(request, response, output, user, moduleLoadPath, path, contextRelativePath);
        if (source == null) {
            return;
        }

        final boolean reportErrors = "YES".equalsIgnoreCase((String) request.getAttribute(ATTR_XQUERY_REPORT_ERRORS));

        if (isSourceViewRequest(request, descriptor)) {
            showSource(request, response, output, source, user, path, descriptor);
            return;
        }

        final String requestAttr = (String) request.getAttribute(ATTR_XQUERY_ATTRIBUTE);
        try {
            execute(request, response, output, session, source, user, moduleLoadPath, path, requestAttr);

        } catch (final PermissionDeniedException e) {
            sendPermissionDenied(request, response, user, "No permission to execute XQuery for: " + escapeHtml(path) + " denied.");
            return;

        } catch (final XPathException e) {
            final Logger logger = getLog();
            if (logger.isDebugEnabled()) {
                logger.debug(e.getMessage(), e);
            }
            reportError(response, output, reportErrors, e);

        } catch (final Throwable e) {
            getLog().error(e.getMessage(), e);
            reportError(response, output, reportErrors, e);
        }

        output.flush();
        output.close();
    }

    private static String contextRelativePath(final HttpServletRequest request) {
        final String contextRelativePath = request.getRequestURI().substring(request.getContextPath().length());
        final int p = contextRelativePath.lastIndexOf(';');
        return p == Constants.STRING_NOT_FOUND ? contextRelativePath : contextRelativePath.substring(0, p);
    }

    /**
     * Resolves the request to a path on disk, then performs the descriptor actions: logs the request
     * if specified in the descriptor and maps the path if a mapping is specified in the descriptor.
     *
     * @return the path, or {@code null} if the request cannot be resolved to a path on disk
     */
    private @Nullable String translatePath(final HttpServletRequest request, final String contextRelativePath, @Nullable final Descriptor descriptor) {
        String path = request.getPathTranslated();
        if (path == null) {
            path = getServletContext().getRealPath(contextRelativePath);
        }

        if (descriptor != null && !descriptor.requestsFiltered()) {
            descriptor.doLogRequestInReplayLog(request);
            path = descriptor.mapPath(path);
        }
        return path;
    }

    private String moduleLoadPath(final HttpServletRequest request) {
        final Object loadPathAttrib = request.getAttribute(ATTR_MODULE_LOAD_PATH);
        if (loadPathAttrib != null) {
            return getValue(loadPathAttrib);
        }

        String requestPath = request.getRequestURI();
        final int p = requestPath.lastIndexOf('/');
        if (p != Constants.STRING_NOT_FOUND) {
            requestPath = requestPath.substring(0, p);
        }
        return getServletContext().getRealPath(requestPath.substring(request.getContextPath().length()));
    }

    /**
     * Determines the user: first the request attribute "xquery.user", then the session attributes
     * "user" and "password", then the user of the servlet request, then the authenticator; falls
     * back to the default user.
     */
    private Subject resolveUser(final HttpServletRequest request, final HttpServletResponse response, @Nullable final HttpSession session) throws IOException {
        Subject user = getDefaultUser();

        final Object userAttrib = request.getAttribute(ATTR_XQUERY_USER);
        if (userAttrib != null || (session != null && request.isRequestedSessionIdValid())) {
            final String username;
            final String password;
            if (userAttrib != null) {
                username = getValue(userAttrib);
                password = getValue(request.getAttribute(ATTR_XQUERY_PASSWORD));
            } else {
                username = getSessionAttribute(session, "user");
                password = getSessionAttribute(session, "password");
            }
            user = authenticate(username, password, user);
        }

        if (user != getDefaultUser()) {
            return user;
        }

        final Subject requestUser = HttpAccount.getUserFromServletRequest(request);
        if (requestUser != null) {
            return requestUser;
        }
        final Subject authenticatedUser = getAuthenticator().authenticate(request, response, false);
        return authenticatedUser != null ? authenticatedUser : user;
    }

    //TODO authentication should use super.authenticate(...) !!!
    private Subject authenticate(@Nullable final String username, @Nullable final String password, final Subject defaultUser) {
        if (username == null || password == null) {
            return defaultUser;
        }
        try {
            final Subject newUser = getPool().getSecurityManager().authenticate(username, password);
            if (newUser != null && newUser.isAuthenticated()) {
                return newUser;
            }
        } catch (final AuthenticationException e) {
            getLog().error("User can not be authenticated ({}).", username);
        }
        return defaultUser;
    }

    /**
     * Resolves the query source from the request attribute "xquery.source", the request attribute
     * "xquery.url" or the request path, in that order.
     *
     * @return the source, or {@code null} if it cannot be resolved, in which case an error has been sent
     */
    private @Nullable Source resolveSource(final HttpServletRequest request, final HttpServletResponse response,
                                           final PrintWriter output, final Subject user, final String moduleLoadPath, @Nullable final String path,
                                           final String contextRelativePath) throws ServletException {
        final Object sourceAttrib = request.getAttribute(ATTR_XQUERY_SOURCE);
        final Object urlAttrib = request.getAttribute(ATTR_XQUERY_URL);
        final Source source;
        if (sourceAttrib != null) {
            source = sourceFromAttribute(sourceAttrib);
        } else if (urlAttrib != null) {
            source = sourceFromUrl(response, output, user, moduleLoadPath, urlAttrib.toString());
        } else {
            source = sourceFromPath(user, path, contextRelativePath);
            if (source == null) {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                sendError(output, "Cannot read source file", path != null ? path : contextRelativePath);
                return null;
            }
        }

        if (source == null) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            sendError(output, "Source not found", path);
        }
        return source;
    }

    private static Source sourceFromAttribute(final Object sourceAttrib) throws ServletException {
        if (sourceAttrib instanceof Item item) {
            try {
                return new StringSource(item.getStringValue());
            } catch (final XPathException e) {
                throw new ServletException("Failed to read XQuery source string from " +
                        "request attribute '" + ATTR_XQUERY_SOURCE + "': " + e.getMessage(), e);
            }
        }
        return new StringSource(sourceAttrib.toString());
    }

    private @Nullable Source sourceFromUrl(final HttpServletResponse response, final PrintWriter output,
                                           final Subject user, final String moduleLoadPath, final String url) {
        try (final DBBroker broker = getPool().get(Optional.ofNullable(user))) {
            // resolved for execution: a stored query needs EXECUTE, not READ. The ?_source view
            // below still validates READ, so an unreadable query can be run but not viewed
            final Source source = SourceFactory.getSourceForExecution(broker, moduleLoadPath, url, true);
            if (source == null) {
                final String msg = "Could not read source: context=" + moduleLoadPath + ", location=" + url;
                getLog().error(msg);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                sendError(output, "Error", msg);
            }
            return source;
        } catch (final Exception e) {
            getLog().error(e.getMessage(), e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendError(output, "Error", e.getMessage());
            return null;
        }
    }

    private @Nullable Source sourceFromPath(final Subject user, @Nullable final String path, final String contextRelativePath) {
        if (path != null) {
            final Path f = Path.of(path);
            if (Files.isReadable(f)) {
                return new FileSource(f, Charset.forName(encoding), true);
            }
        }

        // No readable file on disk (or no disk path at all -- the Servlet API allows both
        // HttpServletRequest#getPathTranslated() and ServletContext#getRealPath(String) to
        // return null, which happens e.g. for a request forwarded to a resource that only
        // exists in the database). Fall back to resolving it there before giving up.
        // See https://github.com/eXist-db/exist/issues/6615
        try (final DBBroker broker = getPool().get(Optional.ofNullable(user))) {
            return SourceFactory.getSourceForExecution(broker, null, contextRelativePath, true);
        } catch (final EXistException | IOException | PermissionDeniedException e) {
            getLog().error(e.getMessage(), e);
            return null;
        }
    }

    /**
     * Should we display the source of the XQuery instead of executing it? Only for a GET request
     * with the parameter {@code _source=yes}.
     */
    private static boolean isSourceViewRequest(final HttpServletRequest request, @Nullable final Descriptor descriptor) {
        return "GET".equals(request.getMethod().toUpperCase())
                && "yes".equals(request.getParameter("_source"))
                && descriptor != null;
    }

    private void showSource(final HttpServletRequest request, final HttpServletResponse response, final PrintWriter output,
                            final Source source, final Subject user, @Nullable final String path, final Descriptor descriptor) throws IOException {
        //check are we allowed to show the xquery source - descriptor.xml
        if (!descriptor.allowSource(path)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Permission to view XQuery source for: " + escapeHtml(path) + " denied. Must be explicitly defined in descriptor.xml");
            return;
        }

        if (source instanceof DBSource bSource) {
            try {
                bSource.validate(user, Permission.READ);
            } catch (final PermissionDeniedException e) {
                sendPermissionDenied(request, response, user, "Permission to view XQuery source for: " + escapeHtml(path) + " denied. (no read access)");
                return;
            }
        }

        response.setContentType("text/plain; charset=" + getFormEncoding());
        output.write(source.getContent());
        output.flush();
    }

    private void sendPermissionDenied(final HttpServletRequest request, final HttpServletResponse response,
                                      final Subject user, final String message) throws IOException {
        if (getDefaultUser().equals(user)) {
            getAuthenticator().sendChallenge(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, message);
        }
    }

    private void execute(final HttpServletRequest request, final HttpServletResponse response, final PrintWriter output,
                         @Nullable final HttpSession session, final Source source, final Subject user, final String moduleLoadPath,
                         @Nullable final String path, @Nullable final String requestAttr)
            throws EXistException, PermissionDeniedException, XPathException, SAXException, IOException {
        try (final DBBroker broker = getPool().get(Optional.ofNullable(user))) {
            final XQuery xquery = broker.getBrokerPool().getXQueryService();
            final CompiledXQuery query = prepareQuery(broker, xquery, source, user, moduleLoadPath);
            final XQueryContext context = query.getContext();

            final Properties outputProperties = new Properties();
            outputProperties.put("base-uri", collectionURI.toString());

            final HttpRequestWrapper reqw = new HttpRequestWrapper(request, getFormEncoding(), getContainerEncoding());
            final ResponseWrapper respw = new HttpResponseWrapper(response);
            context.setHttpContext(new XQueryContext.HttpContext(reqw, respw, session != null ? new HttpSessionWrapper(session) : null));

            applyWatchdogOptions(request, context);

            DebuggeeFactory.checkForDebugRequest(request, context);

            final Sequence resultSequence = executeQuery(broker, xquery, query, source, outputProperties);

            setContentType(response, outputProperties.getProperty(OutputKeys.MEDIA_TYPE), path);

            if (requestAttr != null && (XmldbURI.API_LOCAL.equals(collectionURI.getApiName()))) {
                request.setAttribute(requestAttr, resultSequence);
            } else {
                serialize(broker, context, outputProperties, output, resultSequence);
            }
        }
    }

    /**
     * Borrows the compiled query from the pool, or compiles it.
     */
    private CompiledXQuery prepareQuery(final DBBroker broker, final XQuery xquery, final Source source,
                                        final Subject user, final String moduleLoadPath) throws EXistException, PermissionDeniedException, XPathException {
        final CompiledXQuery pooled = getPool().getXQueryPool().borrowCompiledXQuery(broker, source);

        final XQueryContext context;
        if (pooled == null) {
            context = new XQueryContext(getPool());
            context.setModuleLoadPath(moduleLoadPath);
        } else {
            context = pooled.getContext();
            context.setModuleLoadPath(moduleLoadPath);
            context.prepareForReuse();
        }

        // a caller which may execute but not read the query must not learn anything about its
        // source from a failure. Recomputed per request, as the compiled query is pooled and
        // shared between users, and set before compiling: a compile error never reaches
        // XQuery#execute, which computes the level for a runtime failure
        context.setErrorDisclosure(ErrorDisclosure.of(source, user));

        if (pooled != null) {
            return pooled;
        }

        try {
            return xquery.compile(context, source);

        } catch (final XPathException ex) {
            final XPathException disclosed = ErrorDisclosure.disclose(context, ex);
            throw new EXistException("Cannot compile xquery: " + disclosed.getMessage(), disclosed);

        } catch (final IOException ex) {
            throw new EXistException("I/O exception while compiling xquery: " + ex.getMessage(), ex);
        }
    }

    private static void applyWatchdogOptions(final HttpServletRequest request, final XQueryContext context) throws EXistException {
        final String timeoutOpt = (String) request.getAttribute(ATTR_TIMEOUT);
        if (timeoutOpt != null) {
            try {
                context.getWatchDog().setTimeout(Long.parseLong(timeoutOpt));
            } catch (final NumberFormatException e) {
                throw new EXistException("Bad timeout option: " + timeoutOpt);
            }
        }

        final String maxNodesOpt = (String) request.getAttribute(ATTR_MAX_NODES);
        if (maxNodesOpt != null) {
            try {
                context.getWatchDog().setMaxNodes(Integer.parseInt(maxNodesOpt));
            } catch (final NumberFormatException e) {
                throw new EXistException("Bad max-nodes option: " + maxNodesOpt);
            }
        }
    }

    private Sequence executeQuery(final DBBroker broker, final XQuery xquery, final CompiledXQuery query,
                                  final Source source, final Properties outputProperties) throws XPathException, PermissionDeniedException {
        final XQueryContext context = query.getContext();
        try {
            return xquery.execute(broker, query, null, outputProperties);

        } catch (final XPathException e) {
            // a read-blind caller learns only that the execution failed; the real error is
            // logged server-side with a correlation id
            throw ErrorDisclosure.disclose(context, e);

        } finally {
            context.runCleanupTasks();
            getPool().getXQueryPool().returnCompiledXQuery(source, query);
        }
    }

    private void setContentType(final HttpServletResponse response, @Nullable final String mediaType, @Nullable final String path) {
        if (mediaType != null) {
            if (response.isCommitted()) {
                return;
            }
            if (MimeTable.getInstance().isTextContent(mediaType)) {
                response.setContentType(mediaType + "; charset=" + getFormEncoding());
                response.setCharacterEncoding(getFormEncoding());
            } else {
                response.setContentType(mediaType);
            }
            return;
        }

        String contentType = this.contentType;
        try {
            contentType = getServletContext().getMimeType(path);
            if (contentType == null) {
                contentType = this.contentType;
            }
        } catch (final Throwable e) {
            contentType = this.contentType;
        } finally {
            if (MimeTable.getInstance().isTextContent(contentType)) {
                contentType += "; charset=" + getFormEncoding();
            }
            response.setContentType(contentType);
        }
    }

    private static void serialize(final DBBroker broker, final XQueryContext context, final Properties outputProperties,
                                  final PrintWriter output, final Sequence resultSequence) throws XPathException, SAXException {
        final XQuerySerializer serializer = new XQuerySerializer(broker, outputProperties, output);
        try {
            // serialization runs the tail of a lazily-evaluated query, so a runtime failure can
            // surface here rather than in execute() above; it must be filtered just the same
            serializer.serialize(resultSequence);
        } catch (final XPathException e) {
            throw ErrorDisclosure.disclose(context, e);
        } catch (final SAXException e) {
            final XPathException generic = ErrorDisclosure.discloseGeneric(context, e);
            if (generic != null) {
                throw generic;
            }
            throw e;
        }
    }

    private void reportError(final HttpServletResponse response, final PrintWriter output, final boolean reportErrors, final Throwable e) {
        if (reportErrors) {
            writeError(output, e);
        } else {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            sendError(output, "Error", e.getMessage());
        }
    }

    private String getSessionAttribute(HttpSession session, String attribute) {
        final Object obj = session.getAttribute(attribute);
        return getValue(obj);
    }

    private String getValue(Object obj) {
        if (obj == null) {
            return null;
        }

        if (obj instanceof Sequence sequence)
            try {
                return sequence.getStringValue();
            } catch (final XPathException e) {
                return null;
            }
        return obj.toString();
    }

    private void writeError(final PrintWriter out, final Throwable e) {
        out.print("<error>");
        if (e.getMessage() != null && !hideErrorMessages) {
            out.print(escapeHtml(e.getMessage()));
        }
        out.println("</error>");
    }

    private void sendError(final PrintWriter out, final String message, final String description) {
        out.print(renderErrorPage(message, description, hideErrorMessages));
        out.flush();
    }

    /**
     * Builds the HTML error page as a string, escaping both the message and the
     * description so that a user-derived value (e.g. a requested source path
     * embedded in the description) cannot inject markup into the response.
     *
     * @param message         the message to display; may be {@code null}
     * @param description     the description to display; may be {@code null}
     * @param hideDescription {@code true} to suppress the description entirely
     * @return the complete HTML error page
     */
    static String renderErrorPage(final String message, final String description, final boolean hideDescription) {
        final StringBuilder out = new StringBuilder();
        out.append("<html><head>");
        out.append("<title>XQueryServlet Error</title>");
        out.append("<link rel=\"stylesheet\" type=\"text/css\" href=\"error.css\"></link></head>");
        out.append("<body><h1>Error found</h1>");
        out.append("<div class='message'><b>Message: </b>");
        out.append(message == null ? "" : escapeHtml(message));
        out.append("</div>");

        if (!hideDescription && description != null) {
            out.append("<div class='description'><pre>");
            out.append(escapeHtml(description));
            out.append("</pre></div>");
        }

        out.append("</body></html>");
        return out.toString();
    }

    /**
     * Escapes the characters that are significant in HTML text content: {@code &}, {@code <},
     * {@code >}, {@code "} and {@code '}. Unlike {@link XMLUtil#encodeAttrMarkup(String)} (an XML
     * attribute-value escaper that intentionally leaves an already-well-formed entity reference
     * alone), this always escapes {@code &} literally, which is what's needed when embedding an
     * arbitrary user-derived value into an HTML response.
     *
     * @param str the string to escape; {@code null} is returned as the literal {@code "null"} to
     *            match Java's string-concatenation behaviour at the call sites that don't already guard
     *            against {@code null}.
     * @return the escaped string.
     */
    private static String escapeHtml(final String str) {
        if (str == null) {
            return "null";
        }

        final StringBuilder buf = new StringBuilder(str.length());
        for (int i = 0; i < str.length(); i++) {
            final char ch = str.charAt(i);
            buf.append(switch (ch) {
                case '&' -> "&amp;";
                case '<' -> "&lt;";
                case '>' -> "&gt;";
                case '"' -> "&quot;";
                case '\'' -> "&#39;";
                default -> String.valueOf(ch);
            });
        }
        return buf.toString();
    }
}
