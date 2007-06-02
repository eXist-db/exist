package org.exist.fluent;

import java.io.*;
import java.net.*;
import java.nio.CharBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.*;

import org.xml.sax.InputSource;

/**
 * A source of data to be loaded into the database, distinguishing between XML documents
 * and other (binary) documents.  While you can load XML documents as binary without
 * problem, but will not be able to query them or otherwise access their structure in the
 * database.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public abstract class Source {

	private final String oldName;
	protected String encoding;
	
	private Source(String oldName) {
		this.oldName = oldName;
	}
	
	/**
	 * Set an encoding that indicates how the supplied bytes should be converted to characters
	 * or vice-versa.  Whether and how the encoding is used depends on the source.  If the
	 * encoding is needed but has not been set, the system will make a best guess or default
	 * to UTF-8 as appropriate.
	 *
	 * @param characterEncoding the character encoding to use when dealing with this source
	 * @return this source
	 */
	public Source encoding(String characterEncoding) {
		if (encoding != null) throw new IllegalStateException("encoding already set");
		encoding = characterEncoding;
		return this;
	}
	
	final void applyOldName(Name name) {
		name.setOldName(oldName);
	}
	
	/**
	 * A source of XML data to be loaded into the database.  You should acquire instances by
	 * using the static methods in the {@link Source} class.
	 */
	public static abstract class XML extends Source {
		private XML() {super(null);}
		private XML(String oldName) {super(oldName);}
		final InputSource toInputSource() throws IOException {
			InputSource source = createInputSource();
			if (encoding != null) source.setEncoding(encoding);
			return source;
		}
		abstract InputSource createInputSource() throws IOException;
		@Override public String toString() {return "xml ";}
	}
	
	/**
	 * A source of binary data to be loaded into the database.  You should acquire instances by
	 * using the static methods in the {@link Source} class.
	 */
	public static abstract class Blob extends Source {
		private byte[] byteArray;
		private Blob() {super(null);}
		private Blob(String oldName) {super(oldName);}
		final byte[] toByteArray() throws IOException {
			if (byteArray == null) byteArray = createByteArray();
			return byteArray;
		}
		abstract byte[] createByteArray() throws IOException; 
		@Override public String toString() {return "blob ";}
		protected byte[] encode(CharBuffer buf) throws CharacterCodingException {
			Charset charset = Charset.forName(encoding == null ? "UTF-8" : encoding);
			return charset.newEncoder().encode(buf).array();
		}
	}
	
	
	/**
	 * Create a source that reads an XML document from an external file.
	 *
	 * @param file the XML file
	 * @return a source that reads XML from the file
	 */
	public static Source.XML xml(final File file) {
		final String uri = file.toURI().toASCIIString();
		return new XML(file.getName()) {
			@Override InputSource createInputSource() throws IOException {
				InputSource src = new InputSource(uri);
				if (encoding != null) src.setEncoding(encoding);
				return src;
			}
			@Override public String toString() {
				return super.toString() + "file '" + file.getPath() + "'";
			}
		};
	}
	
	/**
	 * Create a source that reads a binary document from an external file.
	 *
	 * @param file the binary file
	 * @return a source that reads binary from the file
	 */
	public static Source.Blob blob(final File file) {
		return new Blob(file.getName()) {
			@Override byte[] createByteArray() throws IOException {
				FileInputStream stream = new FileInputStream(file);
				try {
					FileChannel channel = stream.getChannel();
					FileLock lock = channel.lock(0L, Long.MAX_VALUE, true);
					try {
						long sizeL = channel.size();
						if (sizeL > Integer.MAX_VALUE) throw new IOException("file too large");
						int size = (int) sizeL;
						byte[] bytes = new byte[size];
						if (stream.read(bytes) != size) throw new IOException("read wrong number of bytes");
						return bytes;
					} finally {
						lock.release();
					}
				} finally {
					stream.close();
				}
			}
			@Override public String toString() {
				return super.toString() + "file '" + file.getPath() + "'";
			}
		};
	}
	
	/**
	 * Create a source of XML data that reads from the given input stream.  Note that the
	 * contents of the stream will need to be read <em>twice</em>, so if the stream is transient
	 * (i.e., its contents are not available for random access, such as for a socket input stream) its
	 * contents will be automatically saved in memory.  This could be very inefficient if the
	 * document being streamed is large.
	 *
	 * @param stream the input stream to read the XML from
	 * @return a source that reads from the given input stream
	 */
	public static Source.XML xml(final InputStream stream) {
		return new Source.XML() {
			private InputStream markedStream;
			@Override InputSource createInputSource() throws IOException {
				if (markedStream == null) {
					if (stream.markSupported()) {
						markedStream = stream;
					} else {
						// TODO: if stream size exceeds some threshold, save contents to a temporary file instead
						markedStream = new ByteArrayInputStream(readInputStream(stream, null));
					}
					markedStream.mark(Integer.MAX_VALUE);
				}
				markedStream.reset();
				return new InputSource(markedStream);
			}
			@Override public String toString() {
				return super.toString() + "input stream" + (markedStream == stream ? "": " (cached)");
			}
		};
	}
	
	/**
	 * Create a source of binary data that reads from the given input stream.  At this time,
	 * eXist doesn't support streaming into the database, so the contents of the stream will
	 * be read entirely into memory before being persisted.
	 *
	 * @param stream the binary stream to read from
	 * @return a source that reads from the given input stream
	 */
	public static Source.Blob blob(final InputStream stream) {
		return new Source.Blob() {
			@Override byte[] createByteArray() throws IOException {
				return readInputStream(stream, null);
			}
			@Override public String toString() {
				return super.toString() + "input stream";
			}
		};
	}
	
	/**
	 * Create a source of XML data that reads from the given reader.  Note that the
	 * contents of the reader will need to be read <em>twice</em>, so if the reader is transient
	 * (i.e., its contents are not available for random access, such as for a socket reader) its
	 * contents will be automatically saved in memory.  This could be very inefficient if the
	 * document being streamed is large.
	 *
	 * @param reader the reader to read the XML from
	 * @return a source that reads from the given reader
	 */
	public static Source.XML xml(final Reader reader) {
		return new Source.XML() {
			private Reader markedReader;
			@Override InputSource createInputSource() throws IOException {
				if (markedReader == null) {
					if (reader.markSupported()) {
						markedReader = reader;
					} else {
						// TODO: if stream size exceeds some threshold, save contents to a temporary file instead
						markedReader = new CharArrayReader(readReader(reader, null));
					}
					markedReader.mark(Integer.MAX_VALUE);
				}
				markedReader.reset();
				return new InputSource(markedReader);
			}
			@Override public String toString() {
				return super.toString() + "reader" + (markedReader == reader ? "": " (cached)");
			}
		};
	}
	
	/**
	 * Create a source of binary data that reads from the given reader.  The characters
	 * will be converted into bytes by using the encoding specified for this source, or UTF-8
	 * by default.  At this time, eXist doesn't support streaming into the database, so the
	 * contents of the reader will be read entirely into memory before being persisted.
	 *
	 * @param reader the reader to read from
	 * @return a source that reads from the given reader
	 */
	public static Source.Blob blob(final Reader reader) {
		return new Source.Blob() {
			@Override byte[] createByteArray() throws IOException {
				return encode(CharBuffer.wrap(readReader(reader, null)));
			}
			@Override public String toString() {
				return super.toString() + "reader";
			}
		};
	}
	
	/**
	 * Create a source of XML data from the given byte array.  If this source specifies an
	 * encoding, it will be used to decode the characters, otherwise the encoding will be
	 * guessed using standard XML parsing techniques.
	 *
	 * @param bytes the source bytes
	 * @return a source that reads XML from the given byte array
	 */
	public static Source.XML xml(final byte[] bytes) {
		return new Source.XML() {
			@Override InputSource createInputSource() throws IOException {
				return new InputSource(new ByteArrayInputStream(bytes));
			}
			@Override public String toString() {
				return super.toString() + "byte array [" + bytes.length + "]";
			}
		};
	}
	
	/**
	 * Create a source of binary data from the given byte array.  The bytes will be stored in
	 * the database verbatim.
	 *
	 * @param bytes the source bytes
	 * @return a source that reads binary data from the given byte array
	 */
	public static Source.Blob blob(final byte[] bytes) {
		return new Source.Blob() {
			@Override byte[] createByteArray() throws IOException {
				return bytes;
			}
			@Override public String toString() {
				return super.toString() + "byte array [" + bytes.length + "]";
			}
		};
	}
	
	/**
	 * Create a source of XML data from the given string literal.  The string should contain
	 * an XML document, not a filename or URI.
	 *
	 * @param literal the contents of an XML document
	 * @return a source that reads XML from the given literal string
	 */
	public static Source.XML xml(final String literal) {
		return new Source.XML() {
			@Override InputSource createInputSource() throws IOException {
				return new InputSource(new StringReader(literal));
			}
			@Override public String toString() {
				return super.toString() + "literal string";
			}
		};		
	}
	
	/**
	 * Create a source of binary data from the given string literal.  The string should contain
	 * the actual data, not a filename or URI.  The string's characters will be converted into
	 * bytes by using the encoding specified for this source, or UTF-8 by default.
	 *
	 * @param literal the contents of the document
	 * @return a source that reads binary data from the given literal string
	 */
	public static Source.Blob blob(final String literal) {
		return new Source.Blob() {
			@Override byte[] createByteArray() throws IOException {
				return encode(CharBuffer.wrap(literal));
			}
			@Override public String toString() {
				return super.toString() + "literal string";
			}
		};
	}
	
	/**
	 * Create a source of XML that reads from the given URL.  Note that the URL's contents
	 * will have to be retrieved twice, so if the connection is slow or expensive it might be
	 * worthwhile to cache them and use a different (local) source constructor.
	 *
	 * @param url the URL to read XML from
	 * @return a source that reads XML from the given URL
	 * @throws URISyntaxException if the URL syntax is not strictly spec-compliant
	 */
	public static Source.XML xml(URL url) throws URISyntaxException {
		final String uri = url.toURI().toASCIIString();
		return new Source.XML(urlToFilename(url)) {
			@Override InputSource createInputSource() throws IOException {
				return new InputSource(uri);
			}
			@Override public String toString() {
				return super.toString() + "at URL '" + uri + "'";
			}
		};		
	}
	
	/**
	 * Create a source of binary data that reads from the given URL.
	 *
	 * @param url the URL to read binary data from
	 * @return a source that reads binary data from the given URL
	 */
	public static Source.Blob blob(final URL url) {
		return new Source.Blob(urlToFilename(url)) {
			@Override byte[] createByteArray() throws IOException {
				URLConnection connection = url.openConnection();
				connection.setAllowUserInteraction(false);
				connection.connect();
				try {
					byte[] bytes = null;
					if (connection.getContentLength() != -1) bytes = new byte[connection.getContentLength()];
					bytes = readInputStream(connection.getInputStream(), bytes);
					return bytes;
				} finally {
					connection.getInputStream().close();
				}
			}
			@Override public String toString() {
				return super.toString() + "at URL '" + url + "'";
			}
		};
	}
	
	private static String urlToFilename(URL url) {
		String path = url.getPath();
		if (path == null || path.length() == 0) return null;
		int k = path.lastIndexOf('/');
		if (k >= 0) path = path.substring(k+1, path.length());
		return path;
	}
	
	private static final int CHUNK_SIZE = 16384;
	
	private static byte[] readInputStream(InputStream stream, byte[] chunk) throws IOException {
		PushbackInputStream in = new PushbackInputStream(stream);
		if (chunk == null) chunk = new byte[CHUNK_SIZE];
		int totalSize = 0;
		List<byte[]> chunks = new LinkedList<byte[]>();
		chunks.add(chunk);
		int k = 0;
		for(;;) {
			int n = in.read(chunk, k, chunk.length-k);
			if (n == -1) break;
			totalSize += n;
			k += n;
			if (k == chunk.length) {
				// probe next byte to avoid allocating another chunk if contents fit perfectly
				int b = in.read();
				if (b == -1) break;
				in.unread(b);
				chunk = new byte[CHUNK_SIZE];
				chunks.add(chunk);
				k = 0;
			}
		}
		if (chunks.size() == 1 && chunks.get(0).length == totalSize) return chunks.get(0);
		chunk = new byte[totalSize];
		k = 0;
		for (byte[] a : chunks) {
			System.arraycopy(a, 0, chunk, k, a.length);
			k += a.length;
		}
		return chunk;
	}
	
	private static char[] readReader(Reader reader, char[] chunk) throws IOException {
		PushbackReader in = new PushbackReader(reader);
		if (chunk == null) chunk = new char[CHUNK_SIZE];
		int totalSize = 0;
		List<char[]> chunks = new LinkedList<char[]>();
		chunks.add(chunk);
		int k = 0;
		for(;;) {
			int n = in.read(chunk, k, chunk.length-k);
			if (n == -1) break;
			totalSize += n;
			k += n;
			if (k == chunk.length) {
				// probe next byte to avoid allocating another chunk if contents fit perfectly
				int b = in.read();
				if (b == -1) break;
				in.unread(b);
				chunk = new char[CHUNK_SIZE];
				chunks.add(chunk);
				k = 0;
			}
		}
		if (chunks.size() == 1 && chunks.get(0).length == totalSize) return chunks.get(0);
		chunk = new char[totalSize];
		k = 0;
		for (char[] a : chunks) {
			System.arraycopy(a, 0, chunk, k, a.length);
			k += a.length;
		}
		return chunk;
	}
}
