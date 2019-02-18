/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-09 Wolfgang M. Meier
 *  wolfgang@exist-db.org
 *  http://exist.sourceforge.net
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
package org.exist.xquery.functions.request;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.util.List;

import org.exist.dom.QName;
import org.exist.http.servlets.RequestWrapper;
import org.exist.xquery.*;
import org.exist.xquery.value.Base64BinaryValueType;
import org.exist.xquery.value.BinaryValueFromFile;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import javax.annotation.Nonnull;

/**
 * @author wolf
 */
public class GetUploadedFile extends StrictRequestFunction {

    protected static final Logger logger = LogManager.getLogger(GetUploadedFile.class);
    public final static FunctionSignature signatures[] = {
        new FunctionSignature(
        new QName("get-uploaded-file-data", RequestModule.NAMESPACE_URI, RequestModule.PREFIX),
        "Retrieve the base64 encoded data where the file part of a multi-part request has been stored. "
        + "Returns the empty sequence if the request is not a multi-part request or the parameter name "
        + "does not point to a file part.",
        new SequenceType[]{
            new FunctionParameterSequenceType("upload-param-name", Type.STRING, Cardinality.EXACTLY_ONE, "The parameter name")
        },
        new FunctionReturnSequenceType(Type.BASE64_BINARY, Cardinality.ZERO_OR_MORE, "the base64 encoded data from the uploaded file"))
    };

    public GetUploadedFile(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }

    @Override
    public Sequence eval(final Sequence[] args, @Nonnull final RequestWrapper request)
            throws XPathException {

        final String uploadParamName = args[0].getStringValue();
        final List<Path> files = request.getFileUploadParam(uploadParamName);
        if (files == null || files.isEmpty()) {
            if (logger.isDebugEnabled()) {
                logger.debug("File param not found: " + uploadParamName);
            }
            return Sequence.EMPTY_SEQUENCE;
        }

        final ValueSequence result = new ValueSequence();
        for (final Path file : files) {
            result.add(BinaryValueFromFile.getInstance(context, new Base64BinaryValueType(), file));
        }

        return result;
    }
}
