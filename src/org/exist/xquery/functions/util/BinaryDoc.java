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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.exist.dom.persistent.BinaryDocument;
import org.exist.dom.persistent.DocumentImpl;
import org.exist.dom.QName;
import org.exist.dom.persistent.LockedDocument;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.lock.Lock.LockMode;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.Base64BinaryDocument;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

public class BinaryDoc extends BasicFunction {
	
	protected static final Logger logger = LogManager.getLogger(BinaryDoc.class);

    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
            new QName("binary-doc", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Retrieves the binary resource and returns its contents as a " +
            "value of type xs:base64Binary. An empty sequence is returned if the resource " +
            "could not be found or $binary-resource was empty.",
            new SequenceType[] {
                new FunctionParameterSequenceType("binary-resource", Type.STRING, Cardinality.ZERO_OR_ONE, "The path to the binary resource")
            },
            new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_ONE, "the binary document")
        ),
        new FunctionSignature(
            new QName("binary-doc-available", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Checks if the binary resource identified by $binary-resource is available.",
            new SequenceType[] {
                new FunctionParameterSequenceType("binary-resource", Type.STRING, Cardinality.ZERO_OR_ONE, "The path to the binary resource")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the binary document is available")
        ),
        new FunctionSignature(
            new QName("is-binary-doc", UtilModule.NAMESPACE_URI, UtilModule.PREFIX),
            "Checks if the resource identified by $binary-resource is a binary resource.",
            new SequenceType[] {
                new FunctionParameterSequenceType("binary-resource", Type.STRING, Cardinality.ZERO_OR_ONE, "The path to the binary resource")
            },
            new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true if the resource is a binary document")
        )
    };
    
    public BinaryDoc(XQueryContext context, FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(Sequence[] args, Sequence contextSequence)
            throws XPathException {
    	
        final Sequence defaultReturn = (isCalledAs("binary-doc") ? Sequence.EMPTY_SEQUENCE : BooleanValue.FALSE);

        if (args[0].isEmpty()) {
            return defaultReturn;
        }

        final String path = args[0].getStringValue();
        try(final LockedDocument lockedDoc = context.getBroker().getXMLResource(XmldbURI.xmldbUriFor(path), LockMode.READ_LOCK);) {
            if(lockedDoc == null) {
                return defaultReturn;
            }

            final DocumentImpl doc = lockedDoc.getDocument();

            if(doc.getResourceType() != DocumentImpl.BINARY_FILE) {
                return defaultReturn;
            } else if(isCalledAs("binary-doc")) {
                final BinaryDocument bin = (BinaryDocument) doc;
                final InputStream is = context.getBroker().getBinaryResource(bin);

                /*
                long binaryLength = context.getBroker().getBinaryResourceSize(bin);

                byte[] data = new byte[(binaryLength > (long)Integer.MAX_VALUE)?Integer.MAX_VALUE:(int)binaryLength];
                is.read(data);
                is.close(); */
                final Base64BinaryDocument b64doc = Base64BinaryDocument.getInstance(context, is);
                b64doc.setUrl(path);
                return b64doc;
            } else {
                return BooleanValue.TRUE;
            }
        } catch (final URISyntaxException e) {
        	logger.error("Invalid resource URI", e);
            throw new XPathException(this, "Invalid resource uri",e);
        } catch (final PermissionDeniedException e) {
        	logger.info(path + ": permission denied to read resource", e);
            throw new XPathException(this, path + ": permission denied to read resource");
        } catch (final IOException e) {
        	logger.error(path + ": I/O error while reading resource", e);
            throw new XPathException(this, path + ": I/O error while reading resource",e);
        }
    }
}
