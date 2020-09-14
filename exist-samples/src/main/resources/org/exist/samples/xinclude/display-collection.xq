(:
 : eXist-db Open Source Native XML Database
 : Copyright (C) 2001 The eXist-db Authors
 :
 : info@exist-db.org
 : http://www.exist-db.org
 :
 : This library is free software; you can redistribute it and/or
 : modify it under the terms of the GNU Lesser General Public
 : License as published by the Free Software Foundation; either
 : version 2.1 of the License, or (at your option) any later version.
 :
 : This library is distributed in the hope that it will be useful,
 : but WITHOUT ANY WARRANTY; without even the implied warranty of
 : MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 : Lesser General Public License for more details.
 :
 : You should have received a copy of the GNU Lesser General Public
 : License along with this library; if not, write to the Free Software
 : Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 :)
xquery version "1.0";

declare namespace xdb="http://exist-db.org/xquery/xmldb";
declare namespace util="http://exist-db.org/xquery/util";

declare namespace display="display-collection";

declare function display:display-collection($colName as xs:string) as element()*
{
    <collection 
        name="{util:collection-name($colName)}"
        owner="{xdb:get-owner($colName)}"
        group="{xdb:get-group($colName)}"
        permissions="{xdb:permissions-to-string(xdb:get-permissions($colName))}"
    >
    {
        for $child in xdb:get-child-collections($colName)
        let $childCol := concat($colName, "/", $child)
        return
            display:display-collection($childCol)
    }
    {
        for $res in xdb:get-child-resources($colName)
        return
            display:display-resource($colName, $res)
    }
    </collection>
};

declare function display:display-resource($colName, $resource)
as element()* {
    <resource 
        name="{$resource}"
        owner="{xdb:get-owner($colName, $resource)}"
        group="{xdb:get-group($colName, $resource)}"
        permissions="{xdb:permissions-to-string(xdb:get-permissions($colName, $resource))}"/>
};


display:display-collection($xinclude:current-collection)