xquery version "1.0";

import module namespace xproc = "http://xproc.net/xproc";

declare variable $pipeline as item() external;
declare variable $stdin    as item() external;
declare variable $debug    as item() external;
declare variable $bindings as item() external;
declare variable $options  as item() external;
declare variable $autobind  external;

let $requestparams :=
if($autobind eq '1') then
for $binding in request:get-parameter-names()
return
   if($binding eq 'stdin' or $binding eq 'debug' or $binding eq 'autobind') then
     ()
   else
  <binding port="{$binding}">
     {util:parse(request:get-parameter($binding,''))}
  </binding>

else
 ()
let $xprocbindings := <bindings>
                        {$requestparams}
                        {util:parse($bindings)//binding}
                    </bindings>

return
    xproc:run( doc($pipeline), doc($stdin), $debug, "0", $xprocbindings, util:parse($options))
