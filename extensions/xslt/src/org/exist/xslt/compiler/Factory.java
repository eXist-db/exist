/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2008-2010 The eXist Project
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
package org.exist.xslt.compiler;

import java.util.HashMap;
import java.util.Map;

import org.exist.dom.QName;
import org.exist.xslt.XSLStylesheet;
import org.exist.xslt.expression.*;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public class Factory {
	
	public static final String prefix = "xsl";
	public static final String namespaceURI = "http://www.w3.org/1999/XSL/Transform";
	
	public static final Map<QName, Class> qns = new HashMap<QName, Class>();
	
	static {
		qns.put(new QName("stylesheet", namespaceURI, prefix), XSLStylesheet.class);
		qns.put(new QName("transform", namespaceURI, prefix), XSLStylesheet.class);
		qns.put(new QName("template", namespaceURI, prefix), Template.class);
		qns.put(new QName("value-of", namespaceURI, prefix), ValueOf.class);
		
		qns.put(new QName("import", namespaceURI, prefix), Import.class);
		qns.put(new QName("include", namespaceURI, prefix), Include.class);
		qns.put(new QName("attribute-set", namespaceURI, prefix), AttributeSet.class);
		qns.put(new QName("character-map", namespaceURI, prefix), CharacterMap.class);
		qns.put(new QName("decimal-format", namespaceURI, prefix), DecimalFormat.class);
		qns.put(new QName("function", namespaceURI, prefix), Function.class);
		qns.put(new QName("import-schema", namespaceURI, prefix), ImportSchema.class);
		qns.put(new QName("key", namespaceURI, prefix), Key.class);
		qns.put(new QName("namespace-alias", namespaceURI, prefix), NamespaceAlias.class);
		qns.put(new QName("output", namespaceURI, prefix), Output.class);
		qns.put(new QName("param", namespaceURI, prefix), Param.class);
		qns.put(new QName("preserve-space", namespaceURI, prefix), PreserveSpace.class);
		qns.put(new QName("strip-space", namespaceURI, prefix), StripSpace.class);
		qns.put(new QName("variable", namespaceURI, prefix), Variable.class);

		qns.put(new QName("attribute", namespaceURI, prefix), Attribute.class);
		
		qns.put(new QName("copy", namespaceURI, prefix), Copy.class);
		qns.put(new QName("element", namespaceURI, prefix), org.exist.xslt.expression.Element.class);
		qns.put(new QName("document", namespaceURI, prefix), Document.class);
		qns.put(new QName("result-document", namespaceURI, prefix), ResultDocument.class);

		qns.put(new QName("comment", namespaceURI, prefix), Comment.class);
		qns.put(new QName("text", namespaceURI, prefix), Text.class);

		qns.put(new QName("message", namespaceURI, prefix), Message.class);

		qns.put(new QName("analyze-string", namespaceURI, prefix), AnalyzeString.class);
		qns.put(new QName("apply-imports", namespaceURI, prefix), ApplyImports.class);
		qns.put(new QName("apply-templates", namespaceURI, prefix), ApplyTemplates.class);
		qns.put(new QName("call-template", namespaceURI, prefix), CallTemplate.class);
		qns.put(new QName("choose", namespaceURI, prefix), Choose.class);
		qns.put(new QName("fallback", namespaceURI, prefix), Fallback.class);
		qns.put(new QName("for-each", namespaceURI, prefix), ForEach.class);
		qns.put(new QName("for-each-group", namespaceURI, prefix), ForEachGroup.class);
		qns.put(new QName("if", namespaceURI, prefix), If.class);
		qns.put(new QName("matching-substring", namespaceURI, prefix), MatchingSubstring.class);
		qns.put(new QName("next-match", namespaceURI, prefix), NextMatch.class);
		qns.put(new QName("non-matching-substring", namespaceURI, prefix), NonMatchingSubstring.class);
		qns.put(new QName("otherwise", namespaceURI, prefix), Otherwise.class);
		qns.put(new QName("perform-sort", namespaceURI, prefix), PerformSort.class);
		qns.put(new QName("sequence", namespaceURI, prefix), SequenceConstructor.class);
		qns.put(new QName("when", namespaceURI, prefix), When.class);

		qns.put(new QName("copy-of", namespaceURI, prefix), CopyOf.class);
		qns.put(new QName("namespace", namespaceURI, prefix), Namespace.class);
		qns.put(new QName("number", namespaceURI, prefix), org.exist.xslt.expression.Number.class);
		qns.put(new QName("output-character", namespaceURI, prefix), OutputCharacter.class);
		qns.put(new QName("processing-instruction", namespaceURI, prefix), ProcessingInstruction.class);
		qns.put(new QName("sort", namespaceURI, prefix), Sort.class);
		qns.put(new QName("with-param", namespaceURI, prefix), WithParam.class);

	}
}
