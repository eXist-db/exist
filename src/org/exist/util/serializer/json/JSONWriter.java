package org.exist.util.serializer.json;

import java.io.IOException;
import java.io.Writer;
import java.util.Properties;
import java.util.Stack;

import javax.xml.transform.TransformerException;

import org.exist.dom.QName;
import org.exist.storage.serializers.EXistOutputKeys;
import org.exist.util.serializer.XMLWriter;

/**
 * This class plugs into eXist's serialization to transform XML to JSON. It is used
 * if the serialization property "method" is set to "json".
 * 
 * The following rules apply for the mapping of XML to JSON:
 * 
 * <ul>
 * 	<li>The root element will be absorbed, i.e. &lt;root&gt;text&lt;/root&gt; becomes "root".</li>
 * 	<li>Sibling elements with the same name are added to an array.</li>
 * 	<li>If an element has attribute and text content, the text content becomes a
 *      property, e.g. '#text': 'my text'.</li>
 *	<li>In mixed content nodes, text nodes will be dropped.</li>
 *	<li>An empty element becomes 'null', i.e. &lt;e/&gt; becomes {"e": null}.</li>
 *	<li>An element with a single text child becomes a property with the value of the text child, i.e.
 *      &lt;e&gt;text&lt;/e&gt; becomes {"e": "text"}<li>
 *  <li>An element with name "json:value" is serialized as a simple value, not an object, i.e.
 *  	&lt;json:value&gt;value&lt;/json:value&gt; just becomes "value".</li>
 * </ul>
 * 
 * Namespace prefixes will be dropped from element and attribute names by default. If the serialization
 * property {@link EXistOutputKeys#JSON_OUTPUT_NS_PREFIX} is set to "yes", namespace prefixes will be
 * added to the resulting JSON property names, replacing the ":" with a "_", i.e. &lt;foo:node&gt; becomes
 * "foo_node".
 * 
 * If an attribute json:array is present on an element it will always be serialized as an array, even if there
 * are no other sibling elements with the same name.
 * 
 * The attribute json:literal indicates that the element's text content should be serialized literally. This is
 * handy for writing boolean or numeric values. By default, text content is serialized as a Javascript string.
 *  
 * @author wolf
 *
 */
public class JSONWriter extends XMLWriter {
	
	public final static String JASON_NS = "http://www.json.org";
	
	protected JSONNode root;
	
	protected Stack<JSONObject> stack = new Stack<JSONObject>();

	protected boolean useNSPrefix = false;
	
	public JSONWriter() {
		// empty
	}

	public JSONWriter(Writer writer) {
		super(writer);
	}

	@Override
	protected void reset() { 
		super.reset();
		stack.clear();
		root = null;
	}

	@Override
	public void setOutputProperties(Properties properties) {
		super.setOutputProperties(properties);
		String useNSProp = properties.getProperty(EXistOutputKeys.JSON_OUTPUT_NS_PREFIX, "no");
		useNSPrefix = useNSProp.equalsIgnoreCase("yes");
	}

	@Override
	public void startDocument() throws TransformerException {
	}

	@Override
	public void endDocument() throws TransformerException {
		try {
			if (root != null)
				root.serialize(writer, true);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void startElement(String qname) throws TransformerException {
		if (qname.equals("json:value"))
			processStartValue();
		else if (useNSPrefix)
			processStartElement(qname.replace(':', '_'), false);
		else
			processStartElement(QName.extractLocalName(qname), false);
	}

	@Override
	public void startElement(QName qname) throws TransformerException {
		if (JASON_NS.equals(qname.getNamespaceURI()) && "value".equals(qname.getLocalName()))
			processStartValue();
		else if (useNSPrefix)
			processStartElement(qname.getPrefix() + '_' + qname.getLocalName(), false);
		else
			processStartElement(qname.getLocalName(), false);
	}

	private void processStartElement(String localName, boolean simpleValue) {
		JSONObject obj = new JSONObject(localName);
		if (root == null) {
			root = obj;
			stack.push(obj);
		} else {
			JSONObject parent = stack.peek();
			parent.addObject(obj);
			stack.push(obj);
		}
	}
	
	private void processStartValue() throws TransformerException {
		// a json:value is stored as an unnamed object
		JSONObject obj = new JSONObject();
		if (root == null) {
			root = obj;
			stack.push(obj);
		} else {
			JSONObject parent = stack.peek();
			parent.addObject(obj);
			stack.push(obj);
		}
	}
	
	@Override
	public void endElement(String qname) throws TransformerException {
		stack.pop();
	}

	@Override
	public void endElement(QName qname) throws TransformerException {
		stack.pop();
	}

	@Override
	public void namespace(String prefix, String nsURI)
			throws TransformerException {
	}

	@Override
	public void attribute(String qname, String value)
			throws TransformerException {
		JSONObject parent = stack.peek();
		if (qname.equals("json:array")) {
			parent.setSerializationType(JSONNode.SerializationType.AS_ARRAY);
		} else if (qname.equals("json:literal")) {
			parent.setSerializationType(JSONNode.SerializationType.AS_LITERAL);
		} else {
			JSONSimpleProperty obj = new JSONSimpleProperty(qname, value);
			parent.addObject(obj);
		}
	}

	@Override
	public void attribute(QName qname, String value)
			throws TransformerException {
		attribute(qname.toString(), value);
	}

	@Override
	public void characters(CharSequence chars) throws TransformerException {
		JSONObject parent = stack.peek();
		JSONNode value = new JSONValue(chars.toString());
		value.setSerializationType(parent.getSerializationType());
		parent.addObject(value);
	}

	@Override
	public void characters(char[] ch, int start, int len)
			throws TransformerException {
		characters(new String(ch, start, len));
	}

	@Override
	public void processingInstruction(String target, String data)
			throws TransformerException {
		// skip
	}

	@Override
	public void comment(CharSequence data) throws TransformerException {
		// skip
	}

	@Override
	public void cdataSection(char[] ch, int start, int len)
			throws TransformerException {
		// treat as string content
		characters(ch, start, len);
	}

	@Override
	public void documentType(String name, String publicId, String systemId)
			throws TransformerException {
		// skip
	}
}
