xquery version "1.0";
import module namespace style = "http://exist-db.org/mods-style" at "../../../modules/style.xqm";
declare option exist:serialize "method=xml media-type=text/xml indent=yes";

let $code-table-collection := concat($style:web-path-to-app, '/code-tables/')

return

<code-tables>
   <collection>{$code-table-collection}</collection>
   
   (:A:)
   {doc(concat($code-table-collection, 'abbreviated-title-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'access-condition-type-codes.xml'))/code-table}
   
   (:B:)
   {doc(concat($code-table-collection, 'bgtchm-genre-codes.xml'))/code-table}
   
   (:C:)
   {doc(concat($code-table-collection, 'classification-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'code-text-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'continent-codes.xml'))/code-table}
   
   (:D:)
   {doc(concat($code-table-collection, 'date-encoding-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'date-keydate-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'date-point-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'date-qualifier-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'dct-resource-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'description-standard-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'digital-origin-type-codes.xml'))/code-table}
   
   (:E:)
   
   {doc(concat($code-table-collection, 'element-attribute-namess.xml'))/code-table}
   (:?:)
   
   (:F:)
   
   {doc(concat($code-table-collection, 'form-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'frequency-codes.xml'))/code-table}
   
   (:G:)
   {doc(concat($code-table-collection, 'genre-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'genre-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'genre-marcgt-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'genre-marcsmd-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'genre-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'geographic-code-authority-codes.xml'))/code-table}
   
   (:H:)
   
   (:I:)
   {doc(concat($code-table-collection, 'identifier-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'internet-media-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'iso3166-country-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'issuance-codes.xml'))/code-table}
   
   (:J:)
   
   (:K:)
   
   (:L:)
   {doc(concat($code-table-collection, 'language-2-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'language-3-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'language-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'location-unittype-codes.xml'))/code-table}
   
   (:M:)
   {doc(concat($code-table-collection, 'marc-country-codes.xml'))/code-table}
   
   (:N:)
   {doc(concat($code-table-collection, 'name-part-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'name-role-relator-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'name-title-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'name-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'note-type-codes.xml'))/code-table}
   
   (:O:)
   
   (:P:)
   {doc(concat($code-table-collection, 'part-date-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'part-date-qualifier-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'part-extent-unit-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'physical-location-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'place-authority-codes.xml'))/code-table}
   
   (:Q:)
   
   (:R:)
   {doc(concat($code-table-collection, 'recordcontentsource-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'reformatting-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'related-item-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'role-long-codes.xml'))/code-table}
   
   (:S:)
   {doc(concat($code-table-collection, 'script-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'start-end-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'subject-authority-codes.xml'))/code-table}
   
   (:T:)
   {doc(concat($code-table-collection, 'target-audience-marctarget-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'target-audience-local-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'target-audience-authority-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'temporal-encoding-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'title-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'transliteration-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'type-of-resource-codes.xml'))/code-table}
   
   (:U:)
   {doc(concat($code-table-collection, 'url-access-type-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'url-usage-type-codes.xml'))/code-table}
   
   (:V:)
   
   (:X:)
   
   (:Y:)
   {doc(concat($code-table-collection, 'yes-empty-codes.xml'))/code-table}
   {doc(concat($code-table-collection, 'yes-no-codes.xml'))/code-table}
   
   (:Z:)
   
</code-tables>