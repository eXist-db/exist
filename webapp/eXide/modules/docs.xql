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

declare namespace xqdoc="http://www.xqdoc.org/1.0";
declare namespace json="http://json.org/";

declare option exist:serialize "method=json media-type=application/json";

declare function local:builtin-modules($xqdocs as element()*) {
    let $prefix := request:get-parameter("prefix", ())
    let $funcs := util:registered-functions()
    let $matches := for $func in $funcs where matches($func, concat("^(\w+:)?", $prefix)) return $func
    for $func in $matches
    let $desc := util:describe-function($func)
    order by $func
    return
        for $proto in $desc/prototype
        let $signature := $proto/signature/string()
        let $help := $proto/description/string()
        let $xqdoc := $xqdocs/json:value[signature = $signature]
        return
            if (exists($xqdoc)) then
                $xqdoc
            else
                <json:value json:array="true">
                    <signature>{$signature}</signature>
                    <help>{$help}</help>
                    <type>function</type>
                    <visibility>
                    {
                        if ($proto/annotation[@name="private"]) then
                            "private"
                        else
                            "public"
                    }
                    </visibility>
                </json:value>
};

declare function local:xqdoc-modules() {
    let $prefix := request:get-parameter("prefix", ())
    for $fun in collection("/db")//xqdoc:function[matches(xqdoc:signature, concat("^(\w+:)?", $prefix))]
    order by $fun/xqdoc:signature
    return
        <json:value json:array="true">
            <signature>{$fun/xqdoc:signature/string()}</signature>
            <help>{$fun/xqdoc:comment/xqdoc:description/node()}</help>
            <type>function</type>
        </json:value>
};

let $xqdocs := local:xqdoc-modules()
return
    <functions xmlns:json="http://json.org/">
    {
        local:builtin-modules($xqdocs)
    }
    </functions>