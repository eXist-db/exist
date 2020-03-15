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

import module namespace xproc = "http://xproc.net/xproc";

declare variable $pipeline external;
declare variable $stdin    external;
declare variable $debug    external;
declare variable $bindings external;
declare variable $options  external;
declare variable $autobind external;

let $requestparams := if($autobind eq '1') then
        for $binding in request:get-parameter-names()
        return
           if($binding eq 'stdin' or $binding eq 'debug' or $binding eq 'autobind') then
             ()
           else
          <binding port="{$binding}">
             {parse-xml(request:get-parameter($binding,''))}
          </binding>
    else
        ()
let $xprocbindings := <bindings>
                        {$requestparams}
                        {parse-xml($bindings)//binding}
                    </bindings>
return
xproc:run( doc($pipeline), doc($stdin), $debug, "0", $xprocbindings, parse-xml($options))
