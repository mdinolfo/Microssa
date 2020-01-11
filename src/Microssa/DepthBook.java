/*
 * DepthBook.java
 *
 * Copyright (C) 2015 Michael Dinolfo
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package Microssa;

import java.util.*;

/**
 * This class implements a modified SkipList to keep track of a single
 * side (Bid or Offer) of OrderIDs for a Symbol, and their relative
 * order to each other based on Price and arrival order: first in, first
 * out.
 */
public class DepthBook {

    /**
     * Represents the single skip layer where there is one SkipNode
     * per price point.  Each skip node points to a PriceNode, which is
     * the oldest OrderID at that Price point.  Prev should be null
     * or a SkipNode with a better Price, and Next should be null or a
     * Skipnode with a worse price.
     */
    class SkipNode {
        public SkipNode Prev;
        public SkipNode Next;
        public PriceNode Lower;
        public Double Price;

        /**
         * Sets references to null and Price to zero.
         */
        public SkipNode ()
        {
            Next = Prev = null;
            Lower = null;
            Price = 0D;
        }

        /**
         * Prints the Price.  Used by the DepthBook function PrintNodes.
         *
         */
        public void Print ()
        {
            System.out.println ( "SN " + Price );
        }
    }

    /**
     * Represents an OrderID's place in the one side of the market.
     * Prev should null, or a PriceNode that is the same Price but arrived
     * earlier, or a PriceNode with a better price.  Next should be null,
     * or a PriceNode that is the same Price but arrived later, or a
     * PriceNode with a worse Price.
     */
    class PriceNode {

        public PriceNode Prev;
        public PriceNode Next;
        public Double Price;
        public String OrderID;

        /**
         * Sets references to null, Price to zero, and OrderID to a blank
         * string.
         */
        public PriceNode ()
        {
            Next = Prev = null;
            Price = 0D;
            OrderID = "";
        }

        /**
         * Prints the Price.  Used by the DepthBook function PrintNodes.
         *
         */
        public void Print ()
        {
            System.out.println ( "-->PN " + OrderID + " " + Price );
        }
    }

    /** Reference to the first PriceNode in our book. */
    private PriceNode PriceHead;

    /** Reference to the first SkipNode in our book. */
    private SkipNode SkipHead;

    /**
     * Sets the Direction: false is descending prices (Buy/Bid book)
     * and true is ascending prices (Sell/Offer book).
     */
    private Boolean Direction;

    /** Last price node found by Match, used by MatchNext. */
    private PriceNode LastPriceNode;

    /** Last skip node found by Match, used by MatchNext. */
    private SkipNode LastSkipNode;

    /**
     * Initializes the PriceNode and SkipNode head references to null.
     * Sets the Direction.
     *
     * @param d The sort direction of the prices
     */
    public DepthBook ( Boolean d )
    {
        PriceHead = null;
        SkipHead = null;
        LastSkipNode = null;
        LastPriceNode = null;
        Direction = d;
    }

