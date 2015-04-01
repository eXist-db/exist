/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2015 The eXist Project
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
 */
package org.exist.xmlrpc;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcHandler;
import org.apache.xmlrpc.server.AbstractReflectiveHandlerMapping;
import org.apache.xmlrpc.server.RequestProcessorFactoryFactory;
import org.apache.xmlrpc.server.XmlRpcHandlerMapping;
import org.apache.xmlrpc.webserver.XmlRpcServlet;
import org.exist.EXistException;
import org.exist.http.Descriptor;
import org.exist.http.servlets.HttpServletRequestWrapper;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class RpcServlet extends XmlRpcServlet {

	private static final long serialVersionUID = -1003413291835771186L;
    private static final Logger LOG = LogManager.getLogger(RpcServlet.class);
    private static boolean useDefaultUser = true;

    public static boolean isUseDefaultUser() {
        return useDefaultUser;
    }

    public static void setUseDefaultUser(final boolean useDefaultUser) {
        RpcServlet.useDefaultUser = useDefaultUser;
    }

    @Override
    public void doPost(HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
        // Request logger

        final Descriptor descriptor = Descriptor.getDescriptorSingleton();
        if (descriptor.allowRequestLogging() && !descriptor.requestsFiltered()) {
            // Wrap HttpServletRequest, because both request Logger and xmlrpc
            // need the request InputStream, which is consumed when read.
            request =
                    new HttpServletRequestWrapper(request, /*formEncoding*/ "utf-8");
            descriptor.doLogRequestInReplayLog(request);
        }

        try {
            super.doPost(request, response);
        } catch (final Throwable e){
            LOG.error("Problem during XmlRpc execution", e);
            final String exceptionMessage;
            if (e instanceof XmlRpcException) {
                final Throwable linkedException = ((XmlRpcException)e).linkedException;
                LOG.error(linkedException.getMessage(), linkedException);
                exceptionMessage = "An error occurred: " + e.getMessage() + ": " + linkedException.getMessage();
            } else {
                exceptionMessage = "An unknown error occurred: " + e.getMessage();
            }
            throw new ServletException(exceptionMessage, e);
        }
    }

    protected XmlRpcHandlerMapping newXmlRpcHandlerMapping() throws XmlRpcException {
        final DefaultHandlerMapping mapping = new DefaultHandlerMapping();
        mapping.setVoidMethodEnabled(true);
        mapping.setRequestProcessorFactoryFactory(new XmldbRequestProcessorFactoryFactory());
        mapping.loadDefault(RpcConnection.class);
        return mapping;
    }

    private static class XmldbRequestProcessorFactoryFactory extends RequestProcessorFactoryFactory.RequestSpecificProcessorFactoryFactory {

        RequestProcessorFactory instance = null;

        @Override
        public RequestProcessorFactory getRequestProcessorFactory(final Class pClass) throws XmlRpcException {
            try {
                if (instance == null) {
                    instance = new XmldbRequestProcessorFactory("exist", useDefaultUser);
                }
                return instance;
            } catch (final EXistException e) {
                throw new XmlRpcException("Failed to initialize XMLRPC interface: " + e.getMessage(), e);
            }
        }
    }

    private static class DefaultHandlerMapping extends AbstractReflectiveHandlerMapping {

        private DefaultHandlerMapping() throws XmlRpcException {
        }

        public void loadDefault(final Class<?> clazz) throws XmlRpcException {
            registerPublicMethods("Default", clazz);
        }

        @Override
        public XmlRpcHandler getHandler(String pHandlerName) throws XmlRpcException {
            if (pHandlerName.indexOf('.') < 0) {
                pHandlerName = "Default." + pHandlerName;
            }
            return super.getHandler(pHandlerName);
        }
    }
}
