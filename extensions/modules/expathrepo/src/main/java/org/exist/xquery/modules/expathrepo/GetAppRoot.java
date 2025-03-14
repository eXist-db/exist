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
package org.exist.xquery.modules.expathrepo;

import org.exist.dom.QName;
import org.exist.repo.Deployment;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.*;
import org.exist.xquery.value.*;

import java.util.Objects;

public class GetAppRoot extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("get-root", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
            "Returns the root collection into which applications are installed. Corresponds to the " +
            "collection path defined in conf.xml (<repository root=\"...\"/>) or /db if not configured.",
            null,
            new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE,
                "The application root collection"));

    public GetAppRoot(XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException {
        String configured = (String) context.getBroker().getConfiguration().getProperty(Deployment.PROPERTY_APP_ROOT);
        return new StringValue(this, Objects.requireNonNullElse(configured, XmldbURI.ROOT_COLLECTION));
    }
}
