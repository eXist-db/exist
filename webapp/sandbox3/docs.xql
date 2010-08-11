xquery version "1.0";

<ul>
{
    let $prefix := request:get-parameter("prefix", ())
    let $funcs := util:registered-functions()
    let $matches := for $func in $funcs where matches($func, concat("^(\w+:)?", $prefix)) return $func
    for $func in $matches
    let $desc := util:describe-function($func)
    order by $func
    return
        for $proto in $desc/prototype
        return
            <li>{$proto/signature/string()}</li>
}
</ul>