/*
 * eXist-db Open Source Native XML Database
 * Copyright (C) 2001 The eXist-db Authors
 *
 * info@exist-db.org
 * http://www.exist-db.org
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */
package org.exist.repo;

import org.expath.pkg.repo.DescriptorExtension;
import org.expath.pkg.repo.FileSystemStorage.FileSystemResolver;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.Repository;
import org.expath.pkg.repo.Storage.NotExistException;
import org.expath.pkg.repo.parser.XMLStreamHelper;

import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handle the exist.xml descriptor in an EXPath package.
 *
 * @author Florent Georges - H2O Consulting
 * @since 2009-09-21
 */
public class ExistPkgExtension
        extends DescriptorExtension {
    public ExistPkgExtension() {
        super("exist", "exist.xml");
    }

    @Override
    protected void parseDescriptor(XMLStreamReader parser, Package pkg)
            throws PackageException {
        myXSHelper.ensureNextElement(parser, "package");
        final ExistPkgInfo info = new ExistPkgInfo(pkg);
        try {
            parser.next();
            while (parser.getEventType() == XMLStreamConstants.START_ELEMENT) {
                if (EXIST_PKG_NS.equals(parser.getNamespaceURI())) {
                    handleElement(parser, pkg, info);
                } else {
                    // ignore elements not in the eXist Pkg namespace
                    // TODO: FIXME: Actually ignore (pass it.)
                    throw new PackageException("TODO: Ignore elements in other namespace");
                }
                parser.next();
            }
            // position to </package>
            parser.next();
        } catch (final XMLStreamException ex) {
            throw new PackageException("Error reading the exist descriptor", ex);
        }
        pkg.addInfo(getName(), info);
        // if the package has never been installed, install it now
        // TODO: This is not an ideal solution, but this should work in most of
        // the cases, and does not need xrepo to depend on any processor-specific
        // stuff.  We need to find a proper way to make that at the real install
        // phase though (during the "xrepo install").
        if (!info.getJars().isEmpty()) {
            try {
                pkg.getResolver().resolveResource(".exist/classpath.txt");
            } catch (final NotExistException ex) {
                setupPackage(pkg, info);
            }
        }
    }

    private void handleElement(XMLStreamReader parser, Package pkg, ExistPkgInfo info)
            throws PackageException, XMLStreamException {
        final String local = parser.getLocalName();
        switch (local) {
            case "jar" -> {
                final String jar = myXSHelper.getElementValue(parser);
                info.addJar(jar);
            }
            case "java" -> handleJava(parser, info);
            case "xquery" -> handleXQuery(parser, pkg, info);
            case null, default -> throw new PackageException("Unknown eXist component type: " + local);
        }
    }

    private void handleJava(XMLStreamReader parser, ExistPkgInfo info)
            throws PackageException
            , XMLStreamException {
        myXSHelper.ensureNextElement(parser, "namespace");
        final String href = myXSHelper.getElementValue(parser);
        myXSHelper.ensureNextElement(parser, "class");
        final String clazz = myXSHelper.getElementValue(parser);
        // position to </java>
        parser.next();
        try {
            info.addJava(new URI(href), clazz);
        } catch (final URISyntaxException ex) {
            throw new PackageException("Invalid URI: " + href, ex);
        }
    }

    private void handleXQuery(XMLStreamReader parser, Package pkg, ExistPkgInfo info)
            throws PackageException
            , XMLStreamException {
        if (!myXSHelper.isNextElement(parser, "import-uri")) {
            myXSHelper.ensureElement(parser, "namespace");
        }
        final String href = myXSHelper.getElementValue(parser);
        myXSHelper.ensureNextElement(parser, "file");
        final String file = myXSHelper.getElementValue(parser);
        // position to </xquery>
        parser.next();
        try {
            info.addXQuery(new URI(href), file);
        } catch (final URISyntaxException ex) {
            throw new PackageException("Invalid URI: " + href, ex);
        }
    }

    // TODO: Must not be here (in the parsing class).  See the comment at the
    // end of parseDescriptor().
    private void setupPackage(Package pkg, ExistPkgInfo info)
            throws PackageException {
        // TODO: FIXME: Bad, BAD design!  But will be resolved naturally by moving the
        // install code within the storage class (because we are writing on disk)...
        final FileSystemResolver res = (FileSystemResolver) pkg.getResolver();
        final Path classpath = res.resolveResourceAsFile(".exist/classpath.txt");

        // create [pkg_dir]/.exist/classpath.txt if not already
        final Path exist = classpath.getParent();
        if (!Files.exists(exist)) {
            try {
                Files.createDirectories(exist);
            } catch (final IOException e) {
                throw new PackageException("Impossible to create directory: " + exist);
            }
        }

        try(final Writer out = Files.newBufferedWriter(classpath)) {
            for (final String jar : info.getJars()) {

                try {
                    res.resolveComponent(jar);
                } catch (final NotExistException ex) {
                    final String msg = "Inconsistent package descriptor, the JAR file is not in the EXPath package: ";
                    throw new PackageException(msg + jar, ex);
                }

                out.write(jar);
                out.write('\n');
            }
        } catch (final IOException ex) {
            throw new PackageException("Error writing the eXist classpath file '" + classpath + "' for the EXPath package: " + pkg.getName(), ex);
        }
    }

    public static final String EXIST_PKG_NS = "http://exist-db.org/ns/expath-pkg";
    private XMLStreamHelper myXSHelper = new XMLStreamHelper(EXIST_PKG_NS);

    @Override
    public void install(Repository repository, Package pkg) throws PackageException {
        init(repository, pkg);
    }
}
