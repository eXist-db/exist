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
package org.exist.xquery.modules.file.expath;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.*;

/**
 * EXPath File Module 4.0 - Path functions.
 * <p>
 * Implements: file:name, file:parent, file:path-to-native, file:path-to-uri, file:resolve-path
 */
public class FilePaths extends BasicFunction {

    private static final FunctionParameterSequenceType PATH_PARAM =
            new FunctionParameterSequenceType("path", Type.STRING, Cardinality.EXACTLY_ONE, "The file path.");

    public static final FunctionSignature[] signatures = {
            // file:name($path as xs:string) as xs:string
            new FunctionSignature(
                    new QName("name", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Returns the name of a file or directory.",
                    new SequenceType[]{PATH_PARAM},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the file or directory name.")
            ),
            // file:parent($path as xs:string) as xs:string?
            new FunctionSignature(
                    new QName("parent", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Returns the parent directory of a path.",
                    new SequenceType[]{PATH_PARAM},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the parent directory path, or empty for root.")
            ),
            // file:path-to-native($path as xs:string) as xs:string
            new FunctionSignature(
                    new QName("path-to-native", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Returns the native, canonical path.",
                    new SequenceType[]{PATH_PARAM},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the native path.")
            ),
            // file:path-to-uri($path as xs:string) as xs:anyURI
            new FunctionSignature(
                    new QName("path-to-uri", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Returns the path as a file:// URI.",
                    new SequenceType[]{PATH_PARAM},
                    new FunctionReturnSequenceType(Type.ANY_URI, Cardinality.EXACTLY_ONE, "the file:// URI.")
            ),
            // file:resolve-path($path as xs:string) as xs:string
            new FunctionSignature(
                    new QName("resolve-path", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Resolves a relative path against the current working directory.",
                    new SequenceType[]{PATH_PARAM},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the resolved absolute path.")
            ),
            // file:resolve-path($path as xs:string, $base as xs:string) as xs:string
            new FunctionSignature(
                    new QName("resolve-path", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Resolves a relative path against a base directory.",
                    new SequenceType[]{
                            PATH_PARAM,
                            new FunctionParameterSequenceType("base", Type.STRING, Cardinality.ZERO_OR_ONE, "The base directory to resolve against.")
                    },
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the resolved absolute path.")
            )
    };

    public FilePaths(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        ExpathFileModuleHelper.checkDbaRole(context, this);

        final String pathStr = args[0].getStringValue();
        final Path path = ExpathFileModuleHelper.getPath(pathStr, this, context);

        if (isCalledAs("name")) {
            final Path fileName = path.getFileName();
            return new StringValue(this, fileName != null ? fileName.toString() : "");
        } else if (isCalledAs("parent")) {
            final Path absPath = path.toAbsolutePath().normalize();
            final Path parent = absPath.getParent();
            if (parent == null) {
                return Sequence.EMPTY_SEQUENCE;
            }
            return new StringValue(this, parent.toString() + File.separator);
        } else if (isCalledAs("path-to-native")) {
            if (!Files.exists(path)) {
                throw new XPathException(this, ExpathFileErrorCode.NOT_FOUND,
                        "Path does not exist: " + path.toAbsolutePath());
            }
            try {
                return new StringValue(this, path.toRealPath().toString());
            } catch (final IOException e) {
                throw new XPathException(this, ExpathFileErrorCode.IO_ERROR, e.getMessage());
            }
        } else if (isCalledAs("path-to-uri")) {
            final Path abs = path.toAbsolutePath().normalize();
            return new AnyURIValue(this, abs.toUri().toString());
        } else if (isCalledAs("resolve-path")) {
            if (args.length > 1 && !args[1].isEmpty()) {
                final Path base = ExpathFileModuleHelper.getPath(args[1].getStringValue(), this, context);
                return new StringValue(this, base.resolve(path).toAbsolutePath().normalize().toString());
            }
            return new StringValue(this, path.toAbsolutePath().normalize().toString());
        }

        throw new XPathException(this, "Unknown function: " + getSignature().getName().getLocalPart());
    }
}
