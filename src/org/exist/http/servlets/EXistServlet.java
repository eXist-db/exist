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
import org.exist.http.BadRequestException;
import org.exist.http.Descriptor;
import org.exist.http.NotFoundException;
import org.exist.http.RESTServer;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.storage.txn.Txn;
import org.exist.validation.XmlLibraryChecker;
import org.exist.xquery.Constants;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.EOFException;
import java.io.IOException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;

/**
 * Implements the REST-style interface if eXist is running within a Servlet
 * engine. The real work is done by class {@link org.exist.http.RESTServer}.
 */
public class EXistServlet extends AbstractExistHttpServlet {

    private static final long serialVersionUID = -3563999345725645647L;
    private final static Logger LOG = LogManager.getLogger(EXistServlet.class);
    private RESTServer srvREST;

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

        // Instantiate REST Server
        srvREST = new RESTServer(getPool(), getFormEncoding(), getContainerEncoding(), useDynamicContentType.equalsIgnoreCase("yes")
                || useDynamicContentType.equalsIgnoreCase("true"), isInternalOnly());

        // XML lib checks....
        XmlLibraryChecker.check();
    }

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
            final Optional<Throwable> maybeError = srvREST.doPut(broker, transaction, path, request, response);
            handleError(maybeError, user, request, response);
            transaction.commit();
        } catch (final EXistException e) {
            handleError(Optional.of(e), user, request, response);
        } catch (final Throwable e) {
            getLog().error(e.getMessage(), e);
            throw new ServletException("An error occurred: " + e.getMessage(), e);
        }
    }

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

            final Optional<Throwable> maybeError = srvREST.doGet(broker, transaction, request, response, path);
            handleError(maybeError, user, request, response);
            
            transaction.commit();
        } catch (final EXistException e) {
            handleError(Optional.of(e), user, request, response);
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
            final Optional<Throwable> maybeError = srvREST.doHead(broker, transaction, request, response, path);
            handleError(maybeError, user, request, response);
            transaction.commit();
        } catch(final EXistException e) {
            handleError(Optional.of(e), user, request, response);
        } catch (final Throwable e) {
            getLog().error(e.getMessage(), e);
            throw new ServletException("An error occurred: " + e.getMessage(), e);
        }
    }

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
            final Optional<Throwable> maybeError = srvREST.doDelete(broker, transaction, path, request, response);
            handleError(maybeError, user, request, response);
            transaction.commit();
        } catch(final EXistException e) {
            handleError(Optional.of(e), user, request, response);
        } catch (final Throwable e) {
            getLog().error(e.getMessage(), e);
            throw new ServletException("An error occurred: " + e.getMessage(), e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse response) throws ServletException, IOException {
        HttpServletRequest request = null;

        // For POST request, If we are logging the requests we must wrap
        // HttpServletRequest in HttpServletRequestWrapper
        // otherwise we cannot access the POST parameters from the content body
        // of the request!!! - deliriumsky
        final Descriptor descriptor = Descriptor.getDescriptorSingleton();
        if (descriptor != null) {
            if (descriptor.allowRequestLogging()) {
                request = new HttpServletRequestWrapper(req, getFormEncoding());
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


            final Optional<Throwable> maybeError = srvREST.doPost(broker, transaction, request, response, path);
            handleError(maybeError, user, request, response);

            transaction.commit();
        } catch (final EXistException e) {
            handleError(Optional.of(e), user, request, response);
        } catch (final Throwable e) {
            getLog().error(e.getMessage(), e);
            throw new ServletException("An error occurred: " + e.getMessage(), e);
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
            final URI u = URI.create("file://" + path.replaceAll("%2F", "%252F"));
            // URI four-argument constructor recreates all the necessary ones
            final URI v = new URI("http", "host", u.getPath(), null).normalize();
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

    private void handleError(final Optional<Throwable> maybeError, final Subject user, final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        if(maybeError.isPresent()) {
            final Throwable t = maybeError.get();

            if(t instanceof PermissionDeniedException) {
                // If the current user is the Default User and they do not have permission
                // then send a challenge request to prompt the client for a username/password.
                // Else return a FORBIDDEN Error
                if (user != null && user.equals(getDefaultUser())) {
                    getAuthenticator().sendChallenge(request, response);
                } else {
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, t.getMessage());
                }
            
            } else if(t instanceof NotFoundException) {
                if (response.isCommitted()) {
                    throw new ServletException(t.getMessage(), t);
                } else {
                    response.sendError(HttpServletResponse.SC_NOT_FOUND, t.getMessage());
                }
                
            } else if(t instanceof BadRequestException) {
                if (response.isCommitted()) {
                    throw new ServletException(t.getMessage(), t);
                } else {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, t.getMessage());
                }

            } else if(t instanceof EXistException) {
                if (response.isCommitted()) {
                    throw new ServletException(t.getMessage(), t);
                } else {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, t.getMessage());
                }

            } else if(t instanceof EOFException) {
                getLog().error("Transfer has been interrupted", t);
                throw new ServletException("Transfer has been interrupted", t);
                
            } else if(t instanceof IOException) {
                throw (IOException)t;

            } else {
                getLog().error(t);
                throw new ServletException("An unknown error occurred: " + t.getMessage(), t);
            }
        }
    }
}
