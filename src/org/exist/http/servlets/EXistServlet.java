/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2010 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.http.servlets;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.http.BadRequestException;
import org.exist.http.Descriptor;
import org.exist.http.NotFoundException;
import org.exist.http.RESTServer;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.util.Configuration;
import org.exist.validation.XmlLibraryChecker;
import org.exist.xmldb.XmldbURI;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.EOFException;
import java.io.IOException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

/**
 * Implements the REST-style interface if eXist is running within a Servlet
 * engine. The real work is done by class {@link org.exist.http.RESTServer}.
 *
 * @author wolf
 */
public class EXistServlet extends AbstractExistHttpServlet {

    private static final long serialVersionUID = -3563999345725645647L;
    private final static Logger LOG = LogManager.getLogger(EXistServlet.class);
    private RESTServer srvREST;

    public enum FeatureEnabled {
        FALSE,
        TRUE,
        AUTHENTICATED_USERS_ONLY
    }

    @Override
    public Logger getLog() {
        return LOG;
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        String useDynamicContentType = config.getInitParameter("dynamic-content-type");
        if (useDynamicContentType == null) {
            useDynamicContentType = "no";
        }

        final FeatureEnabled xquerySubmission = parseFeatureEnabled(config, "xquery-submission", FeatureEnabled.TRUE);
        final FeatureEnabled xupdateSubmission = parseFeatureEnabled(config,"xupdate-submission", FeatureEnabled.TRUE);

        // Instantiate REST Server
        srvREST = new RESTServer(getPool(), getFormEncoding(), getContainerEncoding(), useDynamicContentType.equalsIgnoreCase("yes")
                || useDynamicContentType.equalsIgnoreCase("true"), isInternalOnly(), xquerySubmission, xupdateSubmission);

        // XML lib checks....
        XmlLibraryChecker.check();
    }

