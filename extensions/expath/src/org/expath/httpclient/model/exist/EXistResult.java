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
import javax.xml.transform.Source;
import org.exist.memtree.DocumentImpl;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.modules.ModuleUtils;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.ValueSequence;
import org.exist.xquery.value.StringValue;
import org.expath.httpclient.HttpClientException;
import org.expath.httpclient.HttpResponse;
import org.expath.httpclient.model.Result;
import org.xml.sax.SAXException;

/**
 * @author Adam Retter <adam@existsolutions.com>
 */
public class EXistResult implements Result {

    private final Sequence result = new ValueSequence();
    private final XQueryContext context;

    public EXistResult(XQueryContext context) {
        this.context = context;
    }
    
    //@Override
    public void add(String string) throws HttpClientException {
        try {
            result.add(new StringValue(string));
        } catch (XPathException xpe) {
           throw new HttpClientException("Unable to add string value to result:" + xpe.getMessage(), xpe);
        }
    }

    //TODO would be better if the EXPath API provided a stream!
    //@Override
    public void add(byte[] bytes) throws HttpClientException {
        try {
            result.add(new Base64Binary(bytes));
        } catch(XPathException xpe) {
            throw new HttpClientException("Unable to add binary value to result:" + xpe.getMessage(), xpe);
        }
    }

    //@Override
    public void add(Source src) throws HttpClientException {
        
        try {
            //TODO badly formed HTML needs tidying into XHTML - would be better
            //to have seperate methods for HTML and XML retrieval in EXPath.
            
            //NodeValue nodeValue = ModuleUtils.sourceToXML(context, src);
            NodeValue nodeValue = ModuleUtils.htmlToXHtml(context, "", src, null, null);
            result.add(nodeValue);
        } catch(SAXException saxe) {
            throw new HttpClientException("Unable to add Source to result:" + saxe.getMessage(), saxe);
        } catch(XPathException xpe) {
            throw new HttpClientException("Unable to add Source to result:" + xpe.getMessage(), xpe);
        }
    }

    //@Override
    public void add(HttpResponse response) throws HttpClientException {
        EXistTreeBuilder builder = new EXistTreeBuilder(context);
        response.outputResponseElement(builder);
        DocumentImpl elem = builder.close();
        try {
            result.add(elem);
        } catch (XPathException xpe) {
            throw new HttpClientException("Unable to add HttpResponse to result:" + xpe.getMessage(), xpe);
        }
    }
    
    public Sequence getResult() {
        return result;
    }
}