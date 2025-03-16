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
package org.exist.xquery.xqdoc;

import antlr.RecognitionException;
import antlr.TokenStreamException;
import org.exist.xquery.ExternalModule;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.xqdoc.parser.XQDocLexer;
import org.exist.xquery.xqdoc.parser.XQDocParser;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper for parsing XQDoc comments on function declarations. XQDoc comments
 * are stored in the function signature but not parsed until one of the
 * inspection functions accesses them.
 */
public class XQDocHelper {

    public static void parse(final FunctionSignature signature) {
        final String desc = signature.getDescription();
        if (desc == null || !desc.startsWith("(:")) {
            return;
        }
        final XQDocHelper helper = parseComment(desc);
        if (helper == null) {
            return;
        }
        helper.enhance(signature);
    }

    public static void parse(final ExternalModule module) {
        final String desc = module.getDescription();
        if (desc == null || !desc.startsWith("(:")) {
            return;
        }
        final XQDocHelper helper = parseComment(desc);
        if (helper == null) {
            return;
        }
        helper.enhance(module);
    }

    private static XQDocHelper parseComment(final String desc) {
        final XQDocLexer lexer = new XQDocLexer(new StringReader(desc));
        final XQDocParser parser = new XQDocParser(lexer);
        try {
            final XQDocHelper helper = new XQDocHelper();
            parser.xqdocComment(helper);
            return helper;
        } catch (final RecognitionException | TokenStreamException e) {
            // ignore: comment will be shown unparsed
        }
        return null;
    }

    private StringBuilder description = new StringBuilder();
    private Map<String, String> parameters = new HashMap<>();
    private String returnValue = null;
    private Map<String, String> meta = new HashMap<>();

    public void addDescription(final CharSequence part) {
        description.append(part.toString().trim());
    }

    public void setParameter(final String comment) {
        final String components[] = comment.trim().split("\\s+", 2);
        if(components != null && components.length == 2) {
            String var = components[0];
            if (!var.isEmpty() && var.charAt(0) == '$') {
                var = var.substring(1);
            }
            parameters.put(var, components[1].trim());
        }
    }

    public void setTag(final String tag, final String content) {
        if ("@param".equals(tag)) {
            setParameter(content.trim());
        } else if ("@return".equals(tag)) {
            returnValue = content.trim();
        } else {
            meta.put(tag, content);
        }
    }

    protected void enhance(final FunctionSignature signature) {
        signature.setDescription(description.toString().trim());
        if (returnValue != null) {
            final SequenceType returnType = signature.getReturnType();
            final FunctionReturnSequenceType newType =
                    new FunctionReturnSequenceType(returnType.getPrimaryType(), returnType.getCardinality(), returnValue);
            signature.setReturnType(newType);
        }
        final SequenceType[] args = signature.getArgumentTypes();
        for (final SequenceType type : args) {
            if (type instanceof FunctionParameterSequenceType argType) {
                final String desc = parameters.get(argType.getAttributeName());
                if (desc != null)
                    {argType.setDescription(desc);}
            }
        }
        for (final Map.Entry<String, String> entry: meta.entrySet()) {
            String key = entry.getKey();
            if (key.length() > 1 && key.charAt(0) == '@') {
                key = key.substring(1);
            }
            signature.addMetadata(key, entry.getValue());
        }
    }

    protected void enhance(final ExternalModule module) {
        module.setDescription(description.toString().trim());
        for (final Map.Entry<String, String> entry: meta.entrySet()) {
            String key = entry.getKey();
            if (key.length() > 1 && key.charAt(0) == '@') {
                key = key.substring(1);
            }
            module.addMetadata(key, entry.getValue());
        }
    }

    @Override
    public String toString() {
        final StringBuilder out = new StringBuilder();
        out.append(description.toString().trim()).append("\n\n");
        for (final Map.Entry<String, String> entry : meta.entrySet()) {
            out.append(String.format("%20s\t%s\n", entry.getKey(), entry.getValue()));
        }
        for (final Map.Entry<String, String> entry : parameters.entrySet()) {
            out.append(String.format("%20s\t%s\n", entry.getKey(), entry.getValue()));
        }
        return out.toString();
    }
}