    private FeatureEnabled parseFeatureEnabled(final ServletConfig config, final String paramName, final FeatureEnabled defaultValue) {
        final String paramValue = config.getInitParameter(paramName);
        if(paramValue != null) {
            if (paramValue.equals("disabled")) {
                return FeatureEnabled.FALSE;
            } else if (paramValue.equals("enabled")) {
                return FeatureEnabled.TRUE;
            } else if (paramValue.equals("authenticated")) {
                return FeatureEnabled.AUTHENTICATED_USERS_ONLY;
            }
        }

        return defaultValue;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * javax.servlet.http.HttpServlet#doPut(javax.servlet.http.HttpServletRequest
     * , javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // first, adjust the path
        String path = adjustPath(request);

        // second, perform descriptor actions
        final Descriptor descriptor = Descriptor.getDescriptorSingleton();
        if (descriptor != null) {
            // TODO: figure out a way to log PUT requests with
            // HttpServletRequestWrapper and
            // Descriptor.doLogRequestInReplayLog()

            // map's the path if a mapping is specified in the descriptor
            path = descriptor.mapPath(path);
        }

        // third, authenticate the user
        final Subject user = authenticate(request, response);
        if (user == null) {
            // You now get a challenge if there is no user
            // response.sendError(HttpServletResponse.SC_FORBIDDEN,
            // "Permission denied: unknown user or password");
            return;
        }

        try(final DBBroker broker = getPool().get(Optional.of(user));
                final Txn transaction = getPool().getTransactionManager().beginTransaction()) {
            final XmldbURI dbpath = XmldbURI.createInternal(path);
            final Collection collection = broker.getCollection(dbpath);
            if (collection != null) {
                response.sendError(400, "A PUT request is not allowed against a plain collection path.");
                return;
            }
            srvREST.doPut(broker, transaction, dbpath, request, response);

            transaction.commit();

        } catch (final BadRequestException e) {
            if (response.isCommitted()) {
                throw new ServletException(e.getMessage(), e);
            }
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (final PermissionDeniedException e) {
            // If the current user is the Default User and they do not have permission
            // then send a challenge request to prompt the client for a username/password.
            // Else return a FORBIDDEN Error
            if (user != null && user.equals(getDefaultUser())) {
                getAuthenticator().sendChallenge(request, response);
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            }
        } catch (final EXistException e) {
            if (response.isCommitted()) {
                throw new ServletException(e.getMessage(), e);
            }
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (final Throwable e) {
            LOG.error(e);
            throw new ServletException("An unknown error occurred: " + e.getMessage(), e);
        }
    }

    /**
     * @param request
     */
    private String adjustPath(HttpServletRequest request) throws ServletException {
        String path = request.getPathInfo();

        if (path == null) {
            return "";
        }

        if (LOG.isDebugEnabled()) {
            LOG.debug(" In: " + path);
        }

        // path contains both required and superficial escapes,
        // as different user agents use different conventions;
        // for the sake of interoperability, remove any unnecessary escapes
        try {
            // URI.create undoes _all_ escaping, so protect slashes first
            URI u = URI.create("file://" + path.replaceAll("%2F", "%252F"));
            // URI four-argument constructor recreates all the necessary ones
            URI v = new URI("http", "host", u.getPath(), null).normalize();
            // unprotect slashes in now normalized path
            path = v.getRawPath().replaceAll("%252F", "%2F");
        } catch (final URISyntaxException e) {
            throw new ServletException(e.getMessage(), e);
        }
        // eat trailing slashes, else collections might not be found
        while(path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        // path now is in proper canonical encoded form
        
        if (LOG.isDebugEnabled()) {
            LOG.debug("Out: " + path);
        }

        return path;
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest
     * , javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        // first, adjust the path
        String path = adjustPath(request);

        // second, perform descriptor actions
        final Descriptor descriptor = Descriptor.getDescriptorSingleton();
        if (descriptor != null && !descriptor.requestsFiltered()) {
            // logs the request if specified in the descriptor
            descriptor.doLogRequestInReplayLog(request);

            // map's the path if a mapping is specified in the descriptor
            path = descriptor.mapPath(path);
        }

        // third, authenticate the user
        final Subject user = authenticate(request, response);
        if (user == null) {
            // You now get a challenge if there is no user
            // response.sendError(HttpServletResponse.SC_FORBIDDEN,
            // "Permission denied: unknown user " + "or password");
            return;
        }

        // fourth, process the request
        try(final DBBroker broker = getPool().get(Optional.of(user));
               final Txn transaction = getPool().getTransactionManager().beginTransaction()) {
            srvREST.doGet(broker, transaction, request, response, path);
            transaction.commit();
        } catch (final BadRequestException e) {
            if (response.isCommitted()) {
                throw new ServletException(e.getMessage());
            }
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());

        } catch (final PermissionDeniedException e) {
            // If the current user is the Default User and they do not have permission
            // then send a challenge request to prompt the client for a username/password.
            // Else return a FORBIDDEN Error
            if (user != null && user.equals(getDefaultUser())) {
                getAuthenticator().sendChallenge(request, response);
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            }
        } catch (final NotFoundException e) {
            if (response.isCommitted()) {
                throw new ServletException(e.getMessage());
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());

        } catch (final EXistException e) {
            if (response.isCommitted()) {
                throw new ServletException(e.getMessage(), e);
            }
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (final EOFException ee) {
            getLog().error("GET Connection has been interrupted", ee);
            throw new ServletException("GET Connection has been interrupted", ee);
        } catch (final Throwable e) {
            getLog().error(e.getMessage(), e);
            throw new ServletException("An error occurred: " + e.getMessage(), e);
        }
    }

    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // first, adjust the path
        String path = adjustPath(request);

        // second, perform descriptor actions
        final Descriptor descriptor = Descriptor.getDescriptorSingleton();
        if (descriptor != null && !descriptor.requestsFiltered()) {
            // logs the request if specified in the descriptor
            descriptor.doLogRequestInReplayLog(request);

            // map's the path if a mapping is specified in the descriptor
            path = descriptor.mapPath(path);
        }

        // third, authenticate the user
        final Subject user = authenticate(request, response);
        if (user == null) {
            // You now get a challenge if there is no user
            // response.sendError(HttpServletResponse.SC_FORBIDDEN,
            // "Permission denied: unknown user " + "or password");
            return;
        }

        // fourth, process the request
        try(final DBBroker broker = getPool().get(Optional.of(user));
                final Txn transaction = getPool().getTransactionManager().beginTransaction()) {
            srvREST.doHead(broker, transaction, request, response, path);
            transaction.commit();
        } catch (final BadRequestException e) {
            if (response.isCommitted()) {
                throw new ServletException(e.getMessage(), e);
            }
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());

        } catch (final PermissionDeniedException e) {
            // If the current user is the Default User and they do not have permission
            // then send a challenge request to prompt the client for a username/password.
            // Else return a FORBIDDEN Error
            if (user != null && user.equals(getDefaultUser())) {
                getAuthenticator().sendChallenge(request, response);
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            }
        } catch (final NotFoundException e) {
            if (response.isCommitted()) {
                throw new ServletException(e.getMessage(), e);
            }
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (final EXistException e) {
            if (response.isCommitted()) {
                throw new ServletException(e.getMessage(), e);
            }
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (final Throwable e) {
            getLog().error(e);
            throw new ServletException("An unknown error occurred: " + e.getMessage(), e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * javax.servlet.http.HttpServlet#doDelete(javax.servlet.http.HttpServletRequest
     * , javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // first, adjust the path
        String path = adjustPath(request);

        // second, perform descriptor actions
        final Descriptor descriptor = Descriptor.getDescriptorSingleton();
        if (descriptor != null) {
            // map's the path if a mapping is specified in the descriptor
            path = descriptor.mapPath(path);
        }

        // third, authenticate the user
        final Subject user = authenticate(request, response);
        if (user == null) {
            // You now get a challenge if there is no user
            // response.sendError(HttpServletResponse.SC_FORBIDDEN,
            // "Permission denied: unknown user " + "or password");
            return;
        }

        // fourth, process the request
        try(final DBBroker broker = getPool().get(Optional.of(user));
                final Txn transaction = getPool().getTransactionManager().beginTransaction()) {
            srvREST.doDelete(broker, transaction, path, request, response);
            transaction.commit();
        } catch (final PermissionDeniedException e) {
            // If the current user is the Default User and they do not have permission
            // then send a challenge request to prompt the client for a username/password.
            // Else return a FORBIDDEN Error
            if (user != null && user.equals(getDefaultUser())) {
                getAuthenticator().sendChallenge(request, response);
            } else {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
            }
        } catch (final NotFoundException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (final EXistException e) {
            if (response.isCommitted()) {
                throw new ServletException(e.getMessage(), e);
            }
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (final Throwable e) {
            getLog().error(e);
            throw new ServletException("An unknown error occurred: " + e.getMessage(), e);
        }
    }

    /*
     * (non-Javadoc)
     * 
     * @see
     * javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest
     * , javax.servlet.http.HttpServletResponse)
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
        HttpServletRequest request = null;
        try {
            // For POST request, If we are logging the requests we must wrap
            // HttpServletRequest in HttpServletRequestWrapper
            // otherwise we cannot access the POST parameters from the content body
            // of the request!!! - deliriumsky
            final Descriptor descriptor = Descriptor.getDescriptorSingleton();
            if (descriptor != null) {
                if (descriptor.allowRequestLogging()) {
                    request = new HttpServletRequestWrapper(() -> (String) getPool().getConfiguration().getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY), req, getFormEncoding());
                } else {
                    request = req;
                }
            } else {
                request = req;
            }

            // first, adjust the path
            String path = request.getPathInfo();
            if (path == null) {
                path = "";
            } else {
                path = adjustPath(request);
            }

            // second, perform descriptor actions
            if (descriptor != null && !descriptor.requestsFiltered()) {
                // logs the request if specified in the descriptor
                descriptor.doLogRequestInReplayLog(request);

                // map's the path if a mapping is specified in the descriptor
                path = descriptor.mapPath(path);
            }

            // third, authenticate the user
            final Subject user = authenticate(request, response);
            if (user == null) {
                // You now get a challenge if there is no user
                // response.sendError(HttpServletResponse.SC_FORBIDDEN,
                // "Permission denied: unknown user " + "or password");
                return;
            }

            // fourth, process the request
            try(final DBBroker broker = getPool().get(Optional.of(user));
                    final Txn transaction = getPool().getTransactionManager().beginTransaction()) {
                srvREST.doPost(broker, transaction, request, response, path);
                transaction.commit();
            } catch (final PermissionDeniedException e) {
                // If the current user is the Default User and they do not have permission
                // then send a challenge request to prompt the client for a username/password.
                // Else return a FORBIDDEN Error
                if (user != null && user.equals(getDefaultUser())) {
                    getAuthenticator().sendChallenge(request, response);
                } else {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, e.getMessage());
                }
            } catch (final EXistException e) {
                if (response.isCommitted()) {
                    throw new ServletException(e.getMessage(), e);
                }
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
            } catch (final BadRequestException e) {
                if (response.isCommitted()) {
                    throw new ServletException(e.getMessage(), e);
                }
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
            } catch (final NotFoundException e) {
                if (response.isCommitted()) {
                    throw new ServletException(e.getMessage(), e);
                }
                response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
            } catch (final Throwable e) {
                getLog().error(e);
                throw new ServletException("An unknown error occurred: " + e.getMessage(), e);
            }
        } finally {
            if (request != null && request instanceof HttpServletRequestWrapper) {
                ((HttpServletRequestWrapper)request).close();
            }
        }
    }
}
