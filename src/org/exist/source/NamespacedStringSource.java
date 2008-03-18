package org.exist.source;

import java.io.*;
import java.util.Map;

import org.exist.storage.DBBroker;

/**
 * A simple source object wrapping a single query string, but associating it with a specific
 * set of namespace bindings.  This prevents two textually equal queries with different
 * namespaces from getting aliased in the query pool.
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class NamespacedStringSource extends AbstractSource {
	private final Map<String, String> map;

	/**
	 * Create a new source for the given content and namespace map (string to string).
	 * The map will be taken over and modified by the source, so make a copy first if
	 * you're passing a shared one.
	 *
	 * @param content the content of the query
	 * @param namespaceMap the map of prefixes to namespace URIs
	 */
	public NamespacedStringSource(String content, Map<String, String> namespaceMap) {
		map = namespaceMap;
		map.put("<query>", content);
	}

	public Object getKey() {return map;}
	public int isValid(DBBroker broker) {return Source.VALID;}
	public int isValid(Source other) {return Source.VALID;}
	public Reader getReader() throws IOException {return new StringReader(map.get("<query>"));}
	public String getContent() throws IOException {return map.get("<query>");}
}