/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-06 Wolfgang M. Meier
 *  wolfgang@exist-db.org
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
 *  
 *  $Id$
 */
package org.exist.xquery;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.exist.TestUtils;
import org.exist.test.ExistXmldbEmbeddedServer;
import org.exist.util.FileUtils;
import org.exist.util.XMLFilenameFilter;
import org.exist.xmldb.EXistXQueryService;

import org.junit.Before;
import org.junit.ClassRule;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author Wolfgang Meier (wolfgang@exist-db.org)
 */
public class XQueryUseCase {

	@ClassRule
	public static final ExistXmldbEmbeddedServer existEmbeddedServer = new ExistXmldbEmbeddedServer(false, true);

	private final static Path baseDir = TestUtils.resolveSample("xquery/use-cases");

	private Collection root = null;

	@Before
	protected void setUp() throws Exception {
		root = existEmbeddedServer.getRoot();
	}

	public void doTest(String useCase) throws Exception {
		CollectionManagementService service =
			(CollectionManagementService) root.getService(
				"CollectionManagementService",
				"1.0");
		root = service.createCollection(useCase);

		final Path file = baseDir.resolve(useCase);
		if (!(Files.isReadable(file) && Files.isDirectory(file))) {
			throw new RuntimeException("Cannot read data for use-case " + useCase);
		}
		setupData(file);
		processQueries(file);
	}

	protected void processQueries(final Path file) throws Exception {
		final List<Path> paths = FileUtils.list(file, f -> {
			final String fileName = FileUtils.fileName(f);
			return fileName.startsWith("q") && fileName.endsWith(".xq");
		});

		for(final Path path : paths) {
			System.out.println("processing use-case: " + path.toAbsolutePath());
			System.out.println("========================================================================");
			String query = readQuery(path);
			System.out.println(query);
			System.out.println("_________________________________________________________________________________");
			EXistXQueryService service = (EXistXQueryService)root.getService("XQueryService", "1.0");
			ResourceSet results;
			try {
				results = service.query(query);
				for(int j = 0; j < results.getSize(); j++) {
					String output = (String)results.getResource(j).getContent();
					System.out.println(output);
				}
			} catch (Exception e) {
				Throwable cause = e.getCause();
				if ( cause == null )
					cause = e;
				System.err.println( "Exception: " + e.getClass() + " - "+ cause );
				for (int j = 0; j < 4; j++) {
					StackTraceElement el = cause.getStackTrace()[j];
					System.err.println( el );
				}
				e.getStackTrace();
				// rethrow for JUnit reporting
				throw e;
			}
			System.out.println("========================================================================");
		}
	}
	
	protected void setupData(final Path file) throws Exception {
		final List<Path> paths = FileUtils.list(file, XMLFilenameFilter.asPredicate());
		for (final Path path : paths) {
			final XMLResource res =
				(XMLResource) root.createResource(FileUtils.fileName(path), XMLResource.RESOURCE_TYPE);
			res.setContent(path);
			root.storeResource(res);
		}
	}
	
	protected String readQuery(final Path f) throws IOException {
		return new String(Files.readAllBytes(f), UTF_8);
	}
}
