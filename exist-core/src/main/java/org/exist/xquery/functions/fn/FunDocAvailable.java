/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2005-2017 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 */
package org.exist.xquery.functions.fn;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.exist.dom.QName;
import org.exist.xquery.*;
import org.exist.xquery.functions.xmldb.XMLDBModule;
import org.exist.xquery.util.DocUtils;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.Item;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.Type;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Implements the XQuery's fn:doc-available() function.
 *
 * @author <a href="mailto:pierrick.brihaye@free.fr">Pierrick Brihaye</a>
 * @author wolf
 */
public class FunDocAvailable extends Function {

    protected static final Logger logger = LogManager.getLogger(FunDocAvailable.class);

    public static final FunctionSignature signature =
            new FunctionSignature(
                    new QName("doc-available", Function.BUILTIN_FUNCTION_NS),
                    "Returns whether or not the document, $document-uri, " +
                            "specified in the input sequence is available. " +
                            XMLDBModule.ANY_URI,
                    new SequenceType[]{
                            new FunctionParameterSequenceType("document-uri", Type.STRING,
                                    Cardinality.ZERO_OR_ONE, "The document URI")
                    },
                    new FunctionReturnSequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE,
                            "true() if the document is available, false() otherwise"));

    public FunDocAvailable(final XQueryContext context) {
        super(context, signature);
    }

    @Override
    public int getDependencies() {
        return Dependency.CONTEXT_SET;
    }

    @Override
    public Sequence eval(final Sequence contextSequence, final Item contextItem)
            throws XPathException {
        if (context.getProfiler().isEnabled()) {
            context.getProfiler().start(this);
            context.getProfiler().message(this, Profiler.DEPENDENCIES,
                    "DEPENDENCIES", Dependency.getDependenciesName(this.getDependencies()));
            if (contextSequence != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT SEQUENCE", contextSequence);
            }
            if (contextItem != null) {
                context.getProfiler().message(this, Profiler.START_SEQUENCES,
                        "CONTEXT ITEM", contextItem.toSequence());
            }
        }

        Sequence result = BooleanValue.FALSE;
        final Sequence arg = getArgument(0).eval(contextSequence, contextItem);
        if (!arg.isEmpty()) {
            final String path = arg.itemAt(0).getStringValue();

            try {
                new URI(path);
            } catch (final URISyntaxException e) {
                throw new XPathException(this, ErrorCodes.FODC0005, e.getMessage(), arg, e);
            }

            try {
                result = BooleanValue.valueOf(DocUtils.isDocumentAvailable(this.context, path));
            } catch (final XPathException e) {
                result = BooleanValue.FALSE;
            }
        }

        if (context.getProfiler().isEnabled()) {
            context.getProfiler().end(this, "", result);
        }

        return result;
    }

    @Override
    public void resetState(final boolean postOptimization) {
        super.resetState(postOptimization);
        getArgument(0).resetState(postOptimization);
    }
}
