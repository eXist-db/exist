@REM
@REM eXist-db Open Source Native XML Database
@REM Copyright (C) 2001 The eXist-db Authors
@REM
@REM info@exist-db.org
@REM http://www.exist-db.org
@REM
@REM This library is free software; you can redistribute it and/or
@REM modify it under the terms of the GNU Lesser General Public
@REM License as published by the Free Software Foundation; either
@REM version 2.1 of the License, or (at your option) any later version.
@REM
@REM This library is distributed in the hope that it will be useful,
@REM but WITHOUT ANY WARRANTY; without even the implied warranty of
@REM MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
@REM Lesser General Public License for more details.
@REM
@REM You should have received a copy of the GNU Lesser General Public
@REM License along with this library; if not, write to the Free Software
@REM Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
@REM

set EXIST_HOME=..\..\..
java -Xmx200m -cp .\lib\hsqldb.jar org.hsqldb.util.DatabaseManagerSwing --url jdbc:hsqldb:%EXIST_HOME%\webapp\WEB-INF\data\spatial_index 
