/*
 *  eXist EXPath
 *  Copyright (C) 2011 Adam Retter <adam@existsolutions.com>
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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.xml.transform.Source;
import org.exist.memtree.DocumentImpl;
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

    private Sequence result = new ValueSequence();
    private final XQueryContext context;

    public EXistResult(XQueryContext context) {
        this.context = context;
    }
    
    @Override
    public void add(String string) throws HttpClientException {
        try {
            result.add(new StringValue(string));
        } catch (XPathException xpe) {
           throw new HttpClientException("Unable to add string value to result:" + xpe.getMessage(), xpe);
        }
    }

    //TODO would be better if the EXPath API provided a stream!
    @Override
    public void add(byte[] bytes) throws HttpClientException {
        try {
            result.add(BinaryValueFromInputStream.getInstance(context, new Base64BinaryValueType(), new ByteArrayInputStream(bytes)));
        } catch(XPathException xpe) {
            throw new HttpClientException("Unable to add binary value to result:" + xpe.getMessage(), xpe);
        }
    }

    @Override
    public void add(Source src) throws HttpClientException {
        try {
            NodeValue nodeValue = ModuleUtils.sourceToXML(context, src);
            result.add(nodeValue);
        } catch(SAXException saxe) {
            throw new HttpClientException("Unable to add Source to result:" + saxe.getMessage(), saxe);
        } catch(IOException ioe) {
            throw new HttpClientException("Unable to add Source to result:" + ioe.getMessage(), ioe);
        } catch(XPathException xpe) {
            throw new HttpClientException("Unable to add Source to result:" + xpe.getMessage(), xpe);
        }
    }

    @Override
    public void add(HttpResponse response) throws HttpClientException {
        EXistTreeBuilder builder = new EXistTreeBuilder(context);
        response.outputResponseElement(builder);
        DocumentImpl doc = builder.close();
        try {
            // we add the root *element* to the result sequence
            NodeTest kind = new TypeTest(Type.ELEMENT);
            // the elem must always be added at the front, so if there are
            // already other items, we create a new one, add the elem, then
            // add the original items after
            if ( result.isEmpty() ) {
                doc.selectChildren(kind, result);
            }
            else {
                Sequence buf = result;
                result = new ValueSequence();
                doc.selectChildren(kind, result);
                result.addAll(buf);
            }
        } catch (XPathException xpe) {
            throw new HttpClientException("Unable to add HttpResponse to result:" + xpe.getMessage(), xpe);
        }
    }
    
    public Sequence getResult() {
        return result;
    }
}
