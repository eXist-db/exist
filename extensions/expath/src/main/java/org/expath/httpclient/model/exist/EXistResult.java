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
package org.expath.httpclient.model.exist;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javax.xml.transform.Source;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.util.io.TemporaryFileManager;
import org.exist.xquery.NodeTest;
import org.exist.xquery.TypeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValueFromFile;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;
import org.expath.httpclient.HttpClientException;
import org.expath.httpclient.HttpResponse;
import org.expath.httpclient.model.Result;
import org.xml.sax.SAXException;

import static org.expath.httpclient.HttpClientError.HC001;

/**
 * @author <a href="mailto:adam@existsolutions.com">Adam Retter</a>
 */
public class EXistResult implements Result {

    private static final Logger logger = LogManager.getLogger(EXistResult.class);
    
    ValueSequence result = new ValueSequence();
    
    private final XQueryContext context;

    public EXistResult(final XQueryContext context) {
        this.context = context;
    }

    @Override
    public Result makeNewResult() throws HttpClientException {
        return new EXistResult(context.copyContext());
    }

    @Override
    public void add(final Reader reader, final Charset charset) throws HttpClientException {

        // START TEMP
        //TODO(AR) - replace with a deferred StringReader when eXist has this soon.
        final StringBuilder builder = new StringBuilder();
        try {
            final char cbuf[] = new char[4096];
            int read = -1;
            while((read = reader.read(cbuf)) > -1) {
                builder.append(cbuf, 0, read);
            }
        } catch(final IOException ioe) {
            throw new HttpClientException(HC001, "Unable to add string value to result: " + ioe.getMessage(), ioe);
        } finally {
            try {
                reader.close();
            } catch(final IOException ioe) {
                logger.warn(ioe.getMessage(), ioe);
            }
        }
        // END TEMP

        result.add(new StringValue(builder.toString()));
    }

    @Override
    public void add(final InputStream is) throws HttpClientException {
        try {
            // we have to make a temporary copy of the data stream, as the socket will be closed shortly
            final TemporaryFileManager temporaryFileManager = TemporaryFileManager.getInstance();
            final Path tempFile = temporaryFileManager.getTemporaryFile();
            Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);

            result.add(BinaryValueFromFile.getInstance(context, new Base64BinaryValueType(), tempFile, (isClosed, file) -> temporaryFileManager.returnTemporaryFile(file), null));
        } catch(final XPathException | IOException xpe) {
            throw new HttpClientException(HC001, "Unable to add binary value to result:" + xpe.getMessage(), xpe);
        } finally {
            try {
                is.close();
            } catch(final IOException ioe) {
                logger.warn(ioe.getMessage(), ioe);
            }
        }
    }

    @Override
    public void add(final Source src) throws HttpClientException {
        try {
            final NodeValue nodeValue = ModuleUtils.sourceToXML(context, src, null);
            result.add(nodeValue);
        } catch(final SAXException | IOException saxe) {
            throw new HttpClientException(HC001, "Unable to add Source to result:" + saxe.getMessage(), saxe);
        }
    }

    @Override
    public void add(final HttpResponse response) throws HttpClientException {
        final EXistTreeBuilder builder = new EXistTreeBuilder(context);
        response.outputResponseElement(builder);
        final DocumentImpl doc = builder.close();
        try {
            // we add the root *element* to the result sequence
            final NodeTest kind = new TypeTest(Type.ELEMENT);
            // the elem must always be added at the front, so if there are
            // already other items, we create a new one, add the elem, then
            // add the original items after
            if(result.isEmpty()) {
                doc.selectChildren(kind, result);
            } else {
                final ValueSequence newResult = new ValueSequence();                
                doc.selectChildren(kind, newResult);
                newResult.addAll(result);
                result = newResult;
            }
        } catch (final XPathException xpe) {
            throw new HttpClientException(HC001, "Unable to add HttpResponse to result:" + xpe.getMessage(), xpe);
        }
    }
    
    public Sequence getResult() {
        return result;
    }
}
