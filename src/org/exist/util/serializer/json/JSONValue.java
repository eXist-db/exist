package org.exist.util.serializer.json;

import java.io.IOException;
import java.io.Writer;

public class JSONValue extends JSONNode {
	
	public final static String NAME_VALUE = "#text";
	
	private String content;
	
	public JSONValue(String content) {
		super(Type.VALUE_TYPE, NAME_VALUE);
		this.content = content;
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
}
