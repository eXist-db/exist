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

import java.io.ByteArrayInputStream;
import java.io.IOException;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import org.exist.dom.QName;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.xmldb.api.base.XMLDBException;

/**
 * Extracts files and folders from a Tar file
 * 
 * @author Adam Retter <adam@exist-db.org>
 * @version 1.0
 */
public class UnTarFunction extends AbstractExtractFunction {

   public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("untar", CompressionModule.NAMESPACE_URI, CompressionModule.PREFIX),
            "UnTar all the resources/folders from the provided data by calling user defined functions " +
            "to determine what and how to store the resources/folders",
            new SequenceType[] {
                new FunctionParameterSequenceType("tar-data", Type.BASE64_BINARY, Cardinality.EXACTLY_ONE, "The tar file data"),
                new FunctionParameterSequenceType("entry-filter", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, 
                		"A user defined function for filtering resources from the tar file. The function takes 3 parameters e.g. " +
                		"user:untar-entry-filter($path as xs:string, $data-type as xs:string, $param as item()*) as xs:boolean. " +
                		"$data-type may be 'resource' or 'folder'. $param is a sequence with any additional parameters, " +
                		"for example a list of extracted files. If the return type is true() it indicates the entry " +
                		"should be processed and passed to the entry-data function, else the resource is skipped."),
                new FunctionParameterSequenceType("entry-filter-param", Type.ANY_TYPE, Cardinality.ZERO_OR_MORE, "A sequence with an additional parameters for filtering function."),
                new FunctionParameterSequenceType("entry-data", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, 
                		"A user defined function for storing an extracted resource from the tar file. The function takes 4 parameters e.g. " +
                		"user:untar-entry-data($path as xs:string, $data-type as xs:string, $data as item()?, $param as item()*). " +
                		"Or a user defined function wich returns path for storing an extracted resource from the tar file. The function takes 3 parameters e.g. " +
                		"user:entry-path($path as xs:string, $data-type as xs:string, $param as item()*) as xs:anyURI. " +
                		"$data-type may be 'resource' or 'folder'. $param is a sequence with any additional parameters"),
                new FunctionParameterSequenceType("entry-data-param", Type.ANY_TYPE, Cardinality.ZERO_OR_MORE, "A sequence with an additional parameters for storing function."),
            },
            new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
        )
    };

    public UnTarFunction(XQueryContext context, FunctionSignature signature)
    {
        super(context, signature);
    }
	
    @Override
    protected Sequence processCompressedData(BinaryValue compressedData) throws XPathException, XMLDBException
    {
        TarArchiveInputStream tis = null;
        try
        {
            tis = new TarArchiveInputStream(compressedData.getInputStream());
            TarArchiveEntry entry = null;

            Sequence results = new ValueSequence();

            while((entry = tis.getNextTarEntry()) != null)
            {
                Sequence processCompressedEntryResults = processCompressedEntry(entry.getName(), entry.isDirectory(), tis, filterParam, storeParam);

                results.addAll(processCompressedEntryResults);
            }

            return results;
        }
        catch(IOException ioe)
        {
            LOG.error(ioe.getMessage(), ioe);
            throw new XPathException(this, ioe.getMessage(), ioe);
        }
        finally
        {
            if(tis != null)
            {
                try
                {
                    tis.close();
                }
                catch(IOException ioe)
                {
                    LOG.warn(ioe.getMessage(), ioe);
                }
            }
        }
    }
}