/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-07 The eXist Project
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
 *
 * $Id$
 */
package org.exist.xmlrpc;

import org.apache.xmlrpc.webserver.XmlRpcServlet;
import org.apache.xmlrpc.server.XmlRpcHandlerMapping;
import org.apache.xmlrpc.server.AbstractReflectiveHandlerMapping;
import org.apache.xmlrpc.server.RequestProcessorFactoryFactory;
import org.apache.xmlrpc.server.XmlRpcNoSuchHandlerException;
import org.apache.xmlrpc.XmlRpcException;
import org.apache.xmlrpc.XmlRpcHandler;
import org.exist.EXistException;

public class RpcServlet extends XmlRpcServlet {

    protected XmlRpcHandlerMapping newXmlRpcHandlerMapping() throws XmlRpcException {
        DefaultHandlerMapping mapping = new DefaultHandlerMapping();
        mapping.setVoidMethodEnabled(true);
        mapping.setRequestProcessorFactoryFactory(new XmldbRequestProcessorFactoryFactory());
        mapping.loadDefault(RpcConnection.class);
        return mapping;
    }

    private static class XmldbRequestProcessorFactoryFactory extends RequestProcessorFactoryFactory.RequestSpecificProcessorFactoryFactory {

        RequestProcessorFactory instance = null;

        public RequestProcessorFactory getRequestProcessorFactory(Class pClass) throws XmlRpcException {
            try {
                if (instance == null)
                    instance = new XmldbRequestProcessorFactory("exist");
                return instance;
            } catch (EXistException e) {
                throw new XmlRpcException("Failed to initialize XMLRPC interface: " + e.getMessage(), e);
            }
        }
    }

    private static class DefaultHandlerMapping extends AbstractReflectiveHandlerMapping {

        private DefaultHandlerMapping() throws XmlRpcException {
        }

        public void loadDefault(Class clazz) throws XmlRpcException {
            registerPublicMethods("Default", clazz);
        }

        public XmlRpcHandler getHandler(String pHandlerName) throws XmlRpcNoSuchHandlerException, XmlRpcException {
            if (pHandlerName.indexOf('.') < 0)
                pHandlerName = "Default." + pHandlerName;
            return super.getHandler(pHandlerName);
        }
    }
}
