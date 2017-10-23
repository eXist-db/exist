package org.exist.source;

import java.io.*;
import java.util.Map;

import org.exist.security.PermissionDeniedException;
import org.exist.security.Subject;
import org.exist.storage.DBBroker;

/**
 * A simple source object wrapping a single query string, but associating it with a specific
 * map (e.g., of namespace bindings).  This prevents two textually equal queries with different
 * maps from getting aliased in the query pool.
 * 
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class StringSourceWithMapKey extends AbstractSource {
	private final Map<String, String> map;

	/**
	 * Create a new source for the given content and namespace map (string to string).
	 * The map will be taken over and modified by the source, so make a copy first if
	 * you're passing a shared one.
	 *
	 * @param content the content of the query
	 * @param map the map of prefixes to namespace URIs
	 */
	public StringSourceWithMapKey(String content, Map<String, String> map) {
		this.map = map;
		this.map.put("<query>", content);
	}

	@Override
	public String path() {
		return type();
	}

	@Override
	public String type() {
		return "StringWithMapKey";
	}

	public Object getKey() {return map;}

	@Override
	public Validity isValid(final DBBroker broker) {
		return Validity.VALID;
	}

	@Override
	public Validity isValid(final Source other) {
		return Validity.VALID;
	}

	public Reader getReader() throws IOException {return new StringReader(map.get("<query>"));}

    public InputStream getInputStream() throws IOException {
        // not implemented
        return null;
    }

    public String getContent() throws IOException {return map.get("<query>");}

	@Override
	public void validate(Subject subject, int perm) throws PermissionDeniedException {
		// TODO protected?
	}
}