package org.exist.util.serializer.json;

import java.io.IOException;
import java.io.Writer;

public abstract class JSONNode {

	public static enum SerializationType { AS_STRING, AS_ARRAY, AS_LITERAL };
	
	public static enum Type { OBJECT_TYPE, VALUE_TYPE, SIMPLE_PROPERTY_TYPE };
	
	private Type type;
	private String name;
	private SerializationType writeAs = SerializationType.AS_STRING;
	
	private JSONNode next = null;
	private JSONNode nextOfSame = null;
	
	public JSONNode(Type type, String name) {
		this.type = type;
		this.name = name;
	}
	
	public abstract void serialize(Writer writer, boolean isRoot) throws IOException;
	
	public abstract void serializeContent(Writer writer) throws IOException;
	
	public Type getType() {
		return type;
	}
	
	public SerializationType getSerializationType() {
		return writeAs;
	}
	
	public void setSerializationType(SerializationType type) {
		writeAs = type;
	}
	
	public JSONNode getNextOfSame() {
		return nextOfSame;
	}
	
	public void setNextOfSame(JSONNode nextOfSame) {
		this.nextOfSame = nextOfSame;
	}

	public void setNext(JSONNode next) {
		this.next = next;
	}
	
	public JSONNode getNext() {
		return next;
	}
	
	public String getName() {
		return name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
}