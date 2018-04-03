/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2011 The eXist Project
 *  http://exist.sourceforge.net
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package org.exist.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import com.evolvedbinary.j8fu.Either;
import com.evolvedbinary.j8fu.function.SupplierE;
import org.xml.sax.InputSource;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;


/**
 * This class extends {@link org.xml.sax.InputSource}, so
 * it also manages {@link java.io.File} and
 * {@link org.exist.util.VirtualTempFile} as input sources.
 * 
 * @author jmfernandez
 *
 */
public class VirtualTempFileInputSource extends EXistInputSource {
	private final static Logger LOG = LogManager.getLogger(VirtualTempFileInputSource.class);

	private Optional<Either<Path, VirtualTempFile>> file = Optional.empty();

	public VirtualTempFileInputSource(final VirtualTempFile vtempFile) throws IOException {
		this(vtempFile, null);
	}
	
	public VirtualTempFileInputSource(final VirtualTempFile vtempFile, final String encoding) throws IOException {
		// Temp file must be immutable from this point
		vtempFile.close();

		this.file = Optional.of(Either.Right(vtempFile));
		if(encoding != null) {
			super.setEncoding(encoding);
		}
		
		if(vtempFile.tempFile != null) {
			super.setSystemId(vtempFile.tempFile.toUri().toASCIIString());
		}
	}
	
	public VirtualTempFileInputSource(final Path file) {
		this(file,null);
	}
	
	public VirtualTempFileInputSource(final Path file, final String encoding) {
		this.file = Optional.ofNullable(file).map(Either::Left);
		
		if(encoding != null) {
			super.setEncoding(encoding);
		}

		if(file != null) {
			super.setSystemId(file.toUri().toASCIIString());
		}
	}

	/**
	 * @see InputSource#getByteStream()
	 *
	 * @throws IllegalStateException if the InputSource was previously closed
	 */
	@Override
	public InputStream getByteStream() {
		assertOpen();
		return file
				.flatMap(f -> f.fold(this::newInputStream, this::vtfByteStream))
				.orElse(null);
	}

	private Optional<Reader> inputStreamReader(final InputStream is, final String encoding) {
		return Optional
				.ofNullable(encoding)
				.flatMap(e -> Optional.ofNullable(is).flatMap(i -> {
					try {
						return Optional.of(new InputStreamReader(i, e));
					} catch(final IOException ioe) {
						LOG.error(ioe);
						return Optional.empty();
					}
				}));
	}

	/**
	 * @see InputSource#getCharacterStream()
	 *
	 * @throws IllegalStateException if the InputSource was previously closed
	 */
	@Override
	public Reader getCharacterStream() {
		assertOpen();
		return inputStreamReader(getByteStream(), getEncoding()).orElse(null);
	}
	
    /**
	 * This method now does nothing, so collateral
	 * effects from superclass with this one are avoided
	 *
	 * @throws IllegalStateException if the InputSource was previously closed
	 */
	@Override
	public void setByteStream(final InputStream is) {
		assertOpen();
		// Nothing, so collateral effects are avoided!
	}
	
	/**
	 * This method now does nothing, so collateral
	 * effects from superclass with this one are avoided
	 *
	 * @throws IllegalStateException if the InputSource was previously closed
	 */
	@Override
	public void setCharacterStream(final Reader r) {
		assertOpen();
		// Nothing, so collateral effects are avoided!
	}

	/**
	 * This method now does nothing, so collateral
	 * effects from superclass with this one are avoided
	 *
	 * @throws IllegalStateException if the InputSource was previously closed
	 */
	@Override
	public void setSystemId(final String systemId) {
		assertOpen();
		// Nothing, so collateral effects are avoided!
	}

	/**
	 * @see EXistInputSource#getSymbolicPath()
	 *
	 * @throws IllegalStateException if the InputSource was previously closed
	 */
	@Override
	public String getSymbolicPath() {
		assertOpen();
		return file
				.flatMap(f -> f.fold(l -> Optional.of(l.toAbsolutePath().toString()), r -> Optional.ofNullable(r.tempFile).map(Path::toAbsolutePath).map(Path::toString)))
				.orElse(null);
	}

	/**
	 * @see EXistInputSource#getByteStreamLength()
	 *
	 * @throws IllegalStateException if the InputSource was previously closed
	 */
	@Override
	public long getByteStreamLength() {
		assertOpen();
		return file.flatMap(f -> f.fold(this::fileSize, this::vtfSize)).orElse(-1l);
	}

	@Override
	protected void finalize() throws Throwable {
		try {
			close();
		} finally {
			super.finalize();
		}
	}

	@Override
	public void close() {
		if(!isClosed()) {
			try {
				file.ifPresent(f -> f.fold(l -> true, VirtualTempFile::delete));
				file = Optional.empty();
			} finally {
				super.close();
			}
		}
	}

	private Optional<InputStream> newInputStream(final Path path) {
		return safeIO(() -> new BufferedInputStream(Files.newInputStream(path)));
	}

	private Optional<InputStream> vtfByteStream(final VirtualTempFile vtf) {
		return safeIO(() -> vtf.getByteStream());
	}

	private Optional<Long> fileSize(final Path path) {
		return safeIO(() -> Files.size(path));
	}

	private <T> Optional<T> safeIO(final SupplierE<T, IOException> isSource) {
		try {
			return Optional.of(isSource.get());
		} catch(final IOException e) {
			LOG.error(e);
			return Optional.empty();
		}
	}

	private Optional<Long> vtfSize(final VirtualTempFile vtf) {
		return Optional.of(vtf.length());
	}
}
