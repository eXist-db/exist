let $highbid := max(for $z in doc("bids.xml")//bid_tuple/bid return number($z))
return
    <result>
     {
        for $item in doc("items.xml")//item_tuple,
            $b in doc("bids.xml")//bid_tuple[itemno = $item/itemno]
        where $b/bid = $highbid
        return
            <expensive_item>
                { $item/itemno }
                { $item/description }
                <high_bid>{ $highbid }</high_bid>
            </expensive_item>
     }
    </result>