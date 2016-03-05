package org.exist.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class FileInputSource extends EXistInputSource {
	private final static Logger LOG = LogManager.getLogger(FileInputSource.class);

	private Optional<Path> file = Optional.empty();
	private Optional<InputStream> inputStream = Optional.empty();
	
	/**
	 * Constructor which calls {@link #setFile(Path)}
	 * @param file
	 * The file passed to {@link #setFile(Path)}
	 */
	public FileInputSource(final Path file) {
		super();
		setFile(file);
	}
	
	/**
	 * If a file source has been set, the File
	 * object used for that is returned
	 *
	 * @return The Path object.
	 */
	public Path getFile() {
		return file.orElse(null);
	}
	
	/**
	 * This method sets the File object used to get
	 * the uncompressed stream of data
	 *
	 * @param file The Path object pointing to the file.
	 */
	public void setFile(final Path file) {
		close();
		this.file = Optional.of(file);

		// Remember: super.setSystemId must be used instead of local implementation
		super.setSystemId(this.file.map(f -> f.toUri().toASCIIString()).orElse(null));
	}
	
	/**
	 * This method was re-implemented to open a
	 * new InputStream each time it is called.
	 *
	 * @return If the file was set, and it could be opened, an InputStream object.
	 * null, otherwise.
	 */
	@Override
	public InputStream getByteStream() {
        // close any open stream first
		close();

		if(file.isPresent()) {
			try {
				this.inputStream = Optional.of(new BufferedInputStream(Files.newInputStream(file.get())));
				return inputStream.get();
			} catch(final IOException e) {
				LOG.error(e);
			}
		}

		return null;
	}

	@Override
    public void close() {
        if(inputStream.isPresent()) {
            try {
                inputStream.get().close();
            } catch (final IOException e) {
                LOG.warn(e);
            }
	        inputStream = Optional.empty();
        }
    }

    /**
	 * This method now does nothing, so collateral
	 * effects from superclass with this one are avoided 
	 */
	@Override
	public void setByteStream(final InputStream is) {
		// Nothing, so collateral effects are avoided!
	}
	
	/**
	 * This method now does nothing, so collateral
	 * effects from superclass with this one are avoided 
	 */
	@Override
	public void setCharacterStream(final Reader r) {
		// Nothing, so collateral effects are avoided!
	}

	/**
	 * This method now does nothing, so collateral
	 * effects from superclass with this one are avoided 
	 */
	@Override
	public void setSystemId(final String systemId) {
		// Nothing, so collateral effects are avoided!
	}

	@Override
	public long getByteStreamLength() {
		if(file.isPresent()) {
			try {
				return Files.size(file.get());
			} catch(final IOException e) {
				LOG.error(e);
			}
		}

		return -1;
	}

	@Override
	public String getSymbolicPath() {
		return file.map(Path::toAbsolutePath).map(Path::toString).orElse(null);
	}
}
