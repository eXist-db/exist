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
package org.exist.xquery.modules.compression;

import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Compresses a sequence of resources and/or collections into a Zip file
 *
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @version 1.0
 */
public class ZipFunction extends AbstractCompressFunction {

    private static final QName ZIP_FUNCTION_NAME = new QName("zip", CompressionModule.NAMESPACE_URI, CompressionModule.PREFIX);
    private static final String ZIP_FUNCTION_DESCRIPTION = "Zips nodes, resources and collections.";

    public static final FunctionSignature[] signatures = {

            new FunctionSignature(
                    ZIP_FUNCTION_NAME,
                    ZIP_FUNCTION_DESCRIPTION,
                    new SequenceType[]{
                            SOURCES_PARAM,
                            COLLECTION_HIERARCHY_PARAM,
                    },
                    new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_MORE)
            ),

            new FunctionSignature(
                    ZIP_FUNCTION_NAME,
                    ZIP_FUNCTION_DESCRIPTION,
                    new SequenceType[]{
                            SOURCES_PARAM,
                            COLLECTION_HIERARCHY_PARAM,
                            STRIP_PREFIX_PARAM
                    },
                    new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_MORE)
            ),

            new FunctionSignature(
                    ZIP_FUNCTION_NAME,
                    ZIP_FUNCTION_DESCRIPTION,
                    new SequenceType[]{
                            SOURCES_PARAM,
                            COLLECTION_HIERARCHY_PARAM,
                            STRIP_PREFIX_PARAM,
                            ENCODING_PARAM
                    },
                    new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_MORE)
            )
    };


    public ZipFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    protected void closeEntry(final Object os) throws IOException {
        ((ZipOutputStream) os).closeEntry();
    }

    @Override
    protected Object newEntry(final String name) {
        return new ZipEntry(name);
    }

    @Override
    protected void putEntry(final Object os, final Object entry) throws IOException {
        ((ZipOutputStream) os).putNextEntry((ZipEntry) entry);
    }

    @Override
    protected OutputStream stream(final UnsynchronizedByteArrayOutputStream baos, final Charset encoding) {
        return new ZipOutputStream(baos, encoding);
    }
}
