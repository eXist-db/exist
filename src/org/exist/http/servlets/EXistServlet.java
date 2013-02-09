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

import org.apache.log4j.Logger;
import org.exist.EXistException;
import org.exist.collections.Collection;
import org.exist.http.BadRequestException;
import org.exist.http.Descriptor;
import org.exist.http.NotFoundException;
import org.exist.http.RESTServer;
import org.exist.http.SOAPServer;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.validation.XmlLibraryChecker;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.Constants;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.EOFException;
import java.io.IOException;

/**
 * Implements the REST-style interface if eXist is running within a Servlet
 * engine. The real work is done by class {@link org.exist.http.RESTServer}.
 *
 * @author wolf
 */
public class EXistServlet extends AbstractExistHttpServlet {

    private static final long serialVersionUID = -3563999345725645647L;
    private final static Logger LOG = Logger.getLogger(EXistServlet.class);
    private RESTServer srvREST;
    private SOAPServer srvSOAP;

    @Override
    public Logger getLog() {
        return LOG;
    }

    /*
     * (non-Javadoc)
     * 
     * @see javax.servlet.GenericServlet#init(javax.servlet.ServletConfig)
     */
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

        // Instantiate SOAP Server
        srvSOAP = new SOAPServer(getFormEncoding(), getContainerEncoding());

        // XML lib checks....
        XmlLibraryChecker.check();
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

        DBBroker broker = null;
        try {
            final XmldbURI dbpath = XmldbURI.create(path);
            broker = getPool().get(user);
            final Collection collection = broker.getCollection(dbpath);
            if (collection != null) {
                response.sendError(400, "A PUT request is not allowed against a plain collection path.");
                return;
            }
            srvREST.doPut(broker, dbpath, request, response);

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
        } finally {
            if (broker != null) {
                getPool().release(broker);
            }
        }
    }

    /**
     * @param request
     */
    private String adjustPath(HttpServletRequest request) {
        String path = request.getPathInfo();

        if (path == null) {
            path = "";
        }

        final int p = path.lastIndexOf(';');
        if (p != Constants.STRING_NOT_FOUND) {
            path = path.substring(0, p);
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
        DBBroker broker = null;
        try {
            broker = getPool().get(user);

            // Route the request
            if (path.indexOf(SOAPServer.WEBSERVICE_MODULE_EXTENSION) > -1) {
                // SOAP Server
                srvSOAP.doGet(broker, request, response, path);
            } else {
                // REST Server
                srvREST.doGet(broker, request, response, path);
            }
            
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
        } finally {
            getPool().release(broker);
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
        DBBroker broker = null;
        try {
            broker = getPool().get(user);
            srvREST.doHead(broker, request, response, path);
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
        } finally {
            getPool().release(broker);
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
        DBBroker broker = null;
        try {
            broker = getPool().get(user);
            srvREST.doDelete(broker, path, request, response);
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

        } finally {
            getPool().release(broker);
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
        DBBroker broker = null;
        try {
            broker = getPool().get(user);

            // Route the request
            if (path.indexOf(SOAPServer.WEBSERVICE_MODULE_EXTENSION) > -1) {
                // SOAP Server
                srvSOAP.doPost(broker, request, response, path);
            } else {
                // REST Server
                srvREST.doPost(broker, request, response, path);
            }
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
        } finally {
            getPool().release(broker);
        }
    }
}