    /**
     * Add a PriceNode with the matching Price and OrderID.  PriceNodes
     * are added FIFO, and by Price order determined by Direction (false
     * is descending and true is ascending.  Will also add a
     * SkipNode for that Price if it does not exist.
     *
     * @param Price The Order's price
     * @param OrderID The Order's ID
     */
    public void AddOrder ( Double Price, String OrderID )
    {
        SkipNode SkipPrevPtr = null;
        SkipNode SkipPtr = SkipHead;

        PriceNode TempPtr = null;
        PriceNode Ptr = null;

        while ( SkipPtr != null && PriceMatch( Price, SkipPtr.Price ) )
        {
            SkipPrevPtr = SkipPtr;
            SkipPtr = SkipPtr.Next;
        }

        if ( SkipPtr == null )
        {
            // we reached the end of the skip list
            SkipPtr = new SkipNode();
            SkipPtr.Price = Price;

            if ( SkipPrevPtr == null )
            {
                // this is a completly new list
                SkipHead = SkipPtr;
            }
            else
            {
                // we're at the end of existing list
                SkipPrevPtr.Next = SkipPtr;
                SkipPtr.Prev = SkipPrevPtr;
            }

        }
        else
        {
            // at the beginning or inside of the list
            if ( Double.compare(Price, SkipPtr.Price) == 0 )
            {
                // we found a skip node with our price, so start our
                // order pointer there
                Ptr = SkipPtr.Lower;
            }
            else
            {
                // Didn't find our price point, make a new skip node
                // and connect it

                SkipNode TempSkipPtr = new SkipNode();
                TempSkipPtr.Price = Price;

                SkipPtr.Prev = TempSkipPtr;
                TempSkipPtr.Next = SkipPtr;

                if ( SkipPrevPtr == null )
                {
                    // beginning of list
                    SkipHead = TempSkipPtr;
                }
                else
                {
                    // inside of list
                    SkipPrevPtr.Next = TempSkipPtr;
                    TempSkipPtr.Prev = SkipPrevPtr;
                }

                SkipPtr = TempSkipPtr;
            }
        }

        if ( Ptr == null )
        {
            // new price so we need to connect the list
            Boolean connected = false;

            Ptr = new PriceNode();
            Ptr.Price = Price;
            Ptr.OrderID = OrderID;

            SkipPtr.Lower = Ptr;

            if ( SkipPrevPtr == null )
            {
                // head of list
                PriceHead = Ptr;

                if ( SkipPtr.Next != null ) {
                    TempPtr = SkipPtr.Next.Lower;

                    TempPtr.Prev = Ptr;
                    Ptr.Next = TempPtr;
                }

            }
            else
            {
                // middle or end
                TempPtr = SkipPrevPtr.Lower;
                Double TempPrice = TempPtr.Price;

                while ( TempPtr.Next != null && Double.compare( TempPtr.Next.Price, TempPrice ) == 0 )
                    TempPtr = TempPtr.Next;

                if ( TempPtr.Next != null )
                {
                    // middle of list
                    TempPtr.Next.Prev = Ptr;
                    Ptr.Next = TempPtr.Next;
                }

                TempPtr.Next = Ptr;
                Ptr.Prev = TempPtr;
            }
        }
        else
        {
            // price found, append new order to end of matching prices
            TempPtr = Ptr;

            Ptr = new PriceNode();
            Ptr.Price = Price;
            Ptr.OrderID = OrderID;

            while ( TempPtr.Next != null && Double.compare( TempPtr.Next.Price, Price ) == 0 )
                TempPtr = TempPtr.Next;

            if ( TempPtr.Next != null )
            {
                // middle of list
                TempPtr.Next.Prev = Ptr;
                Ptr.Next = TempPtr.Next;
            }

            // middle or end
            TempPtr.Next = Ptr;
            Ptr.Prev = TempPtr;

        }

    }

    /**
     * Removes the PriceNode with matching Price and OrderID.  Will
     * remove the SkipNode above if there are no more PriceNodes
     * underneath it.
     *
     * @param Price The Order's price
     * @param OrderID The Order's ID
     */
    public void RemoveOrder ( Double Price, String OrderID )
    {
        SkipNode SkipPtr = SkipHead;
        PriceNode Ptr = null;

        while ( SkipPtr != null && Double.compare( SkipPtr.Price, Price ) != 0 )
            SkipPtr = SkipPtr.Next;

        if ( SkipPtr != null )
        {
            // found a matching skip node
            Ptr = SkipPtr.Lower;

            while ( Ptr != null && Double.compare( Ptr.Price, Price ) == 0 &&
                    !Ptr.OrderID.equals(OrderID) )
                Ptr = Ptr.Next;

            if ( Ptr != null && Double.compare( Ptr.Price, Price ) == 0 ) {
                // found a matching price node
                PriceNode TempPrev = Ptr.Prev;
                PriceNode TempNext = Ptr.Next;

                if ( TempPrev != null && TempNext != null )
                {
                    // nodes before and after
                    TempPrev.Next = TempNext;
                    TempNext.Prev = TempPrev;
                }
                else if ( TempNext != null )
                {
                    // only a node after
                    TempNext.Prev = null;

                    if ( PriceHead.OrderID.equals( Ptr.OrderID ) )
                    {
                        PriceHead = TempNext;
                    }
                }
                else if ( TempPrev != null )
                {
                    // only a node before
                    TempPrev.Next = null;
                }

                if ( PriceHead.OrderID.equals( Ptr.OrderID ) )
                {
                    if ( TempNext != null )
                        PriceHead = TempNext;
                    else
                        PriceHead = null;
                }

                if ( SkipPtr.Lower.OrderID.equals( Ptr.OrderID ) )
                {
                    // we deleted the price lower, possibly remove skip
                    if ( TempNext != null && Double.compare ( TempNext.Price, Price ) == 0 )
                    {
                        // next node price match, make it lower
                        SkipPtr.Lower = TempNext;
                    }
                    else
                    {
                        // no matching price nodes, delete skip node
                        SkipNode TempSkipPrev = SkipPtr.Prev;
                        SkipNode TempSkipNext = SkipPtr.Next;

                        if ( TempSkipPrev != null && TempSkipNext != null )
                        {
                            // nodes before and after
                            TempSkipPrev.Next = TempSkipNext;
                            TempSkipNext.Prev = TempSkipPrev;
                        }
                        else if ( TempSkipNext != null )
                        {
                            // only a node after
                            TempSkipNext.Prev = null;

                            if ( Double.compare( SkipHead.Price, SkipPtr.Price ) == 0 )
                            {
                                SkipHead = TempSkipNext;
                            }
                        }
                        else if ( TempSkipPrev != null )
                        {
                            // only a node before
                            TempSkipPrev.Next = null;
                        }

                        if ( Double.compare( SkipHead.Price, SkipPtr.Price ) == 0 )
                        {
                            if ( TempSkipNext != null)
                                SkipHead = TempSkipNext;
                            else
                                SkipHead = null;
                        }

                    }
                }

            }
        }

    }

