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

import org.exist.xquery.*;
import org.exist.xquery.Module;
import org.w3c.dom.Element;
import org.exist.xquery.value.Sequence;
import org.exist.dom.QName;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.ServletException;
import java.io.IOException;
import java.util.ArrayList;

public class ModuleCall extends URLRewrite {
    private static final Logger LOG = LogManager.getLogger(ModuleCall.class);

    private final FunctionCall call;

    public ModuleCall(final Element config, final XQueryContext context, final String uri) throws ServletException {
        super(config, uri);
        String funcName = config.getAttribute("function");
        if (funcName == null || funcName.length() == 0) {
            throw new ServletException("<exist:call> requires an attribute 'function'.");
        }
        int arity = 0;
        final int p = funcName.indexOf('/');
        if (p > -1) {
            final String arityStr = funcName.substring(p + 1);
            try {
                arity = Integer.parseInt(arityStr);
            } catch (final NumberFormatException e) {
                throw new ServletException("<exist:call>: could not parse parameter count in function attribute: " + arityStr);
            }
            funcName = funcName.substring(0, p);
        }
        try {
            final QName fqn = QName.parse(context, funcName);
            final Module module = context.getModule(fqn.getNamespaceURI());
            final UserDefinedFunction func;
            if (module != null) {
                func = ((ExternalModule) module).getFunction(fqn, arity, context);
            } else {
                func = context.resolveFunction(fqn, arity);
            }
            call = new FunctionCall(context, func);
            call.setArguments(new ArrayList<>());
        } catch (final XPathException | QName.IllegalQNameException e) {
            throw new ServletException(e);
        }
    }

    protected ModuleCall(final ModuleCall other) {
        super(other);
        this.call = other.call;
    }

    @Override
    public void doRewrite(final HttpServletRequest request, final HttpServletResponse response) throws ServletException {
        try {
            final ContextItemDeclaration cid = call.getContext().getContextItemDeclartion();
            final Sequence contextSequence;
            if (cid != null) {
                contextSequence = cid.eval(null);
            } else {
                contextSequence = null;
            }

            final Sequence result = call.eval(contextSequence);
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found: " + result.getItemCount());
            }
            request.setAttribute(XQueryURLRewrite.RQ_ATTR_RESULT, result);

        } catch (final XPathException e) {
            throw new ServletException("Called function threw exception: " + e.getMessage(), e);
        }
    }

    @Override
    protected URLRewrite copy() {
        return new ModuleCall(this);
    }
}
