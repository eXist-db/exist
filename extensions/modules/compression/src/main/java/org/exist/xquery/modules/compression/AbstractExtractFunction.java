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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.exist.util.FileUtils;
import org.exist.util.MimeTable;
import org.exist.util.MimeType;
import org.apache.commons.io.input.UnsynchronizedByteArrayInputStream;
import org.apache.commons.io.output.UnsynchronizedByteArrayOutputStream;
import org.exist.xmldb.EXistResource;
import org.exist.xmldb.LocalCollection;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.functions.xmldb.XMLDBAbstractCollectionManipulator;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.*;

import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.BinaryResource;
import org.xmldb.api.modules.XMLResource;

/**
 * @author <a href="mailto:adam@exist-db.org">Adam Retter</a>
 * @version 1.0
 */
public abstract class AbstractExtractFunction extends BasicFunction
{
    private FunctionReference entryFilterFunction = null;
    protected Sequence filterParam = null;
    private FunctionReference entryDataFunction = null;
    protected Sequence storeParam = null;
    private Sequence contextSequence;
    
    public AbstractExtractFunction(XQueryContext context, FunctionSignature signature)
    {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence) throws XPathException
    {
        this.contextSequence = contextSequence;

        if(args[0].isEmpty())
            return Sequence.EMPTY_SEQUENCE;

        //get the entry-filter function and check its types
        if(!(args[1].itemAt(0) instanceof FunctionReference))
            throw new XPathException(this, "No entry-filter function provided.");
        entryFilterFunction = (FunctionReference)args[1].itemAt(0);
        FunctionSignature entryFilterFunctionSig = entryFilterFunction.getSignature();
        if(entryFilterFunctionSig.getArgumentCount() < 3)
            throw new XPathException(this, "entry-filter function must take at least 3 arguments.");

        filterParam = args[2];

        //get the entry-data function and check its types
        if(!(args[3].itemAt(0) instanceof FunctionReference))
            throw new XPathException(this, "No entry-data function provided.");
        entryDataFunction = (FunctionReference)args[3].itemAt(0);
        FunctionSignature entryDataFunctionSig = entryDataFunction.getSignature();
        if(entryDataFunctionSig.getArgumentCount() < 3)
            throw new XPathException(this, "entry-data function must take at least 3 arguments");

        storeParam = args[4];

        try {
            final Charset encoding;
            if ((args.length >= 6) && !args[5].isEmpty()) {
                encoding = Charset.forName(args[5].getStringValue());
            } else {
                encoding = StandardCharsets.UTF_8;
            }

            BinaryValue compressedData = ((BinaryValue) args[0].itemAt(0));

            return processCompressedData(compressedData, encoding);
        } catch(final UnsupportedCharsetException | XMLDBException e) {
            throw new XPathException(this, e.getMessage(), e);
		} finally {
            entryDataFunction.close();
            entryFilterFunction.close();
        }
    }

    /**
     * Processes a compressed archive
     *
     * @param compressedData the compressed data to extract
     * @param encoding the encoding
     * @return Sequence of results
     *
     * @throws XPathException if a query error occurs
     * @throws XMLDBException if a database error occurs
     */
    protected abstract Sequence processCompressedData(BinaryValue compressedData, Charset encoding) throws XPathException, XMLDBException;

