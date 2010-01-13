xquery version "1.0";

declare namespace mods="http://www.loc.gov/mods/v3";

declare variable $local:COLLECTION := "collection('/db')//";

declare variable $local:FIELDS :=
	<fields>
		<field name="Title">mods:mods/mods:titleInfo</field>
		<field name="Author">mods:mods/mods:name</field>
		<field name="All">mods:mods</field>
	</fields>;

declare function local:nodeset() {
    let $field := request:get-parameter("field", "All")
    let $query := $local:FIELDS/field[@name = $field]/string()
    let $found :=
        util:eval(concat($local:COLLECTION, $query))
    let $log := util:log("DEBUG", (count($found)))
    return
        $found
};

declare function local:key($key, $options) {
    $key
};

let $term := request:get-parameter("q", ())
let $log := util:log("DEBUG", ("Q=", $term))
let $callback := util:function(xs:QName("local:key"), 2)
return
    distinct-values(util:index-keys(local:nodeset(), $term, $callback, 20, "lucene-index"))