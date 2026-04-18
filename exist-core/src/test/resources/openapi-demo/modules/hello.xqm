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
xquery version "3.1";

(:~
 : Demo handler module for OpenAPI-native routing.
 :
 : No Roaster import. No controller.xql. Just a plain XQuery module
 : with functions that accept a $request map and return data.
 :
 : The module namespace URI follows the convention:
 :   http://exist-db.org/apps/{prefix}
 : where {prefix} matches the operationId prefix in api.json.
 :)
module namespace hello = "http://exist-db.org/apps/hello";

declare namespace output = "http://www.w3.org/2010/xslt-xquery-serialization";
declare option output:method "json";
declare option output:media-type "application/json";

(:~
 : Simple greeting.
 : GET /api/hello
 :)
declare function hello:greet($request as map(*)) {
    map {
        "message": "Hello from eXist-db's built-in OpenAPI routing!",
        "method": $request?method,
        "path": $request?path,
        "note": "No Roaster. No controller.xql. Just controller.json + api.json + this module."
    }
};

(:~
 : Personalized greeting.
 : GET /api/hello/{name}
 :)
declare function hello:greet-name($request as map(*)) {
    let $name := $request?parameters?name
    return map {
        "message": "Hello, " || $name || "!",
        "parameters": $request?parameters
    }
};

(:~
 : Echo the request.
 : POST /api/echo
 :)
declare function hello:echo($request as map(*)) {
    map {
        "echo": true(),
        "method": $request?method,
        "body": $request?body,
        "parameters": $request?parameters
    }
};
