package org.exist.util.serializer.json;

import java.io.IOException;
import java.io.Writer;

/**
 * Used to serialize attribute nodes, which are written as a simple
 * "property": "value" pair.
 * 
 * @author wolf
 *
 */
public class JSONSimpleProperty extends JSONNode {

	private String value;

	public JSONSimpleProperty(String name, String value) {
		super(Type.SIMPLE_PROPERTY_TYPE, name);
		this.value = JSONValue.escape(value);
	}

	@Override
	public void serialize(Writer writer, boolean isRoot) throws IOException {
		writer.write('"');
		writer.write(getName());
		writer.write("\" : \"");
		writer.write(value);
		writer.write('"');
	}

	@Override
	public void serializeContent(Writer writer) throws IOException {
	}

}
