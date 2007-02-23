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
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class BinaryDoc extends BasicFunction {

    public final static FunctionSignature signature =
        new FunctionSignature(
            new QName("binary-doc", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Retrieves the binary resource identified by $a and returns its contents as a " +
            "value of type xs:base64Binary. An empty sequence is returned if the resource " +
            "could not be found or $a was empty.",
            new SequenceType[] {
                new SequenceType(Type.STRING, Cardinality.ZERO_OR_ONE)
            },
            new SequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE),
            true
        );
    
    public BinaryDoc(XQueryContext context) {
        super(context, signature);
    }

    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
        if (args[0].isEmpty())
            return Sequence.EMPTY_SEQUENCE;
        String path = args[0].getStringValue();
        DocumentImpl doc = null;
        try {
            doc = context.getBroker().getXMLResource(XmldbURI.xmldbUriFor(path), Lock.READ_LOCK);
            if (doc == null)
                return Sequence.EMPTY_SEQUENCE;
            if (doc.getResourceType() != DocumentImpl.BINARY_FILE)
                throw new XPathException(getASTNode(), path + " exists but is not a binary resource");
            BinaryDocument bin = (BinaryDocument) doc;
            byte[] data = context.getBroker().getBinaryResource(bin);
            return new Base64Binary(data);
        } catch (URISyntaxException e) {
            throw new XPathException(getASTNode(), "Invalid resource uri",e);
        } catch (PermissionDeniedException e) {
            throw new XPathException(getASTNode(), path + ": permission denied to read resource");
        } finally {
            if (doc != null)
                doc.getUpdateLock().release(Lock.READ_LOCK);
        }
    }
}
