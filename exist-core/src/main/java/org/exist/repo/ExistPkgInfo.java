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

import org.expath.pkg.repo.Package;
import org.expath.pkg.repo.PackageException;
import org.expath.pkg.repo.PackageInfo;
import org.expath.pkg.repo.URISpace;

import javax.xml.transform.stream.StreamSource;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The extended package info, dedicated to eXist.
 *
 * @author Florent Georges - H2O Consulting
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

    private Set<String>      myJars = new HashSet<>();
    private Map<URI, String> myJava = new HashMap<>();
    private Map<URI, String> myXquery = new HashMap<>();
}
