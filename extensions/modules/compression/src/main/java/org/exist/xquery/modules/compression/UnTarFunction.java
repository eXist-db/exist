/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-2018 The eXist Project
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
 */
package org.exist.xquery.modules.compression;

import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.xmldb.api.base.XMLDBException;

import static org.exist.xquery.FunctionDSL.*;
import static org.exist.xquery.modules.compression.CompressionModule.functionSignatures;

/**
 * Extracts files and folders from a Tar file
 * 
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @version 1.0
 */
public class UnTarFunction extends AbstractExtractFunction {

    private static final FunctionParameterSequenceType FS_PARAM_TAR_DATA = param("tar-data", Type.BASE64_BINARY, "The tar file data");
    private static final FunctionParameterSequenceType FS_PARAM_ENTRY_FILTER = param("entry-filter", Type.FUNCTION_REFERENCE,
            "A user defined function for filtering resources from the tar file. The function takes 2 parameters e.g. "
            + "user:untar-entry-filter($path as xs:string, $data-type as xs:string) as xs:boolean. "
            + "$data-type may be 'resource' or 'folder'. If the return type is true() it indicates the entry "
            + "should be processed and passed to the entry-data function, else the resource is skipped. "
            + "If you wish to extract all resources you can use the provided compression:no-filter#2 function."
    );
    private static final FunctionParameterSequenceType FS_PARAM_ENTRY_FILTER_WITH_PARAMS = param("entry-filter", Type.FUNCTION_REFERENCE,
            "A user defined function for filtering resources from the tar file. The function takes 3 parameters e.g. "
            + "user:untar-entry-filter($path as xs:string, $data-type as xs:string, $param as item()*) as xs:boolean. "
            + "$data-type may be 'resource' or 'folder'. $param is a sequence with any additional parameters, "
            + "for example a list of extracted files. If the return type is true() it indicates the entry "
            + "should be processed and passed to the entry-data function, else the resource is skipped. "
            + "If you wish to extract all resources you can use the provided compression:no-filter#3 function."
    );
    private static final FunctionParameterSequenceType FS_PARAM_ENTRY_FILTER_PARAM = optManyParam("entry-filter-param", Type.ANY_TYPE, "A sequence with an additional parameters for filtering function.");
    private static final FunctionParameterSequenceType FS_PARAM_ENTRY_DATA = param("entry-data", Type.FUNCTION_REFERENCE,
            "A user defined function for storing an extracted resource from the tar file. The function takes 3 parameters e.g. "
            + "user:untar-entry-data($path as xs:string, $data-type as xs:string, $data as item()?). "
            + "Or a user defined function which returns a db path for storing an extracted resource from the tar file. "
            + "The function takes 3 parameters e.g. user:entry-path($path as xs:string, $data-type as xs:string, "
            + "$param as item()*) as xs:anyURI. $data-type may be 'resource' or 'folder'. "
            + "Functions for storing the entries to a folder on the filesystem or a collection in the database "
            + "provided by compression:fs-store-entry3($dest) and compression:db-store-entry3($dest).");
    private static final FunctionParameterSequenceType FS_PARAM_ENTRY_DATA_WITH_PARAMS = param("entry-data", Type.FUNCTION_REFERENCE,
            "A user defined function for storing an extracted resource from the tar file. The function takes 4 parameters e.g. "
            + "user:untar-entry-data($path as xs:string, $data-type as xs:string, $data as item()?, $param as item()*). "
            + "Or a user defined function which returns a db path for storing an extracted resource from the tar file. The function takes 3 parameters e.g. "
            + "user:entry-path($path as xs:string, $data-type as xs:string, $param as item()*) as xs:anyURI. "
            + "$data-type may be 'resource' or 'folder'. $param is a sequence with any additional parameters"
            + "Functions for storing the entries to a folder on the filesystem or a collection in the database "
            + "provided by compression:fs-store-entry4($dest) and compression:db-store-entry4($dest)."
    );
    private static final FunctionParameterSequenceType FS_PARAM_ENTRY_DATA_PARAM = optManyParam("entry-data-param", Type.ANY_TYPE, "A sequence with an additional parameters for storing function.");


    private static final String FS_UNTAR_NAME = "untar";
    static final FunctionSignature[] FS_UNTAR = functionSignatures(
            FS_UNTAR_NAME,
            "UnTar all the resources/folders from the provided data by calling user defined functions to determine what and how to store the resources/folders",
            returnsOptMany(Type.ITEM),
            arities(
                arity(
                    FS_PARAM_TAR_DATA,
                    FS_PARAM_ENTRY_FILTER,
                    FS_PARAM_ENTRY_DATA
                ),
                arity(
                    FS_PARAM_TAR_DATA,
                    FS_PARAM_ENTRY_FILTER_WITH_PARAMS,
                    FS_PARAM_ENTRY_FILTER_PARAM,
                    FS_PARAM_ENTRY_DATA_WITH_PARAMS,
                    FS_PARAM_ENTRY_DATA_PARAM
                ),
                arity(
                    FS_PARAM_TAR_DATA,
                    FS_PARAM_ENTRY_FILTER_WITH_PARAMS,
                    FS_PARAM_ENTRY_FILTER_PARAM,
                    FS_PARAM_ENTRY_DATA_WITH_PARAMS,
                    FS_PARAM_ENTRY_DATA_PARAM,
                    param("encoding", Type.STRING, "The encoding to be used during uncompressing eg: UTF8 or Cp437 from https://docs.oracle.com/javase/8/docs/technotes/guides/intl/encoding.doc.html")
                )
            )
    );

    public UnTarFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }
	
    @Override
    protected Sequence processCompressedData(final BinaryValue compressedData, final Charset encoding) throws XPathException, XMLDBException {
        try(final TarArchiveInputStream tis = new TarArchiveInputStream(compressedData.getInputStream(), encoding.name())) {

            TarArchiveEntry entry = null;

            final Sequence results = new ValueSequence();

            while((entry = tis.getNextTarEntry()) != null) {
                final Sequence processCompressedEntryResults = processCompressedEntry(entry.getName(), entry.isDirectory(), tis, filterParam, storeParam);
                results.addAll(processCompressedEntryResults);
            }

            return results;
        } catch(final IOException ioe) {
            LOG.error(ioe.getMessage(), ioe);
            throw new XPathException(this, ioe.getMessage(), ioe);
        }
    }
}