    /**
     * Processes a compressed entry from an archive
     *
     * @param name The name of the entry
     * @param isDirectory true if the entry is a directory, false otherwise
     * @param is an InputStream for reading the uncompressed data of the entry
     * @param filterParam is an additional param for entry filtering function  
     * @param storeParam is an additional param for entry storing function
     *
     * @return the result of processing the compressed entry.
     *
     * @throws XPathException if a query error occurs
     * @throws XMLDBException if a database error occurs
     * @throws IOException if an I/O error occurs
     */
    protected Sequence processCompressedEntry(String name, boolean isDirectory, InputStream is, Sequence filterParam, Sequence storeParam) throws IOException, XPathException, XMLDBException
    {
        String dataType = isDirectory ? "folder" : "resource";

        //call the entry-filter function
        Sequence filterParams[] = new Sequence[3];
        filterParams[0] = new StringValue(this, name);
        filterParams[1] = new StringValue(this, dataType);
        filterParams[2] = filterParam;
        Sequence entryFilterFunctionResult = entryFilterFunction.evalFunction(contextSequence, null, filterParams);

        if(BooleanValue.FALSE == entryFilterFunctionResult.itemAt(0))
        {
            return Sequence.EMPTY_SEQUENCE;
        }
        else {
            Sequence entryDataFunctionResult;
            Sequence uncompressedData = Sequence.EMPTY_SEQUENCE;

            if (entryDataFunction.getSignature().getReturnType().getPrimaryType() != Type.EMPTY_SEQUENCE && entryDataFunction.getSignature().getArgumentCount() == 3) {

                Sequence dataParams[] = new Sequence[3];
                System.arraycopy(filterParams, 0, dataParams, 0, 2);
                dataParams[2] = storeParam;
                entryDataFunctionResult = entryDataFunction.evalFunction(contextSequence, null, dataParams);

                String path = entryDataFunctionResult.itemAt(0).getStringValue();

                Collection root = new LocalCollection(context.getSubject(), context.getBroker().getBrokerPool(), new AnyURIValue(this, "/db").toXmldbURI());

                if (isDirectory) {

                    XMLDBAbstractCollectionManipulator.createCollection(root, path);

                } else {
                    Path file = Paths.get(path).normalize();
                    name = FileUtils.fileName(file);
                    path = file.getParent().toAbsolutePath().toString();

                    Collection target = (path == null) ? root : XMLDBAbstractCollectionManipulator.createCollection(root, path);

                    MimeType mime = MimeTable.getInstance().getContentTypeFor(name);

                    //copy the input data
                    final byte[] entryData;
                    try (final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream()) {
                        baos.write(is);
                        entryData = baos.toByteArray();
                    }

                    try (final InputStream bis = new UnsynchronizedByteArrayInputStream(entryData)) {
                        NodeValue content = ModuleUtils.streamToXML(context, bis, this);
                        try (Resource  resource = target.createResource(name, XMLResource.class)) {
                            ContentHandler handler = ((XMLResource) resource).setContentAsSAX();
                            handler.startDocument();
                            content.toSAX(context.getBroker(), handler, null);
                            handler.endDocument();
                            storeResource(target, mime, resource);
                        }
                    } catch (SAXException e) {
                        try (Resource  resource = target.createResource(name, BinaryResource.class)) {
                            resource.setContent(entryData);
                            storeResource(target, mime, resource);
                        }
                    }
                }

            } else {

                //copy the input data
                final byte[] entryData;
                try (final UnsynchronizedByteArrayOutputStream baos = new UnsynchronizedByteArrayOutputStream()) {
                    baos.write(is);
                    entryData = baos.toByteArray();
                }

                //try and parse as xml, fall back to binary
                try (final InputStream bis = new UnsynchronizedByteArrayInputStream(entryData)) {
                    uncompressedData = ModuleUtils.streamToXML(context, bis, this);
                } catch (SAXException saxe) {
                    if (entryData.length > 0) {
                        try (final InputStream bis = new UnsynchronizedByteArrayInputStream(entryData)) {
                            uncompressedData = BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), bis, this);
                        }
                    }
                }

                //call the entry-data function
                Sequence dataParams[] = new Sequence[4];
                System.arraycopy(filterParams, 0, dataParams, 0, 2);
                dataParams[2] = uncompressedData;
                dataParams[3] = storeParam;
                entryDataFunctionResult = entryDataFunction.evalFunction(contextSequence, null, dataParams);

            }

            return entryDataFunctionResult;
        }
    }

    private void storeResource(Collection target, MimeType mime, Resource resource) throws XMLDBException {
        if (mime != null) {
            ((EXistResource) resource).setMimeType(mime.getName());
        }
        target.storeResource(resource);
    }

}
