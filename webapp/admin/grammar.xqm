module namespace grammar="http://exist-db.org/xquery/admin-interface/grammar";

import module namespace system="http://exist-db.org/xquery/system";

declare function grammar:show-cache($cache as node()) as element()
{
    <table>
    <tr><td colspan="2"><hr/></td></tr>{
    for $grammar in $cache//grammar
    return
    (
        if (exists($grammar/Namespace)) then
            <tr> <th>Namespace</th> <td>{$grammar/Namespace}</td> </tr>
        else 
            <tr><td></td><td></td></tr>
    , 
        if (exists($grammar/LiteralSystemId)) then
            <tr> <th>LiteralSystemId</th> <td>{$grammar/LiteralSystemId}</td> </tr>
        else 
            <tr><td></td><td></td></tr>
    ,
        if (exists($grammar/ExpandedSystemId)) then
            <tr> <th>ExpandedSystemId</th> <td>{$grammar/ExpandedSystemId}</td> </tr>
        else
            <tr> <td></td> <td></td> </tr>
    ,
        if (exists($grammar/BaseSystemId)) then
            <tr> <th>BaseSystemId</th> <td>{$grammar/BaseSystemId}</td> </tr>
        else
            <tr><td></td><td></td></tr>
    ,
        <tr><td colspan="2"><hr/></td></tr>
    )
   
    }</table>
};

declare function grammar:main() as element() {
    let $action := lower-case(request:get-parameter("action", "refresh"))
    return
        <div class="panel">
            <h1>Grammar Cache</h1>
            <table id="grammar" cellpadding="7">
            {
                let $cache := validation:show-grammar-cache()
                
                return
                   if ( count($cache//grammar) > 0 ) then
                       grammar:show-cache($cache)
                   else
                       <div>No cached grammars</div>
              
            }
            </table>
        </div>
};
