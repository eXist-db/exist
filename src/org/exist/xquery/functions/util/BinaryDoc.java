/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;

import org.exist.dom.BinaryDocument;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64Binary;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class BinaryDoc extends BasicFunction {

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("binary-doc", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Retrieves the binary resource identified by $a and returns its contents as a " +
            "value of type xs:base64Binary. An empty sequence is returned if the resource " +
            "could not be found or $a was empty.",
            new SequenceType[] {
                new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE)
        ),
        new FunctionSignature(
            new QName("binary-doc-available", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Checks if the binary resource identified by $a is available.",
            new SequenceType[] {
                new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
        ),
        new FunctionSignature(
            new QName("is-binary-doc", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Checks if the resource identified by $a is a binary resource.",
            new SequenceType[] {
                new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
        )
    };
    
    public BinaryDoc(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
        Sequence defaultReturn = (isCalledAs("binary-doc") ? Sequence.EMPTY_SEQUENCE : BooleanValue.FALSE);

        if (args[0].isEmpty())
            return defaultReturn;

        String path = args[0].getStringValue();
        DocumentImpl doc = null;
        try {
            doc = context.getBroker().getXMLResource(XmldbURI.xmldbUriFor(path), Lock.READ_LOCK);
            if(doc == null)
            {
                return defaultReturn;
            }
            else if(doc.getResourceType() != DocumentImpl.BINARY_FILE)
            {
                return defaultReturn;
            }
            else if(isCalledAs("binary-doc"))
            {
                BinaryDocument bin = (BinaryDocument) doc;
                InputStream is = context.getBroker().getBinaryResource(bin);
                byte[] data = new byte[(int)context.getBroker().getBinaryResourceSize(bin)];
                is.read(data);
                is.close();
                return new Base64Binary(data);
            }
            else
            {
                return BooleanValue.TRUE;
            }
        } catch (URISyntaxException e) {
            throw new XPathException(this, "Invalid resource uri",e);
        } catch (PermissionDeniedException e) {
            throw new XPathException(this, path + ": permission denied to read resource");
        } catch (IOException e) {
            throw new XPathException(this, path + ": I/O error while reading resource",e);
        } finally {
            if (doc != null)
                doc.getUpdateLock().release(Lock.READ_LOCK);
        }
    }
}
