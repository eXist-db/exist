/*
 *  eXist EXPath
 *  Copyright (C) 2013 Adam Retter <adam@existsolutions.com>
 *  www.existsolutions.com
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.expath.httpclient.model.exist;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import javax.xml.transform.Source;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.memtree.DocumentImpl;
import org.exist.xquery.NodeTest;
import org.exist.xquery.TypeTest;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValueFromInputStream;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.ValueSequence;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;
import org.expath.httpclient.HttpClientException;
import org.expath.httpclient.HttpResponse;
import org.expath.httpclient.model.Result;
import org.xml.sax.SAXException;

/**
 * @author Adam Retter <adam@existsolutions.com>
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
    public void add(final Reader reader) throws HttpClientException {

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
            throw new HttpClientException("Unable to add string value to result: " + ioe.getMessage(), ioe);
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
            result.add(BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), is));
        } catch(final XPathException xpe) {
            throw new HttpClientException("Unable to add binary value to result:" + xpe.getMessage(), xpe);
        }
    }

    @Override
    public void add(final Source src) throws HttpClientException {
        try {
            final NodeValue nodeValue = ModuleUtils.sourceToXML(context, src);
            result.add(nodeValue);
        } catch(final SAXException saxe) {
            throw new HttpClientException("Unable to add Source to result:" + saxe.getMessage(), saxe);
        } catch(final IOException ioe) {
            throw new HttpClientException("Unable to add Source to result:" + ioe.getMessage(), ioe);
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
            throw new HttpClientException("Unable to add HttpResponse to result:" + xpe.getMessage(), xpe);
        }
    }
    
    public Sequence getResult() {
        return result;
    }
}
