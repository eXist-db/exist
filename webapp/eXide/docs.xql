(:
 :  eXist Open Source Native XML Database
 :  Copyright (C) 2011 The eXist Project
 :  http://exist-db.org
 :
 :  This program is free software; you can redistribute it and/or
 :  modify it under the terms of the GNU Lesser General Public License
 :  as published by the Free Software Foundation; either version 2
 :  of the License, or (at your option) any later version.
 :
 :  This program is distributed in the hope that it will be useful,
 :  but WITHOUT ANY WARRANTY; without even the implied warranty of
 :  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 :  GNU Lesser General Public License for more details.
 :
 :  You should have received a copy of the GNU Lesser General Public
 :  License along with this library; if not, write to the Free Software
 :  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 :
 :  $Id$
 :)
xquery version "1.0";

declare option exist:serialize "method=json media-type=application/json";

<functions xmlns:json="http://json.org/">
{
    let $prefix := request:get-parameter("prefix", ())
    let $funcs := util:registered-functions()
    let $matches := for $func in $funcs where matches($func, concat("^(\w+:)?", $prefix)) return $func
    for $func in $matches
    let $desc := util:describe-function($func)
    order by $func
    return
        for $proto in $desc/prototype
        let $help := $proto/description/string()
        return
            <json:value json:array="true">
                <signature>{$proto/signature/string()}</signature>
                <help>{$help}</help>
                <type>function</type>
            </json:value>
}
</functions>