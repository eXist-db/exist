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

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

import javax.xml.transform.stream.StreamSource;

import org.exist.dom.QName;
import org.exist.repo.ExistRepository;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.Packages;
import org.expath.pkg.repo.Storage;

/**
 * repo:resource-available($pkgName, $resource) as xs:boolean
 *
 * Returns true if the specified resource exists in the named EXPath package,
 * false otherwise. Companion to repo:get-resource(), analogous to
 * fn:doc-available() for fn:doc().
 */
public class ResourceAvailable extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("resource-available", ExpathPackageModule.NAMESPACE_URI, ExpathPackageModule.PREFIX),
            "Returns true if the specified resource exists in the named EXPath package, false otherwise.",
            new SequenceType[] {
                new FunctionParameterSequenceType("pkgName", Type.STRING, Cardinality.EXACTLY_ONE, "package name"),
                new FunctionParameterSequenceType("resource", Type.STRING, Cardinality.EXACTLY_ONE, "resource path")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                "true if the resource exists in the package, false otherwise"));

    public ResourceAvailable(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        final String pkgName = args[0].getStringValue();
        final String path = args[1].getStringValue();

        final Optional<ExistRepository> repo = context.getRepository();
        if (repo.isEmpty()) {
            return BooleanValue.FALSE;
        }

        try {
            for (final Packages pp : repo.get().getParentRepo().listPackages()) {
                final Package pkg = pp.latest();
                if (pkg.getName().equals(pkgName)) {
                    try {
                        // resolveResource opens a stream — close it immediately
                        final StreamSource source = (StreamSource) pkg.getResolver().resolveResource(path);
                        final InputStream is = source.getInputStream();
                        if (is != null) {
                            is.close();
                        }
                        return BooleanValue.TRUE;
                    } catch (final Storage.NotExistException | IOException e) {
                        return BooleanValue.FALSE;
                    }
                }
            }
        } catch (final PackageException e) {
            throw new XPathException(this, "Caught package error while checking resource availability", e);
        }
        // Package not found
        return BooleanValue.FALSE;
    }
}
