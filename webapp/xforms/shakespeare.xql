xquery version "1.0";

declare namespace q="http://exist-db.org/xquery/query";

import module namespace kwic="http://exist-db.org/xquery/kwic";

declare function q:filter($node as node(), $mode as xs:string) as xs:string? {
  if ($node/parent::SPEAKER or $node/parent::STAGEDIR) then 
      ()
  else if ($mode eq 'before') then 
      concat($node, ' ')
  else 
      concat(' ', $node)
};

declare function q:createQuery($input as element()) {
    let $queryStr := $input/query/string()
    let $mode := $input/query/@mode
    return
        <query>
        {
            if ($mode eq 'any') then
                for $term in tokenize($queryStr, '\s')
                return
                    <term occur="should">{$term}</term>
            else if ($mode eq 'all') then
                for $term in tokenize($queryStr, '\s')
                return
                    <term occur="must">{$term}</term>
            else if ($mode eq 'phrase') then
                <phrase>{$input/query/@slop, $queryStr}</phrase>
            else
                <near>{$input/query/@slop, $queryStr}</near>
        }
        </query>       
};

declare function q:query($input as element()) {
    let $query := q:createQuery($input)
    let $hits :=
        for $hit in //SPEECH[ft:query(., $query)]
        order by ft:score($hit) descending
        return $hit
    let $callback := util:function(xs:QName("q:filter"), 2)
    return
        <search xmlns="">
            {$input/query}
            <hits>{count($hits)}</hits>
            {
                for $hit in subsequence($hits, 1, 20)
                return
                    kwic:summarize($hit, <config width="40"/>, $callback)
            }
        </search>
};

let $input := request:get-data()
return
    q:query($input)
    