    /**
     * Determines if the book is empty.
     *
     * @return True if there are no SkipNodes, false otherwise
     */
     public Boolean IsEmpty () {
         return ( SkipHead == null );
     }

    /**
     * Finds an OrderID of an order who's price is able to match.
     * Will return a match that can qualify for price improvement if
     * Exact is false, identical match if Exact is true, a or blank
     * string if no such order qualifies.
     *
     * @param Price The price-point the calling function is looking to
     * match on
     * @param Exact If true, return matches with the exact
     * same Price.  If false, return matches that would qualify for
     * price improvement.  Meaning, buy price is greater than sellers'
     * price or sell price is less than buyers'.
     * @return OrderID of valid match, blank otherwise
     */
    public String Match ( Double Price, Boolean Exact )
    {
        SkipNode SkipPtr = SkipHead;

        while ( SkipPtr != null && ( Double.compare( SkipPtr.Price, Price ) != 0 &&
            ( !PriceMatch( Price, SkipPtr.Price ) || Exact ) ) ){
            SkipPtr = SkipPtr.Next;
        }

        if ( SkipPtr != null )
        {
            LastSkipNode = SkipPtr;
            LastPriceNode = SkipPtr.Lower;
            return LastPriceNode.OrderID;
        }
        
        LastSkipNode = null;
        LastPriceNode = null;

        return "";
    }

    /**
     * As the function Match, but continues where the search finished.
     *
     * @param Price The price-point the calling function is looking to
     * match on
     * @param Exact If true, return matches with the exact
     * same Price.  If false, return matches that would qualify for
     * price improvement.  Meaning, buy price is greater than sellers'
     * price or sell price is less than buyers'.
     * @return OrderID of valid match, blank otherwise
     */
    public String MatchNext ( Double Price, Boolean Exact )
    {
        if ( LastPriceNode == null || LastSkipNode == null )
            return "";
        
        if ( LastPriceNode.Next != null ) {
            LastPriceNode = LastPriceNode.Next;
            return LastPriceNode.OrderID;
        }
        
        SkipNode SkipPtr = LastSkipNode.Next;

        while ( SkipPtr != null && ( Double.compare( SkipPtr.Price, Price ) != 0 &&
            ( !PriceMatch( Price, SkipPtr.Price ) || Exact ) ) ){
            SkipPtr = SkipPtr.Next;
        }

        if ( SkipPtr != null )
        {
            LastSkipNode = SkipPtr;
            LastPriceNode = SkipPtr.Lower;
            return LastPriceNode.OrderID;
        }

        LastSkipNode = null;
        LastPriceNode = null;

        return "";
    }

    /**
     * Gets a list of all OrderIDs in the DepthBook.  This will be used
     * by the MatchingEngine to provide market data.
     *
     * @return String list of all OrderIDs
     */
    public List getOrderIDs ()
    {
        List<String> OrderIDs = new ArrayList<>();
        PriceNode Ptr = PriceHead;

        while (Ptr != null)
        {
            OrderIDs.add( Ptr.OrderID );
            Ptr = Ptr.Next;
        }

        return OrderIDs;
    }

    /**
     * Prints a list of SkipNodes and their PriceNodes underneath to
     * standard out.  This is provided as a diagnostic tool for use
     * during development cycles.
     */
    public void PrintNodes ()
    {
        SkipNode SkipPtr = SkipHead;
        PriceNode Ptr = PriceHead;

        Double Price;

        while ( SkipPtr != null )
        {
            Ptr = SkipPtr.Lower;
            Price = Ptr.Price;

            SkipPtr.Print();

            while ( Ptr != null && Double.compare( Price, Ptr.Price) == 0 )
            {
                Ptr.Print();
                Ptr = Ptr.Next;
            }

            SkipPtr = SkipPtr.Next;
        }
    }

    /**
     * Price comparitor configured on whether the list is sorted
     * ascending or descending.
     *
     * @param Price Target price
     * @param NodePrice Contra price
     * @return True if Price is before NodePrice, false if it is after
     */
    private Boolean PriceMatch ( Double Price, Double NodePrice )
    {
        if ( Direction )
            return ( Double.compare( Price, NodePrice ) > 0 );
        else
            return ( Double.compare( Price, NodePrice ) < 0 );
    }


}

