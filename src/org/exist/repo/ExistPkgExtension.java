/****************************************************************************/
/*  File:       ExistPkgExtension.java                                      */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2010-09-21                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2010 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package org.exist.repo;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.expath.pkg.repo.DescriptorExtension;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.Repository;
import org.expath.pkg.repo.XMLStreamHelper;

/**
 * TODO: To be moved into eXist code base...
 *
 * @author Florent Georges
 * @date   2010-09-21
 */
public class ExistPkgExtension
        extends DescriptorExtension
{
    public ExistPkgExtension()
    {
        super("exist.xml");
    }

    @Override
    protected void parseDescriptor(XMLStreamReader parser, Package pkg)
            throws PackageException
    {
        myXSHelper.ensureNextElement(parser, "package");
        ExistPkgInfo info = new ExistPkgInfo();
        try {
            parser.next();
            while ( parser.getEventType() == XMLStreamConstants.START_ELEMENT ) {
                if ( Repository.PKG_NS.equals(parser.getNamespaceURI()) ) {
                    handleElement(parser, pkg, info);
                }
                else {
                    // ignore elements not in the EXPath Pkg namespace
                    // TODO: FIXME: Actually ignore (pass it.)
                    throw new PackageException("TODO: Ignore elements in other namespace");
                }
                parser.next();
            }
            // position to </exist>
            parser.next();
        }
        catch ( XMLStreamException ex ) {
            throw new PackageException("Error reading the package descriptor", ex);
        }
        pkg.addInfo("exist", info);
        // if the package has never been installed, install it now
        // TODO: This is not an ideal solution, but this should work in most of
        // the cases, and does not need xrepo to depend on any processor-specific
        // stuff.  We need to find a proper way to make that at the real install
        // phase though (during the "xrepo install").
        if ( ! info.getJars().isEmpty() ) {
            if ( ! new File(pkg.getRootDir(), ".exist/classpath.txt").exists() ) {
                setupPackage(pkg, info);
            }
        }
    }

    private void handleElement(XMLStreamReader parser, Package pkg, ExistPkgInfo info)
            throws PackageException, XMLStreamException
    {
        String local = parser.getLocalName();
        if ( "jar".equals(local) ) {
            String jar = myXSHelper.getElementValue(parser);
            info.addJar(new File(pkg.getModuleDir(), jar));
        }
        else if ( "java".equals(local) ) {
            handleJava(parser, info);
        }
        else if ( "xquery".equals(local) ) {
            handleXQuery(parser, pkg, info);
        }
        else {
            throw new PackageException("Unknown eXist component type: " + local);
        }
    }

    private void handleJava(XMLStreamReader parser, ExistPkgInfo info)
            throws PackageException
                 , XMLStreamException
    {
        myXSHelper.ensureNextElement(parser, "namespace");
        String href = myXSHelper.getElementValue(parser);
        myXSHelper.ensureNextElement(parser, "class");
        String clazz = myXSHelper.getElementValue(parser);
        // position to </java>
        parser.next();
        info.addJava(href, clazz);
    }

    private void handleXQuery(XMLStreamReader parser, Package pkg, ExistPkgInfo info)
            throws PackageException
                 , XMLStreamException
    {
        if ( ! myXSHelper.isNextElement(parser, "import-uri") ) {
            myXSHelper.ensureElement(parser, "namespace");
        }
        String href = myXSHelper.getElementValue(parser);
        myXSHelper.ensureNextElement(parser, "file");
        String file = myXSHelper.getElementValue(parser);
        // position to </xquery>
        parser.next();
        info.addXQuery(href, new File(pkg.getModuleDir(), file));
    }

    // TODO: Must not be here (in the parsing class).  See the comment at the
    // end of parseDescriptor().
    private void setupPackage(Package pkg, ExistPkgInfo info)
            throws PackageException
    {
        // create [pkg_dir]/.exist/classpath.txt
        File exist = new File(pkg.getRootDir(), ".exist/");
        if ( ! exist.exists() && ! exist.mkdir() ) {
            throw new PackageException("Impossible to create directory: " + exist);
        }
        File target = new File(exist, "classpath.txt");
        Set<File> jars = info.getJars();
        try {
            FileWriter out = new FileWriter(target);
            for ( File jar : jars ) {
                out.write(jar.getCanonicalPath());
                out.write("\n");
            }
            out.close();
        }
        catch ( IOException ex ) {
            throw new PackageException("Error writing the eXist classpath file: " + target, ex);
        }
    }

    public static final String EXIST_PKG_NS = "http://exist.org/ns/expath-pkg";
    private XMLStreamHelper myXSHelper = new XMLStreamHelper(EXIST_PKG_NS);
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
