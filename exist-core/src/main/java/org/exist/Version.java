/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2010-2014 The eXist-db Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 */
package org.exist;

/**
 * @author <a href="mailto:shabanovd@gmail.com">Dmitriy Shabanov</a>
 *
 */
public final class Version {

	private static final String NAME;

	private static final String VERSION;
	private static final String BUILD;
	private static final String GIT_BRANCH;
	private static final String GIT_COMMIT;
	
	static {

		final SystemProperties systemProperties = SystemProperties.getInstance();
		NAME = systemProperties.getSystemProperty("product-name", "eXist");
		VERSION = systemProperties.getSystemProperty("product-version");
		BUILD = systemProperties.getSystemProperty("product-build");
		GIT_BRANCH = systemProperties.getSystemProperty("git-branch");
		GIT_COMMIT = systemProperties.getSystemProperty("git-commit");
	}
	
	public static String getProductName() {
		return NAME;
	}

	public static String getVersion() {
		return VERSION;
	}

	public static String getBuild() {
		return BUILD;
	}

	public static String getGitBranch() {
		return GIT_BRANCH;
	}

	public static String getGitCommit() {
		return GIT_COMMIT;
	}
}
