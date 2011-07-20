(:
 :  eXide - web-based XQuery IDE
 :  
 :  Copyright (C) 2011 Wolfgang Meier
 :
 :  This program is free software: you can redistribute it and/or modify
 :  it under the terms of the GNU General Public License as published by
 :  the Free Software Foundation, either version 3 of the License, or
 :  (at your option) any later version.
 :
 :  This program is distributed in the hope that it will be useful,
 :  but WITHOUT ANY WARRANTY; without even the implied warranty of
 :  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 :  GNU General Public License for more details.
 :
 :  You should have received a copy of the GNU General Public License
 :  along with this program.  If not, see <http://www.gnu.org/licenses/>.
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