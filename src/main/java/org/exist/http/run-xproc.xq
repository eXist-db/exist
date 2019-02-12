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
