/*
Copyright (c) 2012, Adam Retter
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:
    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.
    * Redistributions in binary form must reproduce the above copyright
      notice, this list of conditions and the following disclaimer in the
      documentation and/or other materials provided with the distribution.
    * Neither the name of Adam Retter Consulting nor the
      names of its contributors may be used to endorse or promote products
      derived from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL Adam Retter BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.exist.extensions.exquery.restxq.impl;

import java.io.IOException;
import java.util.Optional;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.EXistException;
import org.exist.extensions.exquery.restxq.impl.adapters.HttpServletRequestAdapter;
import org.exist.extensions.exquery.restxq.impl.adapters.HttpServletResponseAdapter;
import org.exist.http.servlets.AbstractExistHttpServlet;
import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;
import org.exist.util.Configuration;
import org.exquery.http.HttpRequest;
import org.exquery.restxq.RestXqService;
import org.exquery.restxq.RestXqServiceException;
import org.exquery.restxq.RestXqServiceRegistry;

/**
 *
 * @author <a href="mailto:adam.retter@googlemail.com">Adam Retter</a>
 */
public class RestXqServlet extends AbstractExistHttpServlet {

    private final Logger log = LogManager.getLogger(getClass());

    private RestXqServiceRegistry getRegistry() {
        return RestXqServiceRegistryManager.getRegistry(getPool());
    }

    @Override
    protected void service(final HttpServletRequest request, final HttpServletResponse response) throws ServletException, IOException {
        
        //authenticate
        final Subject user = authenticate(request, response);
        if (user == null) {
            // You now get a challenge if there is no user
            // response.sendError(HttpServletResponse.SC_FORBIDDEN,
            // "Permission denied: unknown user or password");
            return;
        }

        try(final DBBroker broker = getPool().get(Optional.of(user))) {
            final Configuration configuration = broker.getConfiguration();

            final HttpRequest requestAdapter = new HttpServletRequestAdapter(
                request,
                    () -> (String)configuration.getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY));

            final RestXqService service = getRegistry().findService(requestAdapter);
            if(service != null) {
                
                if(log.isTraceEnabled()) {
                    log.trace("Received " + requestAdapter.getMethod().name() + " request for \"" + requestAdapter.getPath() + "\" and found Resource Function \"" + service.getResourceFunction().getFunctionSignature() + "\" in  module \"" + service.getResourceFunction().getXQueryLocation() + "\"");
                }
                
                service.service(
                    requestAdapter,
                    new HttpServletResponseAdapter(response),
                    new ResourceFunctionExecutorImpl(getPool(), request.getContextPath() + request.getServletPath(), request.getRequestURI()),
                    new RestXqServiceSerializerImpl(getPool())
                );
            } else {
                if(log.isTraceEnabled()) {
                    log.trace("Received " + requestAdapter.getMethod().name() + " request for \"" + requestAdapter.getPath() + "\" but no suitable Resource Function found!");
                }
                
                super.service(request, response);
            }
        } catch(final EXistException e) {
            getLog().error(e.getMessage(), e);
            throw new ServletException(e.getMessage(), e);
        } catch(final RestXqServiceException e) {
            if (e.getCause() instanceof PermissionDeniedException) {
                getAuthenticator().sendChallenge(request, response);
            } else {
                //TODO should probably be caught higher up and returned as a HTTP Response? maybe need two different types of exception to differentiate critical vs processing exception
                getLog().error(e.getMessage(), e);
                throw new ServletException(e.getMessage(), e);
            }
        }
    }

    @Override
    public Logger getLog() {
        return log;
    }
}