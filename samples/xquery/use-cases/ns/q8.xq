declare namespace ma = "http://www.example.com/AuctionWatch";

<Q8>
  {
    for $s in doc("auction.xml")//ma:Trading_Partners/(ma:Seller | ma:High_Bidder)
    where $s/*:NegativeComments = 0
    return $s
  }
</Q8>
