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
package org.exist.http.urlrewrite;

import org.w3c.dom.Element;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.XPathException;
import org.exist.xquery.Module;
import org.exist.xquery.UserDefinedFunction;
import org.exist.xquery.ExternalModule;
import org.exist.xquery.FunctionCall;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Type;
import org.exist.dom.QName;
import org.exist.util.serializer.SAXSerializer;
import org.exist.util.serializer.SerializerPool;
import org.exist.storage.serializers.Serializer;
import org.xml.sax.SAXException;
import org.apache.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Properties;

public class ModuleCall extends URLRewrite {

    private final static Logger LOG = Logger.getLogger(ModuleCall.class);
    
    private FunctionCall call;

    public ModuleCall(Element config, XQueryContext context, String uri) throws ServletException {
        super(config, uri);
        String funcName = config.getAttribute("function");
        if (funcName == null || funcName.length() == 0)
            throw new ServletException("<exist:call> requires an attribute 'function'.");
        int arity = 0;
        int p = funcName.indexOf('/');
        if (p > -1) {
            String arityStr = funcName.substring(p + 1);
            try {
                arity = Integer.parseInt(arityStr);
            } catch (NumberFormatException e) {
                throw new ServletException("<exist:call>: could not parse parameter count in function attribute: " + arityStr);
            }
            funcName = funcName.substring(0, p);
        }
        try {
            QName fqn = QName.parse(context, funcName);
            Module module = context.getModule(fqn.getNamespaceURI());
            UserDefinedFunction func = null;
            if (module != null)
                func = ((ExternalModule)module).getFunction(fqn, arity);
            else
                func = context.resolveFunction(fqn, arity);
            call = new FunctionCall(context, func);
            call.setArguments(new ArrayList());
        } catch (XPathException e) {
            e.printStackTrace();
        }
    }

    public void doRewrite(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            Sequence result = call.eval(null);
            LOG.debug("Found: " + result.getItemCount());
            request.setAttribute(XQueryURLRewrite.RQ_ATTR_RESULT, result);
            
        } catch (XPathException e) {
            throw new ServletException("Called function threw exception: " + e.getMessage(), e);
        }
    }
}
