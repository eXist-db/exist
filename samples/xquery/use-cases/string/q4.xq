
declare namespace my="my.uri";
declare function my:partners($company as xs:string) as element()*
{
    let $c := doc("company-data.xml")//company[name = $company]
    return $c//partner
};

for $item in doc("news.xml")//news_item,
    $c in doc("company-data.xml")//company
let $partners := my:partners($c/name)
where contains(string($item), $c/name)
  and (some $p in $partners satisfies
    contains(string($item), $p) and $item/news_agent != $c/name)
return
    $item
