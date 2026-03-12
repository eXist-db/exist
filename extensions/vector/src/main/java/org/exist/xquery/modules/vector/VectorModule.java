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

import java.util.List;
import java.util.Map;

import org.exist.dom.QName;
import org.exist.xquery.AbstractInternalModule;
import org.exist.xquery.FunctionDSL;
import org.exist.xquery.FunctionDef;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;

import static org.exist.xquery.FunctionDSL.optParam;
import static org.exist.xquery.FunctionDSL.param;

/**
 * XQuery module for vector embedding operations.
 * Provides query-time embedding via vector:embed() for use with ft:query-vector.
 */
public class VectorModule extends AbstractInternalModule {

  public static final String NAMESPACE_URI = "http://exist-db.org/xquery/vector";
  public static final String PREFIX = "vector";
  public static final String INCLUSION_DATE = "2025-01-01";
  public static final String RELEASED_IN_VERSION = "eXist-7.0";

  private static final FunctionParameterSequenceType FS_PARAM_TEXT = param("text", org.exist.xquery.value.Type.STRING,
      "Text to embed (e.g. user search query).");
  private static final FunctionParameterSequenceType FS_PARAM_MODEL = param("model", org.exist.xquery.value.Type.STRING,
      "Model identifier (e.g. 'all-MiniLM-L6-v2').");
  private static final FunctionParameterSequenceType FS_PARAM_MODEL_PATH = optParam("model-path", org.exist.xquery.value.Type.STRING,
      "Optional path to model directory. If omitted, resolves via conf.xml registry or onnx-models/{model}.");

  public static final FunctionDef[] functions = {
      new FunctionDef(Embed.signatures[0], Embed.class),
      new FunctionDef(Embed.signatures[1], Embed.class),
      new FunctionDef(Embed.signatures[2], Embed.class),
      new FunctionDef(EmbedBatch.signatures[0], EmbedBatch.class),
      new FunctionDef(EmbedBatch.signatures[1], EmbedBatch.class),
      new FunctionDef(EmbedBatch.signatures[2], EmbedBatch.class),
      new FunctionDef(Models.signatures[0], Models.class)
  };

  public VectorModule(final Map<String, List<? extends Object>> parameters) {
    super(functions, parameters, false);
  }

  @Override
  public String getNamespaceURI() {
    return NAMESPACE_URI;
  }

  @Override
  public String getDefaultPrefix() {
    return PREFIX;
  }

  @Override
  public String getDescription() {
    return "Vector embedding module for query-time embedding (e.g. for ft:query-vector).";
  }

  @Override
  public String getReleaseVersion() {
    return RELEASED_IN_VERSION;
  }

  static FunctionSignature functionSignature(final String name, final String description,
      final FunctionReturnSequenceType returnType, final FunctionParameterSequenceType... paramTypes) {
    return FunctionDSL.functionSignature(new QName(name, NAMESPACE_URI, PREFIX), description, returnType, paramTypes);
  }
}
