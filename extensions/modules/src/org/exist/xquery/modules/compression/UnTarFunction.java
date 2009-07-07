/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-08 The eXist Project
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

import org.apache.tools.tar.TarEntry;
import org.apache.tools.tar.TarInputStream;
import org.exist.dom.QName;
import org.exist.memtree.InMemoryNodeSet;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.xmldb.api.base.Collection;
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
                new FunctionParameterSequenceType("entry-filter", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "A user defined function for filtering resources from the tar file. The function takes 2 parameters e.g. user:untar-entry-filter($path as xs:anyURI, $data-type as xs:string) as xs:boolean. $type may be 'resource' or 'folder'. If the return type is true() it indicates the entry should be processed and passed to the entry-data function, else the resource is skipped."),
                new FunctionParameterSequenceType("entry-data", Type.FUNCTION_REFERENCE, Cardinality.EXACTLY_ONE, "A user defined function for storing an extracted resource from the tar file. The function takes 3 parameters e.g. user:untar-entry-data($path as xs:anyURI, $data-type as xs:string, $data as item()?). $type may be 'resource' or 'folder'"),
            },
            new SequenceType(Type.ITEM, Cardinality.ZERO_OR_MORE)
        )
    };

    public UnTarFunction(XQueryContext context, FunctionSignature signature)
    {
        super(context, signature);
    }
	
    @Override
    protected Sequence processCompressedData(Base64Binary compressedData) throws XPathException
    {
        TarInputStream tis = null;
        try
        {
            tis = new TarInputStream(new ByteArrayInputStream(compressedData.getBinaryData()));
            TarEntry entry = null;

            Sequence results = new ValueSequence();

            while((entry = tis.getNextEntry()) != null)
            {
                Sequence processCompressedEntryResults = processCompressedEntry(entry.getName(), entry.isDirectory(), tis);

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