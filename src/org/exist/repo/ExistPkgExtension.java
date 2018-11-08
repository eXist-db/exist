/****************************************************************************/
/*  File:       ExistPkgExtension.java                                      */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2010-09-21                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2010 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package org.exist.repo;

import java.io.IOException;
import java.io.Writer;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.stream.StreamSource;

import org.expath.pkg.repo.DescriptorExtension;
import org.expath.pkg.repo.FileSystemStorage.FileSystemResolver;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.Repository;
import org.expath.pkg.repo.Storage.NotExistException;
import org.expath.pkg.repo.parser.XMLStreamHelper;

/**
 * Handle the exist.xml descriptor in an EXPath package.
 *
 * @author Florent Georges
 * @since 2010-09-21
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
        if ("jar".equals(local)) {
            final String jar = myXSHelper.getElementValue(parser);
            info.addJar(jar);
        } else if ("java".equals(local)) {
            handleJava(parser, info);
        } else if ("xquery".equals(local)) {
            handleXQuery(parser, pkg, info);
        } else {
            throw new PackageException("Unknown eXist component type: " + local);
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
        final Set<String> jars = info.getJars();
        try(final Writer out = Files.newBufferedWriter(classpath)) {
            for (final String jar : jars) {
                StreamSource jar_src;
                try {
                    jar_src = res.resolveComponent(jar);
                } catch (final NotExistException ex) {
                    final String msg = "Inconsistent package descriptor, the JAR file is not in the package: ";
                    throw new PackageException(msg + jar, ex);
                }
                final URI uri = URI.create(jar_src.getSystemId());
                final Path file = Paths.get(uri);
                out.write(file.normalize().toString());
                out.write("\n");
            }
        } catch (final IOException ex) {
            throw new PackageException("Error writing the eXist classpath file: " + classpath, ex);
        }
    }

    public static final String EXIST_PKG_NS = "http://exist-db.org/ns/expath-pkg";
    private XMLStreamHelper myXSHelper = new XMLStreamHelper(EXIST_PKG_NS);

    @Override
    public void install(Repository repository, Package pkg) throws PackageException {
        init(repository, pkg);
    }
}


/* ------------------------------------------------------------------------ */
/*  DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS COMMENT.               */
/*                                                                          */
/*  The contents of this file are subject to the Mozilla Public License     */
/*  Version 1.0 (the "License"); you may not use this file except in        */
/*  compliance with the License. You may obtain a copy of the License at    */
/*  http://www.mozilla.org/MPL/.                                            */
/*                                                                          */
/*  Software distributed under the License is distributed on an "AS IS"     */
/*  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied.  See    */
/*  the License for the specific language governing rights and limitations  */
/*  under the License.                                                      */
/*                                                                          */
/*  The Original Code is: all this file.                                    */
/*                                                                          */
/*  The Initial Developer of the Original Code is Florent Georges.          */
/*                                                                          */
/*  Contributor(s): none.                                                   */
/* ------------------------------------------------------------------------ */
