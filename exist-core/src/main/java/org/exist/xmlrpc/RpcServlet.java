/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2018 The eXist Project
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

import com.evolvedbinary.j8fu.lazy.AtomicLazyValE;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcHandler;
import org.apache.xmlrpc.server.AbstractReflectiveHandlerMapping;
import org.apache.xmlrpc.server.RequestProcessorFactoryFactory;
import org.apache.xmlrpc.server.XmlRpcHandlerMapping;
import org.apache.xmlrpc.webserver.XmlRpcServlet;
import org.apache.xmlrpc.webserver.XmlRpcServletServer;
import org.exist.EXistException;
import org.exist.http.Descriptor;
import org.exist.http.servlets.HttpServletRequestWrapper;
import org.exist.storage.BrokerPool;
import org.exist.util.Configuration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Predicate;

import static com.evolvedbinary.j8fu.Either.*;
import static java.nio.charset.StandardCharsets.ISO_8859_1;

public class RpcServlet extends XmlRpcServlet {

	private static final long serialVersionUID = -1003413291835771186L;
    private static final Logger LOG = LogManager.getLogger(RpcServlet.class);
    private static final boolean DEFAULT_USE_DEFAULT_USER = true;

    private boolean useDefaultUser = DEFAULT_USE_DEFAULT_USER;
    private Charset charset = null;

    @Override
    public void init(final ServletConfig pConfig) throws ServletException {
        final String useDefaultUser = pConfig.getInitParameter("useDefaultUser");
        if(useDefaultUser != null) {
            this.useDefaultUser = Boolean.parseBoolean(useDefaultUser);
        } else {
            this.useDefaultUser = DEFAULT_USE_DEFAULT_USER;
        }

        final String charset = pConfig.getInitParameter("charset");
        if (charset != null) {
            this.charset = Charset.forName(charset);
        }

        super.init(new FilteredServletConfig(pConfig, paramName -> (!"useDefaultUser".equals(paramName)) && (!"charset".equals(paramName))));
    }

    @Override
    public void doPost(HttpServletRequest request, final HttpServletResponse response) throws IOException, ServletException {
        try {
            // Request logger

            final Descriptor descriptor = Descriptor.getDescriptorSingleton();
            if (descriptor.allowRequestLogging() && !descriptor.requestsFiltered()) {
                // Wrap HttpServletRequest, because both request Logger and xmlrpc
                // need the request InputStream, which is consumed when read.
                final String cacheClass = (String) BrokerPool.getInstance().getConfiguration().getProperty(Configuration.BINARY_CACHE_CLASS_PROPERTY);
                request =
                        new HttpServletRequestWrapper(() -> cacheClass, request, /*formEncoding*/ charset != null ? charset.displayName() : ISO_8859_1.displayName());
                descriptor.doLogRequestInReplayLog(request);
            }

            try {
                if (charset != null) {
                    response.setCharacterEncoding(charset.displayName());
                }
                super.doPost(request, response);
            } catch (final Throwable e) {
                LOG.error("Problem during XmlRpc execution", e);
                final String exceptionMessage;
                if (e instanceof XmlRpcException) {
                    final Throwable linkedException = ((XmlRpcException) e).linkedException;
                    LOG.error(linkedException.getMessage(), linkedException);
                    exceptionMessage = "An error occurred: " + e.getMessage() + ": " + linkedException.getMessage();
                } else {
                    exceptionMessage = "An unknown error occurred: " + e.getMessage();
                }
                throw new ServletException(exceptionMessage, e);
            }
        } catch (final EXistException e) {
            throw new ServletException(e);
        } finally {
            if (request != null && request instanceof HttpServletRequestWrapper) {
                ((HttpServletRequestWrapper)request).close();
            }
        }
    }

    @Override
    protected XmlRpcServletServer newXmlRpcServer(final ServletConfig pConfig) throws XmlRpcException {
        final XmlRpcServletServer server = super.newXmlRpcServer(pConfig);
        server.setTypeFactory(new ExistRpcTypeFactory(server));
        return server;
    }

    @Override
    protected XmlRpcHandlerMapping newXmlRpcHandlerMapping() throws XmlRpcException {
        final DefaultHandlerMapping mapping = new DefaultHandlerMapping();
        mapping.setVoidMethodEnabled(true);
        mapping.setRequestProcessorFactoryFactory(new XmldbRequestProcessorFactoryFactory(useDefaultUser));
        mapping.loadDefault(RpcConnection.class);
        return mapping;
    }

    private static class XmldbRequestProcessorFactoryFactory extends RequestProcessorFactoryFactory.RequestSpecificProcessorFactoryFactory {
        private final AtomicLazyValE<RequestProcessorFactory, XmlRpcException> instance;

        public XmldbRequestProcessorFactoryFactory(final boolean useDefaultUser) {
            instance = new AtomicLazyValE<>(() -> {
                try {
                    return Right(new XmldbRequestProcessorFactory("exist", useDefaultUser));
                } catch (final EXistException e) {
                    return Left(new XmlRpcException("Failed to initialize XMLRPC interface: " + e.getMessage(), e));
                }
            });
        }

        @Override
        public RequestProcessorFactory getRequestProcessorFactory(final Class pClass) throws XmlRpcException {
            return instance.get();
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

    /**
     * Filters parameters from an existing {@link ServletConfig}.
     */
    private static class FilteredServletConfig implements ServletConfig {
        private final ServletConfig config;
        private final Predicate<String> parameterPredicate;

        /**
         * @param config a ServletConfig
         * @param parameterPredicate a predicate which includes parameters from {@code config} in this config.
         */
        private FilteredServletConfig(final ServletConfig config, final Predicate<String> parameterPredicate) {
            this.config = config;
            this.parameterPredicate = parameterPredicate;
        }

        @Override
        public String getServletName() {
            return config.getServletName();
        }

        @Override
        public ServletContext getServletContext() {
            return config.getServletContext();
        }

        @Override
        public String getInitParameter(final String s) {
            if(parameterPredicate.test(s)) {
                return config.getInitParameter(s);
            }
            return null;
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            final Enumeration<String> names = config.getInitParameterNames();
            final List<String> filteredNames = new ArrayList<>();
            while(names.hasMoreElements()) {
                final String name = names.nextElement();
                if(parameterPredicate.test(name)) {
                    filteredNames.add(name);
                }
            }
            return Collections.enumeration(filteredNames);
        }
    }
}
