/****************************************************************************/
/*  File:       ExistPkgInfo.java                                           */
/*  Author:     F. Georges - H2O Consulting                                 */
/*  Date:       2010-09-21                                                  */
/*  Tags:                                                                   */
/*      Copyright (c) 2010 Florent Georges (see end of file.)               */
/* ------------------------------------------------------------------------ */


package org.exist.repo;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.PackageInfo;

/**
 * TODO: To be moved into eXist code base...
 *
 * @author Florent Georges
 * @date   2010-09-21
 */
public class ExistPkgInfo
        extends PackageInfo
{
    public ExistPkgInfo(Package pkg)
    {
        super("exist", pkg);
    }

    // TODO: resolve(), etc...

    public Set<File> getJars() {
        return myJars;
    }
    public String getJava(String namespace) {
        return myJava.get(namespace);
    }
    public File getXQuery(String namespace) {
        return myXquery.get(namespace);
    }

    public void addJar(File jar) {
        myJars.add(jar);
    }
    public void addJava(String uri, String fun) {
        myJava.put(uri, fun);
    }
    public void addXQuery(String uri, File file) {
        myXquery.put(uri, file);
    }

    private Set<File>        myJars = new HashSet<File>();
    private Map<String, String> myJava = new HashMap<String, String>();
    private Map<String, File>   myXquery = new HashMap<String, File>();
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
