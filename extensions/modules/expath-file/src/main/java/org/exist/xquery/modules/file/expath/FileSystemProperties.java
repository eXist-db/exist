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
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.exist.dom.QName;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 * EXPath File Module 4.0 - System property functions.
 * <p>
 * Implements: file:dir-separator, file:line-separator, file:path-separator,
 * file:temp-dir, file:base-dir, file:current-dir
 */
public class FileSystemProperties extends BasicFunction {

    public static final FunctionSignature[] signatures = {
            // file:dir-separator() as xs:string
            new FunctionSignature(
                    new QName("dir-separator", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Returns the directory separator used by the operating system.",
                    new SequenceType[]{},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the directory separator.")
            ),
            // file:line-separator() as xs:string
            new FunctionSignature(
                    new QName("line-separator", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Returns the line separator used by the operating system.",
                    new SequenceType[]{},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the line separator.")
            ),
            // file:path-separator() as xs:string
            new FunctionSignature(
                    new QName("path-separator", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Returns the path separator used by the operating system.",
                    new SequenceType[]{},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the path separator.")
            ),
            // file:temp-dir() as xs:string
            new FunctionSignature(
                    new QName("temp-dir", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Returns the path of the temporary directory.",
                    new SequenceType[]{},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the temporary directory path.")
            ),
            // file:base-dir() as xs:string?
            new FunctionSignature(
                    new QName("base-dir", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Returns the base directory of the current query, or empty if not available.",
                    new SequenceType[]{},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.ZERO_OR_ONE, "the base directory path.")
            ),
            // file:current-dir() as xs:string
            new FunctionSignature(
                    new QName("current-dir", ExpathFileModule.NAMESPACE_URI, ExpathFileModule.PREFIX),
                    "Returns the current working directory.",
                    new SequenceType[]{},
                    new FunctionReturnSequenceType(Type.STRING, Cardinality.EXACTLY_ONE, "the current working directory.")
            )
    };

    public FileSystemProperties(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {
        ExpathFileModuleHelper.checkDbaRole(context, this);

        if (isCalledAs("dir-separator")) {
            return new StringValue(this, File.separator);
        } else if (isCalledAs("line-separator")) {
            return new StringValue(this, System.lineSeparator());
        } else if (isCalledAs("path-separator")) {
            return new StringValue(this, File.pathSeparator);
        } else if (isCalledAs("temp-dir")) {
            return new StringValue(this, System.getProperty("java.io.tmpdir") + File.separator);
        } else if (isCalledAs("base-dir")) {
            try {
                final String baseURI = context.getBaseURI().getStringValue();
                if (baseURI != null && !baseURI.isEmpty() && baseURI.startsWith("file:")) {
                    final Path basePath = Paths.get(new URI(baseURI));
                    final Path parent = basePath.getParent();
                    if (parent != null) {
                        return new StringValue(this, parent.toString() + File.separator);
                    }
                }
            } catch (final Exception e) {
                // Fall through to return empty
            }
            return Sequence.EMPTY_SEQUENCE;
        } else if (isCalledAs("current-dir")) {
            // If a file: base URI is set (e.g., sandpit), use its directory as the working directory
            try {
                final String baseURI = context.getBaseURI().getStringValue();
                if (baseURI != null && !baseURI.isEmpty() && baseURI.startsWith("file:")) {
                    final Path basePath = Paths.get(new URI(baseURI));
                    final Path dir = java.nio.file.Files.isDirectory(basePath) ? basePath : basePath.getParent();
                    if (dir != null) {
                        return new StringValue(this, dir.toString() + File.separator);
                    }
                }
            } catch (final Exception ignored) {
                // Fall through to JVM CWD
            }
            return new StringValue(this, System.getProperty("user.dir") + File.separator);
        }

        throw new XPathException(this, "Unknown function: " + getSignature().getName().getLocalPart());
    }
}
