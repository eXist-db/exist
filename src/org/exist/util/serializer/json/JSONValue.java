/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2011-2012 The eXist Project
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
 *  
 *  $Id$
 */
package org.exist.util.serializer.json;

import java.io.IOException;
import java.io.Writer;

public class JSONValue extends JSONNode {
	
	public final static String NAME_VALUE = "#text";
	
	private String content = null;

    public JSONValue(String content) {
        this(content, true);
    }

	public JSONValue(String content, boolean escape) {
		super(Type.VALUE_TYPE, NAME_VALUE);
        if (escape)
            {this.content =  escape(content);}
        else
            {this.content = content;}
	}

	public JSONValue() {
		super(Type.VALUE_TYPE, NAME_VALUE);
	}

	public void addContent(String str) {
		if (content == null)
			{content = str;}
		else
			{content += str;}
	}
	
	@Override
	public void serialize(Writer writer, boolean isRoot) throws IOException {
		if (getNextOfSame() != null) {
			writer.write("[");
			JSONNode next = this;
			while (next != null) {
				next.serializeContent(writer);
				next = next.getNextOfSame();
				if (next != null)
					{writer.write(", ");}
			}
			writer.write("]");
		} else
			{serializeContent(writer);}		
	}

	@Override
	public void serializeContent(Writer writer) throws IOException {
		if (getSerializationType() != SerializationType.AS_LITERAL)
			{writer.write('"');}
		writer.write(content);
		if (getSerializationType() != SerializationType.AS_LITERAL)
			{writer.write('"');}
	}
	
	protected static String escape(String str) {
		final StringBuilder builder = new StringBuilder();
		for (int i = 0; i < str.length(); i++) {
			final char ch = str.charAt(i);
			switch (ch) {
			case '\n':
				builder.append("\\n");
				break;
			case '\r':
				break;
			case '\t':
				builder.append("\\t");
				break;
			case '"':
				builder.append("\\\"");
				break;
			case '\\':
				builder.append("\\\\");
				break;
			default:
				builder.append(ch);
				break;
			}
		}
		return builder.toString();
	}
}