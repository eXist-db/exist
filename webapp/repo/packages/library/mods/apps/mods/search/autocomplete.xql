xquery version "1.0";

declare namespace mods="http://www.loc.gov/mods/v3";

declare option exist:serialize "media-type=text/json";

declare variable $local:COLLECTION := '/db';

declare variable $local:FIELDS :=
	<fields>
		<field name="Title">mods:titleInfo</field>
		<field name="Author">mods:name</field>
	</fields>;

declare function local:key($key, $options) {
    concat('"', $key, '"')
};

let $term := request:get-parameter("term", ())
let $field := request:get-parameter("field", "All")
let $qnames :=
    if ($field eq "All") then
        for $field in $local:FIELDS/field return xs:QName($field/string())
    else
        xs:QName($local:FIELDS/field[@name = $field]/string())
let $callback := util:function(xs:QName("local:key"), 2)
return
    concat("[",
        string-join(
            collection($local:COLLECTION)/util:index-keys-by-qname($qnames, $term, $callback, 20, "lucene-index"),
            ', '
        ),
        "]")