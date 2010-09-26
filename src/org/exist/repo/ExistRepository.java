/****************************************************************************/
/*  File:       ExistRepository.java                                        */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2010-09-22                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2010 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package org.exist.repo;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import org.exist.xquery.InternalModule;
import org.exist.xquery.Module;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.Repository;
import org.expath.pkg.repo.URISpace;

/**
 * TODO: ...
 *
 * @author Florent Georges
 * @date   2010-09-22
 */
public class ExistRepository
{
    public ExistRepository(File root)
            throws PackageException
    {
        this(new Repository(root));
    }

    public ExistRepository(Repository parent)
            throws PackageException
    {
        myParent = parent;
        parent.registerExtension(new ExistPkgExtension());
    }

    public Module resolveJavaModule(String namespace, XQueryContext ctxt)
            throws XPathException
    {
        for ( Package pkg : myParent.listPackages() ) {
            ExistPkgInfo info = (ExistPkgInfo) pkg.getInfo("exist");
            if ( info != null ) {
                String clazz = info.getJava(namespace);
                if ( clazz != null ) {
                    try {
                        Class c = Class.forName(clazz);
                        InternalModule im = (InternalModule) c.newInstance();
                        String im_ns = im.getNamespaceURI();
                        if ( ! im_ns.equals(namespace) ) {
                            throw new XPathException("The namespace in the Java module does not match the namespace in the package descriptor: " + namespace + " - " + im_ns);
                        }
                        return ctxt.loadBuiltInModule(namespace, clazz);
                    }
                    catch ( ClassNotFoundException ex ) {
                        throw new XPathException("Cannot find module from expath repository, but it should be there.", ex);
                    }
                    catch ( InstantiationException ex ) {
                        throw new XPathException("Problem instantiating module from expath repository.", ex);
                    }
                    catch ( IllegalAccessException ex ) {
                        throw new XPathException("Cannot access expath repository directory", ex);
                    }
                    catch ( ClassCastException ex ) {
                        throw new XPathException("Problem casting module from expath repository.", ex);
                    }
                }
            }
        }
        return null;
    }

    public File resolveXQueryModule(String namespace)
            throws XPathException
    {
        for ( Package pkg : myParent.listPackages() ) {
            ExistPkgInfo info = (ExistPkgInfo) pkg.getInfo("exist");
            if ( info != null ) {
                File f = info.getXQuery(namespace);
                if ( f != null ) {
                    return f;
                }
            }
            // TODO: Should we really build URI objects?  Shouldn't the EXPath
            // repository use plain strings instead?
            try {
                File f = pkg.resolveFile(new URI(namespace), URISpace.XQUERY);
                if ( f != null ) {
                    return f;
                }
            }
            catch ( URISyntaxException ex ) {
                throw new XPathException("Namespace URI is not correct URI", ex);
            }
        }
        return null;
    }

    /** The wrapped EXPath repository. */
    private Repository myParent;
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
