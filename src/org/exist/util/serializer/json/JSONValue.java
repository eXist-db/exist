package org.exist.util.serializer.json;

import java.io.IOException;
import java.io.Writer;

public class JSONValue extends JSONNode {
	
	public final static String NAME_VALUE = "#text";
	
	private String content = null;
	
	public JSONValue(String content) {
		super(Type.VALUE_TYPE, NAME_VALUE);
		this.content = escape(content);
	}

	public JSONValue() {
		super(Type.VALUE_TYPE, NAME_VALUE);
	}

	public void addContent(String str) {
		if (content == null)
			content = str;
		else
			content += str;
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
					writer.write(", ");
			}
			writer.write("]");
		} else
			serializeContent(writer);		
	}

	@Override
	public void serializeContent(Writer writer) throws IOException {
		if (getSerializationType() != SerializationType.AS_LITERAL)
			writer.write('"');
		writer.write(content);
		if (getSerializationType() != SerializationType.AS_LITERAL)
			writer.write('"');
	}
	
	protected static String escape(String str) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < str.length(); i++) {
			char ch = str.charAt(i);
			switch (ch) {
			case '\n':
				builder.append("\\n");
				break;
			case '\r':
				break;
			case '"':
				builder.append("\\\"");
				break;
			default:
				builder.append(ch);
				break;
			}
		}
		return builder.toString();
	}
}