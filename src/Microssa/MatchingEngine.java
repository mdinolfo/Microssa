/*
 * MatchingEngineLibre.java
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
import java.io.IOException;
import quickfix.SessionID;

import Microssa.Order;
import Microssa.Logger;
import Microssa.DepthBook;
import Microssa.OrderSocket;
import Microssa.PriceSocket;
import Microssa.Configuration;
import Microssa.Database;
import Microssa.FIXInterface;

/**
 * This class handles order input, matching, reporting, and market data.
 * It is the central class of the Microssa project which connects all of
 * the other classes together.
 */
public class MatchingEngine {

    /**
     * Pair key used for the MasterBook.
     */
    private class Key<T1, T2> {

        private final T1 x;
        private final T2 y;

        public Key(T1 x, T2 y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Key)) return false;
            Key key = (Key) o;
            return x.equals(key.x) && y.equals(key.y);
        }

        @Override
        public int hashCode() {
            return x.hashCode() * 37 + y.hashCode();
        }

    }

    /*
     * More efficient algorithm exists, and the MEs can be
     * threaded on symbol or symbol group basis.
     * But, the goal for version 1 is to just get it to work.
     *
     * HashMap MasterBook: (Symbol, OrderID) => Order object
     * HashMap BidBook   : Symbol => DepthBook object
     * HashMap OfferBook : Symbol => DepthBook object
     *
     * We break by symbol for two reasons:
     *  1) Orders for different symbols will never match.
     *  2) Future expansion regarding multi-threading.
     *
     */

    /**
     * Master list of Orders.  Key is Symbol and OrderID pair.
     * We split all books by Symbol for two reasons: Orders for
     * different symbols will never match and, given this, we
     * may have multiple MatchingEngine threads in a future
     * release where each one handles a an exclusive subset of Symbols.
     */
    private Map<Key<String,String>, Order> MasterBook;

    /** Holds the Bid (Buy) DepthBooks.  Key is Symbol. */
    private Map<String, DepthBook> BidBook;

    /** Holds the Offer (Sell) DepthBooks.  Key is Symbol. */
    private Map<String, DepthBook> OfferBook;

    /** A set of Symbols the PriceSocket is subscribed for market data. */
    private Set<String> MDSubs;

    /** Indicates whether we should check for valid Symbols. */
    private Boolean UseValidSymbols;

    /** A set of valid Symbols for new orders. */
    private Set<String> ValidSymbols;
    
    /** Mapping of order IDs to sessions */
    private Map<String, SessionID> OrderSessionMap;

    /** Maximum allowed Order Quantity for new or amended orders. */
    private double MaxOrderQuantity;

    /** 
     * Whether the matching engine is running in dark pool mode,
     * which rejects all market data subscriptions
     */
    private boolean DarkPool;

    /**
     * A reference to an OrderSocket object so the MatchingEngine
     * may send replies.
     *
     * @see OrderSocket
     */
    private OrderSocket OS;

    /**
     * A reference to a PriceSocket object so the MatchingEngine
     * may send subscribed market data.
     *
     * @see PriceSocket
     */
    private PriceSocket PS;

    /**
     * A reference to a Database object so the MatchingEngine
     * may send matches.
     *
     * @see Database
     */
    private Database DB;

    /**
     * A reference to a FIXInterface object so the MatchingEngine
     * may communicate order and trade messages via FIX.
     *
     * @see FIXInterface
     */
    private FIXInterface FI;

    /**
     * Initializes objects, sets references to null, sends notification
     * to Logger that we are up.
     *
     * @see Logger
     * @throws IOException Passthrough from Logger write
     */
    public MatchingEngine () throws IOException
    {
        // we'll build dynamically as new symbols and bid/offers arrive
        MasterBook  = new HashMap<>();
        BidBook     = new HashMap<>();
        OfferBook   = new HashMap<>();
        OrderSessionMap = new HashMap<>();

        MDSubs      = new HashSet<>();

        OS = null;
        PS = null;
        DB = null;
        FI = null;
        
        MaxOrderQuantity = Configuration.getInstance().getDouble("MAXORDERSIZE");
        
        String VS = Configuration.getInstance().getString("VALIDSYMBOLS");
        
        if ( VS == null )
            UseValidSymbols = false;
        else
        {
            UseValidSymbols = true;
            ValidSymbols = new HashSet<>();
            
            String[] SymbolList = VS.split(" ");
            
            for ( int i = 0; i < SymbolList.length; i++ )
                ValidSymbols.add( SymbolList[i] );
        }

        if ( Configuration.getInstance().getString("DARKPOOL").equals("YES") )
        {
            DarkPool = true;
        }
        else
        {
            DarkPool = false;
        }

        try
        {
            Logger.getInstance().write("Started Microssa Matching Engine Version 1.4");
        }
        catch ( IOException e )
        {
            throw e;
        }
    }

    /**
     * This is the standard interface for entering a new order into the
     * matching engine.  The following steps are performed:
     * 1) Validate we do not have another order with the same ID.
     * 2) Report that we have a new order to Hooks, OrderSocket, and Logger.
     * 3) Attempt to match the order against previously entered, live
     * DAY orders.
     * 4) Add the order to the book if it is a DAY order and was not
     * fully executed by the previous step.
     * 5) Update the market data via PriceSocket.
     *
     * @param o The new Order
     * @throws IOException Passthrough from Logger
     * @return An error message, or blank is successful
     * @see Hooks
     * @see Logger
     * @see PriceSocket
     * @see OrderSocket
     */
    public String NewOrder ( Order o ) throws IOException
    {
		try {
			return NewOrder( o, null );
		} catch ( Exception e ) {
			throw e;
		}
	}

    public String NewOrder ( Order o, SessionID session ) throws IOException
    {
        String rejectText = "";

        Boolean proceed = true;
        
        AddOrderSessionMap( o, session );

        // First, check if we already have an order with this ID
        if ( IsOrderKnown(o) )
        {
            rejectText = "Order already exists in book";
            proceed = false;

        }
        
        // Second, check if we have a valid symbol
        if ( UseValidSymbols && !ValidSymbols.contains( o.getSymbol() ) )
        {
            rejectText = "Symbol " + o.getSymbol() + " is not a " +
                         "valid instrument";
            proceed = false;
        }
        
        // Third, check if the order quantity is not above the maximum
        // order size
        if ( MaxOrderQuantity > 0 && Double.compare(o.getQuantity(), MaxOrderQuantity ) > 0 )
        {
            rejectText = "Order quantity " + o.getQuantity() + " is " +
                         "greater than maximum order quantity " +
                         MaxOrderQuantity;
            proceed = false;
        }

        // Report to Logger
        if ( proceed )
        {
            Hooks.NewOrderHook( o );
            WriteOrderSocket( "NEW," + o.toString(), o );
            FIXSendExecutionReport( o, 'N' );

            try
            {
                Logger.getInstance().write( "NEW," + o.toString() );
            }
            catch (IOException e)
            {
                throw e;
            }

            // The only two times we actually try to match is during
            // new and amended orders, other times the book does not change.
            o = Execute ( o );

            // Was the new order fully filled?  Is the available quantity
            // too small to handle another fill?
            if ( Double.compare( o.getAvailableQuantity(), 0D ) == 0 ||
                 Double.compare( o.getAvailableQuantity(), o.getMinFillQuantity() ) < 0 )
                proceed = false;

        }

        if ( proceed )
        {

            // Do not add IOC orders to book
            if ( o.getTIF().equals("DAY") )
            {
                InternalEntry(o);
            }
            else
            {
                Hooks.OrderExpireHook( o );
                WriteOrderSocket( "EXPIRED," + o.toString(), o );
                FIXSendExecutionReport( o, 'E' );
                RemoveOrderSessionMap( o );

                try
                {
                    Logger.getInstance().write( "EXPIRED," + o.toString() );
                }
                catch (IOException e)
                {
                    throw e;
                }
            }

            // future expansion - send an update rather than snapshot
            MarketDataSnapshot ( o.getSymbol() );

        }
        else if ( rejectText.equals("") )
        {
            Hooks.OrderCompleteHook( o );
            WriteOrderSocket( "COMPLETED," + o.toString(), o );
			RemoveOrderSessionMap( o );

            try
            {
                Logger.getInstance().write( "COMPLETED," + o.toString() );
            }
            catch (IOException e)
            {
                throw e;
            }
        }
        else
        {
            Hooks.NewOrderRejectHook( o, rejectText );
            WriteOrderSocket( "REJECTNEW," + o.toString() + ",RejectText=" + rejectText, o );
            FIXSendReject( o, rejectText );
            RemoveOrderSessionMap( o );

            try
            {
                Logger.getInstance().write( "REJECTNEW," + o.toString() + ",RejectText=" + rejectText );
            }
            catch (IOException e)
            {
                throw e;
            }
        }

        return rejectText;
    }

    /**
     * This is the standard interface for canceling a DAY order from the
     * matching engine.  The following steps are performed:
     * 1) Validate we have another order with the same ID.
     * 2) Report that we are canceling the order to Hooks,
     * OrderSocket, and Logger.
     * 3) Remove the order from the book.
     * 4) Update the market data.
     *
     * @param o The new Order
     * @throws IOException Passthrough from Logger
     * @return An error message, or blank is successful
     * @see Hooks
     * @see Logger
     * @see PriceSocket
     * @see OrderSocket
     */
    public String CancelOrder ( Order o ) throws IOException
    {
		try {
			return CancelOrder( o, null );
		} catch ( Exception e ) {
			throw e;
		}
	}
	
    public String CancelOrder ( Order o, SessionID session ) throws IOException
    {
        String rejectText = "";
        Order OldOrder = null;

        Boolean proceed = true;
        
        // First, check if we already have an order with this ID
        if ( !IsOrderKnown(o) )
        {
            rejectText = "Cannot cancel unknown order";
            proceed = false;
        }

        if ( proceed ) {
            OldOrder = MasterBook.get( new Key(o.getSymbol(), o.getOrderID()));

            Hooks.CancelOrderHook( OldOrder );
            WriteOrderSocket( "CANCEL," + OldOrder.toString(), o );
            FIXSendExecutionReport( o, 'C' );
            RemoveOrderSessionMap( o );

            InternalCancel(OldOrder);

            try
            {
                Logger.getInstance().write( "CANCEL," + OldOrder.toString() );
            }
            catch (IOException e)
            {
                throw e;
            }

            // future expansion - send an update rather than snapshot
            MarketDataSnapshot ( o.getSymbol() );

        }
        else
        {
            Hooks.CancelOrderRejectHook( o, rejectText );
            WriteOrderSocket( "REJECTCANCEL," + o.toString() + ",RejectText=" + rejectText, o );
            FIXSendReject( o, rejectText );

            try
            {
                Logger.getInstance().write( "REJECTCANCEL," + o.toString() + ",RejectText=" + rejectText );
            }
            catch (IOException e)
            {
                throw e;
            }
        }

        return rejectText;
    }

    /**
     * This is the standard interface for amending an Order in the
     * matching engine.  The following steps are performed:
     *  1) Validate we have another Order with the same ID.
     *  2) Validate we are not making a dubious amendment.
     *  3) Report that we are amending the Order to Hooks, OrderSocket,
     *     and Logger.
     *  4) Attempt to match the new Order against previous, live DAY Order.
     *  5) Remove the old Order from the book.
     *  6) Add the new Order to the book if it is a DAY Order and was not
     *     fully executed by the execution step.
     *  7) Update the market data.
     *
     * @param o The Order being amended
     * @throws IOException Passthrough from Logger
     * @return An error message, blank if no error occured
     * @see     Order
     * @see     Logger
     */
    public String AmendOrder ( Order o ) throws IOException {
		try {
			return AmendOrder( o, "", null );
		}
		catch ( IOException e ) {
			throw e;
		}
	}
	
    public String AmendOrder ( Order o, String OrigOrderID, SessionID session ) throws IOException
    {
        String rejectText = "";
        Order OldOrder = null;
        Boolean proceed = true;
        
		AddOrderSessionMap( o, session );
        
        // For FIX orders
        if ( OrigOrderID.equals("") ) {
			OrigOrderID = o.getOrderID();
		}
		Order testOrder = new Order(o);
		
		try {
			testOrder.setOrderID( OrigOrderID );
		}
		catch ( Exception e ) {
			
		}

        // First, check if we already have an order with this ID
        if ( !IsOrderKnown( testOrder ) )
        {
            rejectText = "Cannot amend unknown order";
            proceed = false;
        }

        // Second, check if this amendment is not questionable
        // Generally this means changing the TIF, quantity, or price
        // Otherwise it is not really the same order: they should
        // cancel and send a new one!
        if ( proceed )
        {
            OldOrder = MasterBook.get( new Key(o.getSymbol(), OrigOrderID));
            String OrderID = o.getOrderID();

            // symbol and order ID are the same, otherwise IsOrderKnown would
            // have returned false in the above block

            if ( !o.getCustomer().equals(OldOrder.getCustomer()) )
            {
                rejectText = "Cannot amend customer";
                proceed = false;
            }
            else if ( !o.getSource().equals(OldOrder.getSource()) )
            {
                rejectText = "Cannot amend source";
                proceed = false;
            }
            else if ( o.getSide() != OldOrder.getSide() )
            {
                rejectText = "Cannot amend side";
                proceed = false;
            }

        }

        // Third, check if the order quantity is not above the maximum
        // order size
        if ( MaxOrderQuantity > 0 && Double.compare(o.getQuantity(), MaxOrderQuantity ) > 0 )
        {
            rejectText = "Order quantity " + o.getQuantity() + " is " +
                         "greater than maximum order quantity " +
                         MaxOrderQuantity;
            proceed = false;
        }

        if ( proceed )
        {
            Hooks.AmendOrderHook( OldOrder, o );
            WriteOrderSocket( "AMEND," + o.toString(), o );
            
            FIXSendExecutionReport( o, 'A' );
            RemoveOrderSessionMap( OldOrder );

            try
            {
                Logger.getInstance().write( "AMEND," + o.toString() );
            }
            catch (IOException e)
            {
                throw e;
            }

            InternalCancel(OldOrder);

            // The only two times we actually try to match is during
            // new and amended orders, since otherwise the book does not change.
            o = Execute ( o );

            // Was the new order fully filled?  Is the available quantity
            // too small to handle another fill?
            if ( Double.compare( o.getAvailableQuantity(), 0D ) == 0 ||
                 Double.compare( o.getAvailableQuantity(), o.getMinFillQuantity() ) < 0 )
                proceed = false;
        }

        if ( proceed )
        {

            // Do not add IOC orders to book
            if ( o.getTIF().equals("DAY") )
            {
                InternalEntry(o);
            }
            else
            {
                Hooks.OrderExpireHook( o );
                WriteOrderSocket( "EXPIRED," + o.toString(), o );
                FIXSendExecutionReport( o, 'E' );
                RemoveOrderSessionMap( o );

                try
                {
                    Logger.getInstance().write( "EXPIRED," + o.toString() );
                }
                catch (IOException e)
                {
                    throw e;
                }
            }

            // future expansion - send an update rather than snapshot
            MarketDataSnapshot ( o.getSymbol() );

        }
        else if ( rejectText.equals("") )
        {
            Hooks.OrderCompleteHook( o );
            WriteOrderSocket( "COMPLETED," + o.toString(), o );
            RemoveOrderSessionMap( o );

            try
            {
                Logger.getInstance().write( "COMPLETED," + o.toString() );
            }
            catch (IOException e)
            {
                throw e;
            }
        }
        else
        {
            Hooks.AmendOrderRejectHook( OldOrder, o, rejectText );
            WriteOrderSocket( "REJECTAMEND," + o.toString() + ",RejectText=" + rejectText, o );
            FIXSendReject( o, rejectText );
            RemoveOrderSessionMap( o );

            try
            {
                Logger.getInstance().write( "REJECTAMEND," + o.toString() + ",RejectText=" + rejectText );
            }
            catch (IOException e)
            {
                throw e;
            }
        }
        return rejectText;
    }

    /**
     * Interface for the PriceSocket to subscribe to a Symbol.  Throws
     * a reject down the connection if already subscribed.
     *
     * @param Symbol The name to subscribe
     */
    public void MarketDataSubscribe ( String Symbol )
    {
        if ( DarkPool )
        {
            WritePriceSocket( "REJECT,Symbol=" + Symbol + ",RejectText=No subscriptions allowed, Dark Pool" );
        }
        else if ( UseValidSymbols && !ValidSymbols.contains( Symbol ) )
        {
            WritePriceSocket( "REJECT,Symbol=" + Symbol + ",RejectText=Invalid instrument" );
        }
        else if ( MDSubs.contains( Symbol ) )
        {
            WritePriceSocket( "REJECT,Symbol=" + Symbol + ",RejectText=Already subscribed" );
        }
        else
        {
            MDSubs.add( Symbol );
            MarketDataSnapshot ( Symbol );
        }
    }

    /**
     * Interface for the PriceSocket to unsubscribe from a Symbol.
     * Throws a reject down the connection if not currently subscribed.
     *
     * @param Symbol The name to unsubscribe
     */

    public void MarketDataUnsubscribe ( String Symbol )
    {
        if ( MDSubs.contains( Symbol ) )
            MDSubs.remove( Symbol );
        else
            WritePriceSocket( "REJECT,Symbol=" + Symbol + ",RejectText=Not subscribed" );
    }

    /**
     * Sets the OrderSocket reference.
     *
     * @param os OrderSocket
     */
    public void SetOrderSocket ( OrderSocket os )
    {
        OS = os;
    }

    /**
     * Sets the PriceSocket reference.
     *
     * @param ps PriceSocket
     */
    public void SetPriceSocket ( PriceSocket ps )
    {
        PS = ps;
    }

    /**
     * Sets the Database reference.
     *
     * @param db Database
     */
    public void SetDatabase ( Database db )
    {
        DB = db;
    }

    /**
     * Sets the FIXInterface reference.
     *
     * @param fi FIXInterface
     */
    public void SetFIXInterface ( FIXInterface fi )
    {
        FI = fi;
    }

    /**
     * Adds a message to the outbound OrderSocket buffer.
     *
     * @param s Outbound message
     */
    private void WriteOrderSocket ( String s, Order o )
    {
        if ( OS != null && o.getSource().equals("OS") )
            OS.WriteReply( s );
    }
    
    /**
     * Writes the details of the match to the database
     *
     * @param a Order, aggressive
     * @param p Order, passive
     * @param px Price
     * @param qty Quantity
     */
    private void WriteDatabase ( Order a, Order p, double px, double qty )
    {
        if ( DB != null )
            DB.WriteMatch( a, p, px, qty );
    }


    /**
     * Sends a snapshot of the Prices and AvailableQuantities of the
     * orders for Symbol to the PriceSocket, if subscribed.
     *
     * @param Symbol The name to send market data
     */
    private void MarketDataSnapshot ( String Symbol )
    {
        if ( !MDSubs.contains( Symbol ) )
            return;

        String output = "SNAPSHOT," + Symbol;

        if ( BidBook.containsKey(Symbol) ){
            List<String> OrderIDs = BidBook.get(Symbol).getOrderIDs();

            if ( OrderIDs.size() > 0 )
            {
                output += ",BID";

                for (ListIterator<String> it = OrderIDs.listIterator(); it.hasNext(); ) {
                    String OrderID = it.next();

                    Order o = MasterBook.get( new Key(Symbol, OrderID) );

                    Double Price = o.getPrice();
                    Double Quantity = o.getAvailableQuantity();

                    output += "," + Price + "," + Quantity;
                }
            }
        }

        if ( OfferBook.containsKey(Symbol) ){
            List<String> OrderIDs = OfferBook.get(Symbol).getOrderIDs();

            if ( OrderIDs.size() > 0 )
            {
                output += ",OFFER";

                for (ListIterator<String> it = OrderIDs.listIterator(); it.hasNext(); ) {
                    String OrderID = it.next();

                    Order o = MasterBook.get( new Key(Symbol, OrderID) );

                    Double Price = o.getPrice();
                    Double Quantity = o.getAvailableQuantity();

                    output += "," + Price + "," + Quantity;
                }
            }
        }
        WritePriceSocket( output );
    }

    /**
     * Adds a message to the outbound PriceSocket buffer.
     *
     * @param s Outbound message
     */
    private void WritePriceSocket ( String s )
    {
        if ( PS != null )
            PS.WriteReply( s );
    }

    /**
     * Checks whether an Order is in the MasterBook.
     *
     * @param o Order
     * @return True if found, false if not
     */
    private Boolean IsOrderKnown ( Order o )
    {
        return MasterBook.containsKey(new Key(o.getSymbol(),o.getOrderID()));
    }

    /**
     * Private function to enter an Order into the books.
     * Called by the public MatchingEngine functions NewOrder and
     * AmendOrder.
     *
     * @param o The new Order
     */
    private void InternalEntry ( Order o )
    {
        String Symbol   = o.getSymbol();
        String OrderID  = o.getOrderID();
        double Price    = o.getPrice();
        char Side       = o.getSide();
        DepthBook Book  = null;

        //@SuppressWarnings("unchecked")
        MasterBook.put( new Key(Symbol, OrderID), o );

        if ( Side == 'B' )
        {
            if ( !BidBook.containsKey(Symbol) )
            {
                Book = new DepthBook(false);
                BidBook.put(Symbol, Book);
            }
            else
            {
                Book = BidBook.get(Symbol);
            }
        }
        else
        {
            if ( !OfferBook.containsKey(Symbol) )
            {
                Book = new DepthBook(true);
                OfferBook.put(Symbol, Book);
            }
            else
            {
                Book = OfferBook.get(Symbol);
            }
        }

        Book.AddOrder( Price, OrderID );

    }

    /**
     * Private function to remove an Order from the books. Called by
     * the public MatchingEngine functions CancelOrder and AmendOrder,
     * and the private function Execute.
     *
     * @param o The Order to be canceled
     */
    private void InternalCancel ( Order o )
    {
        String Symbol   = o.getSymbol();
        String OrderID  = o.getOrderID();
        double Price    = o.getPrice();
        char Side       = o.getSide();
        DepthBook Book  = null;

        //@SuppressWarnings("unchecked")
        MasterBook.remove( new Key(Symbol, OrderID) );

        if ( Side == 'B' )
        {
            Book = BidBook.get(Symbol);
        }
        else
        {
            Book = OfferBook.get(Symbol);
        }

        Book.RemoveOrder( Price, OrderID );

    }

    /**
     * This function attempts to execute an Order until either it is
     * fully filled, or there are no more valid matches to be made.
     * Called by the public functions NewOrder and AmendOrder.
     *
     * @param o The Order attempting to match
     * @return The Order object o back.  The only change could be
     * a lower AvailableQuantity than when it was passed in
     * @throws IOException Passthrough from Logger
     */
    private Order Execute ( Order o ) throws IOException
    {
        String Symbol   = o.getSymbol();
        String OrderID  = o.getOrderID();
        double Price    = o.getPrice();
        char Side       = o.getSide();

        DepthBook Book       = null;
        Order oMatch         = null;
        String MatchOrderID  = "";
        double TradePrice    = 0D;
        double TradeQuantity = 0D;

        // choose opposite depth book to execute against
        if ( Side == 'S' )
        {
            Book = BidBook.get(Symbol);
        }
        else
        {
            Book = OfferBook.get(Symbol);
        }

        // find next eligible match
        if ( Book != null )
        {
            MatchOrderID = Book.Match( Price, false );

            while ( !MatchOrderID.equals("") && Double.compare(o.getAvailableQuantity(),0D) != 0)
            {
                oMatch = MasterBook.get( new Key(Symbol, MatchOrderID) );

                // expanded match criteria
                if ( o.getCurrency().equals( oMatch.getCurrency() ) && 
                     Double.compare( o.getMinFillQuantity(), 
                                     oMatch.getAvailableQuantity() ) <= 0 &&
                    Double.compare( oMatch.getMinFillQuantity(), 
                                     o.getAvailableQuantity() ) <= 0 ) {

                    // price improve to midpoint
                    TradePrice = (Price + oMatch.getPrice()) / 2D;
                    TradePrice = Math.floor ( 100 * TradePrice ) / 100D;

                    // resolve execution quantity
                    TradeQuantity = Math.min( o.getAvailableQuantity(), oMatch.getAvailableQuantity() );

					// execute passive order
					oMatch.Execute( TradeQuantity, TradePrice );

					// execute aggressive order
					o.Execute( TradeQuantity, TradePrice );

                    // notify of execution
                    Hooks.ExecutionHook( o, oMatch, TradePrice, TradeQuantity );

                    String MatchDetails = "TradePrice=" + TradePrice + "," +
                                          "TradeQuantity=" + TradeQuantity;

                    WriteOrderSocket( "MATCH,OrderID=" + OrderID + "," + MatchDetails, o );
    
                    WriteOrderSocket( "MATCH,OrderID=" + MatchOrderID + "," + MatchDetails, oMatch );
                    
                    FIXSendExecutionReport( o, 'F' );
                    FIXSendExecutionReport( oMatch, 'F' );

                    WriteDatabase( o, oMatch, TradePrice, TradeQuantity );

                    try
                    {
                        String LogNotification = "aggressiveOrderID=" + OrderID + "," +
                                                 "passiveOrderID=" + MatchOrderID + "," +
                                                 MatchDetails;

                        Logger.getInstance().write( "MATCH," + LogNotification );
                        Logger.getInstance().writeMatch( LogNotification );
                    }
                    catch (IOException e)
                    {
                        throw e;
                    }

                    // remove passive order from book if fully filled or min fill quantity
                    // is greater than available quantity
                    if ( Double.compare( oMatch.getAvailableQuantity(), 0D ) == 0  ||
                         Double.compare( oMatch.getAvailableQuantity(), oMatch.getMinFillQuantity() ) < 0)
                    {
                        InternalCancel( oMatch );

                        Hooks.OrderCompleteHook( oMatch );
                        WriteOrderSocket( "COMPLETED," + oMatch.toString(), oMatch );
                        RemoveOrderSessionMap( oMatch );

                        try
                        {
                            Logger.getInstance().write( "COMPLETED," + oMatch.toString() );
                        }
                        catch (IOException e)
                        {
                            throw e;
                        }
                    }

                    // future expansion - send an update rather than snapshot here
                    MarketDataSnapshot ( o.getSymbol() );

                }

                // find next eligible match
                MatchOrderID = Book.MatchNext( Price, false );
            }
        }

        return o;
    }
    
    public Order FindOrder( String OrderID, String Symbol ) {
		
		if ( MasterBook.containsKey( new Key(Symbol, OrderID) ) )
			return MasterBook.get( new Key(Symbol, OrderID) );
		
		return null;
	}
	
	private void AddOrderSessionMap( Order o, SessionID session ) {
		
		if ( !o.getSource().equals("FIX") )
			return;
		
		String OrderID = o.getOrderID();
		
		if ( !OrderSessionMap.containsKey ( OrderID ) )
			OrderSessionMap.put( OrderID, session );

	}
	
	private void RemoveOrderSessionMap( Order o ) {

		if ( !o.getSource().equals("FIX") )
			return;
		
		String OrderID = o.getOrderID();
		
		if ( OrderSessionMap.containsKey ( OrderID ) )
			OrderSessionMap.remove( OrderID );
	
	}
	
	private SessionID GetOrderSessionMap( Order o ) {

		if ( !o.getSource().equals("FIX") )
			return null;
		
		String OrderID = o.getOrderID();
		
		if ( OrderSessionMap.containsKey ( OrderID ) ) 
			return OrderSessionMap.get( OrderID );
		
		return null;
	}
	
	private void FIXSendReject ( Order o, String RejectText ) {
		
		if ( FI != null && OrderSessionMap.containsKey( o.getOrderID() ) )
			FI.SendReject( o.getOrderID(), RejectText, GetOrderSessionMap( o ) );
		
	}

	private void FIXSendExecutionReport ( Order o, char operation ) {
		
		if ( FI != null && OrderSessionMap.containsKey( o.getOrderID() ) )
			FI.SendExecutionReport( o, operation, GetOrderSessionMap( o ) );
		
	}
	
}
