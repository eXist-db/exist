/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 The eXist Project
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
package org.exist.xquery.modules.compression;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import org.exist.dom.QName;
import org.exist.util.io.FastByteArrayOutputStream;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

/**
 * Compresses a sequence of resources and/or collections into a Tar file
 * 
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @version 1.0
 */
public class TarFunction extends AbstractCompressFunction
{
    private final static QName TAR_FUNCTION_NAME = new QName("tar", CompressionModule.NAMESPACE_URI, CompressionModule.PREFIX);
    private final static String TAR_FUNCTION_DESCRIPTION = "Tars nodes, resources and collections.";


    public final static FunctionSignature signatures[] = {

        new FunctionSignature(
            TAR_FUNCTION_NAME,
            TAR_FUNCTION_DESCRIPTION,
            new SequenceType[] {
               SOURCES_PARAM,
               COLLECTION_HIERARCHY_PARAM,
            },
            new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_MORE)
        ),

        new FunctionSignature(
            TAR_FUNCTION_NAME,
            TAR_FUNCTION_DESCRIPTION,
            new SequenceType[] {
                SOURCES_PARAM,
                COLLECTION_HIERARCHY_PARAM,
                STRIP_PREFIX_PARAM
            },
            new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_MORE)),

        new FunctionSignature(
            TAR_FUNCTION_NAME,
            TAR_FUNCTION_DESCRIPTION,
            new SequenceType[] {
                SOURCES_PARAM,
                COLLECTION_HIERARCHY_PARAM,
                STRIP_PREFIX_PARAM,
				ENCODING_PARAM
            },
            new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_MORE))
    };

    public TarFunction(XQueryContext context, FunctionSignature signature)
    {
        super(context, signature);
    }

    @Override
    protected void closeEntry(Object os) throws IOException
    {
		((TarArchiveOutputStream) os).closeArchiveEntry();
    }

    @Override
    protected Object newEntry(String name)
    {
            return new TarArchiveEntry(name);
    }

    @Override
    protected void putEntry(Object os, Object entry) throws IOException
    {
		((TarArchiveOutputStream) os).putArchiveEntry((TarArchiveEntry) entry);
    }

    @Override
    protected OutputStream stream(final FastByteArrayOutputStream baos, final Charset encoding)
    {
        return new TarArchiveOutputStream(baos, encoding.name());
    }	
}