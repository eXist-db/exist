let $allbikes := doc("items.xml")//item_tuple
                    [contains(description, "Bicycle") 
                     or contains(description, "Tricycle")]
let $bikebids := doc("bids.xml")//bid_tuple[itemno = $allbikes/itemno]
return
    <high_bid>
      { 
        max(for $z in $bikebids/bid return number($z)) 
      }
    </high_bid>