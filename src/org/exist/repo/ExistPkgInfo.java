/****************************************************************************/
/*  File:       ExistPkgInfo.java                                           */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2010-09-21                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2010 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package org.exist.repo;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.transform.stream.StreamSource;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.PackageInfo;
import org.expath.pkg.repo.URISpace;

/**
 * The extended package info, dedicated to eXist.
 *
 * @author Florent Georges
 * @since  2010-09-21
 */
public class ExistPkgInfo
        extends PackageInfo
{
    public ExistPkgInfo(Package pkg)
    {
        super("exist", pkg);
    }

    @Override
    public StreamSource resolve(String href, URISpace space)
            throws PackageException
    {
        // TODO: Really?  Probably to refactor in accordance with ExistRepository...
        return null;
    }

    public Set<String> getJars() {
        return myJars;
    }
    public String getJava(URI namespace) {
        return myJava.get(namespace);
    }
    public String getXQuery(URI namespace) {
        return myXquery.get(namespace);
    }

    public Set<URI> getJavaModules() {
        return myJava.keySet();
    }

    public void addJar(String jar) {
        myJars.add(jar);
    }
    public void addJava(URI uri, String fun) {
        myJava.put(uri, fun);
    }
    public void addXQuery(URI uri, String file) {
        myXquery.put(uri, file);
    }

    private Set<String>      myJars = new HashSet<String>();
    private Map<URI, String> myJava = new HashMap<URI, String>();
    private Map<URI, String> myXquery = new HashMap<URI, String>();
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
