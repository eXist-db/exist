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
package org.exist.xquery.modules.vector;

import org.exist.dom.QName;
import org.exist.vector.VectorModelDiagnostics;
import org.exist.vector.VectorModelInfo;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.NodeValue;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.Type;
import org.exist.xquery.value.ValueSequence;

import javax.annotation.Nullable;
import java.util.List;

import static org.exist.xquery.modules.vector.VectorModule.functionSignature;

/**
 * vector:diagnostics() — Detailed status for configured and built-in models.
 * <p>
 * Returns a sequence of <code>&lt;model/></code> elements with attributes:
 * <ul>
 *   <li>id — model identifier</li>
 *   <li>source — "registry" | "builtin" | "registry+builtin"</li>
 *   <li>path — resolved configuration path (relative string)</li>
 *   <li>dimension — configured or default dimension</li>
 *   <li>status — "available" | "missing" | "http"</li>
 *   <li>provider — "ONNX" | "HTTP" (uppercase by convention)</li>
 *   <li>message — optional hint when status != "available"</li>
 * </ul>
 */
public class Diagnostics extends BasicFunction {

  static final FunctionSignature[] signatures = {
      functionSignature("diagnostics",
          "Returns diagnostic information for vector models: registry entries and built-in models, including path, dimension and availability status.",
          new FunctionReturnSequenceType(Type.NODE, Cardinality.ZERO_OR_MORE, "Model diagnostic elements"))
  };

  private static final QName MODEL_QNAME = new QName("model", VectorModule.NAMESPACE_URI, VectorModule.PREFIX);

  public Diagnostics(final XQueryContext context, final FunctionSignature signature) {
    super(context, signature);
  }

  @Override
  public Sequence eval(final Sequence[] args, @Nullable final Sequence contextSequence) {
    final ValueSequence result = new ValueSequence();
    for (final VectorModelInfo model : VectorModelDiagnostics.collectModels()) {
      context.pushDocumentContext();
      final org.exist.dom.memtree.MemTreeBuilder builder = context.getDocumentBuilder();
      builder.startDocument();
      final int nodeNr = builder.startElement(MODEL_QNAME, null);
      builder.addAttribute(new QName("id", null, null), model.getId());
      builder.addAttribute(new QName("source", null, null), model.getSource());
      builder.addAttribute(new QName("path", null, null), model.getPath());
      builder.addAttribute(new QName("dimension", null, null), Integer.toString(model.getDimension()));
      builder.addAttribute(new QName("status", null, null), model.getStatus());
      builder.addAttribute(new QName("provider", null, null), model.getProvider());
      final String message = model.getMessage();
      if (message != null && !message.isEmpty()) {
        builder.addAttribute(new QName("message", null, null), message);
      }
      builder.endElement();
      context.popDocumentContext();
      final NodeValue node = (NodeValue) builder.getDocument().getNode(nodeNr);
      result.add(node);
    }

    return result;
  }
}
