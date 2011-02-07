package org.exist.util.serializer.json;

import java.io.IOException;
import java.io.Writer;

public class JSONValue extends JSONNode {
	
	public final static String NAME_VALUE = "#text";
	
	private String content;
	
	public JSONValue(String content) {
		super(Type.VALUE_TYPE, NAME_VALUE);
		this.content = escape(content);
	}

	@Override
	public void serialize(Writer writer, boolean isRoot) throws IOException {
		serializeContent(writer);		
	}

	@Override
	public void serializeContent(Writer writer) throws IOException {
		if (getSerializationType() == SerializationType.AS_STRING)
			writer.write('"');
		JSONNode next = this;
		while (next != null) {
			
			writer.write(content);
			next = next.getNextOfSame();
		}
		if (getSerializationType() == SerializationType.AS_STRING)
			writer.write('"');
	}
	
	protected static String escape(String str) {
		StringBuilder builder = null;
		for (int i = 0; i < str.length(); i++) {
			char ch = str.charAt(i);
			switch (ch) {
			case '\n':
				builder = copy(str, builder, i);
				builder.append("\\n");
				break;
			case '"':
				builder = copy(str, builder, i);
				builder.append("\\\"");
				break;
			}
		}
		return builder == null ? str : builder.toString();
	}

	private static StringBuilder copy(String str, StringBuilder builder, int i) {
		if (builder == null) {
			builder = new StringBuilder(str.length());
			builder.append(str.substring(0, i));
		}
		return builder;
	}
}