/*
 * Order.java
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
import java.util.zip.*;
import java.text.*;

import Microssa.Configuration;

/**
 * Object used to store order details.  An order is a willingness
 * to buy or sell (Side) some amount (Quantity) of a real or virtual
 * object (Symbol) for a specific dollar amount (Price).  Other fields
 * serve as identifiers or special instructions for the order.
 */
public class Order {

    /** The identifier of the Order.  Should be unique. */
    private String OrderID;

    /** The internal identifier of the Order.  Should be unique. */
    private String InternalID;

    /** The next internal identifier to use for a new Order. */
    private static int nextInternalID = 1;

    /** The label/code for the real or virtual good of this Order. */
    private String Symbol;

    /** The customer's identifier. */
    private String Customer;

    /** The location from where this Order originated. */
    private String Source;

    /** The date this Order arrived.  Currently unused. */
    private String ArriveDate;

    /** The Time in Force of the order.  Valid values are DAY meaning
     * the order will sit in the book until either fully filled
     * or the matching engine is shut down, or IOC (Immediate or cancel)
     * meaning the Order will immediately match but, if not fully
     * filled, will expire rather than sit in the book waiting for
     * subsequent matches.
     */
    private String TIF;

    /** The price the Order would like to buy or sell.  Actual match
     * prices may be better depending on market conditions, meaning
     * lower than a buy Order's price or higher than a sell Order's price.
     */
    private double Price;

    /** The maximum amount of goods to buy or sell. */
    private double Quantity;

    /** The remaining quantity on the order.  Would be less than Quantity
     * if the order was partially filled, or zero if the order was fully
     * filled.
     */
    private double AvailableQuantity;

    /** Whether this is a buy or sell Order. */
    private char Side;

    /** The day the order will settle. */
    private String SettlementDate;

    /** The currency of the order. */
    private String Currency;

    /** Minimum fill quantity for a partial execution. */
    private double MinFillQuantity;

    /** Minimum fill quantity for a partial execution. */
    private double AveragePrice;

	/** Total amount executed */
	private double CumulativeQuantity;

    /**
     * Full constructor; all private variables passed.
     *
     * @param oID OrderID
     * @param s Symbol
     * @param c Customer
     * @param sID Source
     * @param a ArriveDate
     * @param p Price
     * @param q Quantity
     * @param aq AvailableQuantity
     * @param buySell Side
     * @param t TIF
     * @throws DataFormatException Passed up from error checking functions
     */
    public Order (String oID, String s, String c, String sID, String a,
                  double p, double q, double aq, char buySell, String t,
                  String ccy, Double mfq )
                  throws DataFormatException
    {
        try
        {
            setOrderID(oID);
            setSymbol(s);
            setCustomer(c);
            setSource(sID);
            setArriveDate(a);
            setPrice(p);
            setQuantity(q);
            setAvailableQuantity(aq);
            setSide(buySell);
            setTIF(t);
            setCurrency(ccy);
            setMinFillQuantity(mfq);
            AveragePrice = CumulativeQuantity = 0D;
        }
        catch ( DataFormatException e )
        {
            // pass it up so the order manager can handle the reject
            throw e;
        }

        InternalID = String.format("%010d", nextInternalID++) + "MC";
    }

    /**
     * Copy constructor.
     *
     * @param o Order object from where to copy values
     */
    public Order ( Order o )
    {
        OrderID             = o.OrderID;
        Symbol              = o.Symbol;
        Customer            = o.Customer;
        Source              = o.Source;
        ArriveDate          = o.ArriveDate;
        Price               = o.Price;
        Quantity            = o.Quantity;
        AvailableQuantity   = o.Quantity;
        Side                = o.Side;
        TIF                 = o.TIF;
        Currency            = o.Currency;
        MinFillQuantity     = o.MinFillQuantity;
        AveragePrice = CumulativeQuantity = 0D;

        InternalID = String.format("%010d", nextInternalID++) + "MC";
    }

    /**
     * @return OrderID
     */
    public String getOrderID ()
    {
        return OrderID;
    }

    /**
     * @return InternalID
     */
    public String getInternalID ()
    {
        return InternalID;
    }

    /**
     * @return Symbol
     */
    public String getSymbol ()
    {
        return Symbol;
    }

    /**
     * @return Customer
     */
    public String getCustomer ()
    {
        return Customer;
    }

    /**
     * @return Source
     */
    public String getSource ()
    {
        return Source;
    }

    /**
     * @return ArriveDate
     */
    public String getArriveDate ()
    {
        return ArriveDate;
    }

    /**
     * @return Price
     */
    public double getPrice ()
    {
        return Price;
    }

    /**
     * @return Quantity
     */
    public double getQuantity ()
    {
        return Quantity;
    }

    /**
     * @return AvailableQuantity
     */
    public double getAvailableQuantity ()
    {
        return AvailableQuantity;
    }

    /**
     * @return Side
     */
    public char getSide ()
    {
        return Side;
    }

    /**
     * @return TIF
     */
    public String getTIF ()
    {
        return TIF;
    }

    /**
     * @return Currency
     */
    public String getCurrency ()
    {
        return Currency;
    }

    /**
     * @return MinFillQuantity
     */
    public double getMinFillQuantity ()
    {
        return MinFillQuantity;
    }
    
    /**
     * @return AveragePrice
     */
    public double getAveragePrice ()
    {
        return AveragePrice;
    }

    /**
     * @return CumulativeQuantity
     */
    public double getCumulativeQuantity ()
    {
        return CumulativeQuantity;
    }

    /**
     * @param oID OrderID
     * @throws DataFormatException If oID is a blank string
     */
    public void setOrderID ( String oID ) throws DataFormatException
    {
        if ( oID.equals("") )
            throw new DataFormatException("OrderID cannot be blank");

        OrderID = oID;
    }

    /**
     * @param s Symbol
     * @throws DataFormatException If s is a blank string
     */
    public void setSymbol ( String s ) throws DataFormatException
    {
        if ( s.equals("") )
            throw new DataFormatException("Symbol cannot be blank");

        Symbol = s;
    }

    /**
     * @param c Customer
     * @throws DataFormatException If c is a blank string
     */
    public void setCustomer ( String c ) throws DataFormatException
    {
        if ( c.equals("") )
            throw new DataFormatException("Customer cannot be blank");

        Customer = c;
    }

    /**
     * @param sID Source
     * @throws DataFormatException If sID is a blank string
     */
    public void setSource ( String sID ) throws DataFormatException
    {
        if ( sID.equals("") )
            throw new DataFormatException("Source cannot be blank");

        Source = sID;
    }

    /**
     * @param a ArriveDate
     */
    public void setArriveDate ( String a )
    {
        if ( a.equals("") ) {
			Format formatter = new SimpleDateFormat("yyyyMMdd");
			Date today = Calendar.getInstance().getTime();
			a = formatter.format(today);
		}

        ArriveDate = a;
    }

    /**
     * @param p Price
     * @throws DataFormatException If p is less than or equal to zero
     */
    public void setPrice ( double p ) throws DataFormatException
    {
        if ( Double.compare( p, 0D ) <= 0 )
            throw new DataFormatException("Price " + p +
                " is zero or negative");

        Price = p;
    }

    /**
     * Also updates AvailableQuantity by the amount Quantity changes.
     *
     * @param q Quantity
     * @throws DataFormatException If q is less than or equal to zero,
     * or if the updated AvailableQuantity would be less than or
     * equal to zero
     */
    public void setQuantity ( double q ) throws DataFormatException
    {
        double aq = AvailableQuantity + (q - Quantity);

        if ( Double.compare( q, 0D ) <= 0 )
            throw new DataFormatException("Quantity " + q +
                " is zero or negative");

        if ( Double.compare( aq, 0D ) < 0 )
            throw new DataFormatException("New " +
                "AvailableQuantity " + aq + " would be negative");

        AvailableQuantity = aq;
        Quantity = q;
    }

    /**
     * @param aq AvailableQuantity
     * @throws DataFormatException If aq is less than or equal to zero,
     * or if aq is greater than Quantity
     */
    public void setAvailableQuantity ( double aq ) throws DataFormatException
    {
        if ( Double.compare( aq, 0D ) < 0 )
            throw new DataFormatException("New available " +
                "Quantity " + aq + " is negative");

        if ( Double.compare( aq, Quantity ) > 0 )
            throw new DataFormatException("New " +
                "AvailableQuantity " + aq + " is greater than " +
                "total Quantity " + Quantity);

        AvailableQuantity = aq;
    }

    /**
     * @param s Side
     * @throws DataFormatException If s is not equal to B or S
     */
    public void setSide ( char s ) throws DataFormatException
    {
        if ( s == 'B' || s == 'S' )
            Side = s;
        else
            throw new DataFormatException(s +
                " is not a valid side");
    }

    /**
     * @param t TIF
     * @throws DataFormatException If t is not equal to IOC or DAY
     */
    public void setTIF ( String t ) throws DataFormatException
    {
        if ( t.equals("IOC") || t.equals("DAY") )
            TIF = t;
        else
            throw new DataFormatException(t +
            " is not a valid time in force");
    }

    /**
     * @param ccy Currency
     * @throws DataFormatException If blank and no default is specified
     */
    public void setCurrency ( String ccy ) throws DataFormatException
    {
        if ( ccy.equals("") )
            ccy = Configuration.getInstance().getString("DEFAULTCURRENCY");
        
        if ( ccy.equals("") )
            throw new DataFormatException("Currency cannot be blank");
        else
            Currency = ccy;
    }

    /**
     * @param mfq MinFillQuantity
     * @throws DataFormatException If mfq is greater than available
     * quantity
     */
    public void setMinFillQuantity ( double mfq ) throws DataFormatException
    {
        if ( Double.compare( mfq, AvailableQuantity ) <= 0 )
            MinFillQuantity = mfq;
        else
            throw new DataFormatException("MinFillQuantity " + mfq + 
            " is greater than AvailableQuantity " + AvailableQuantity);
    }

    /**
     * This function's purpose is to calculate the average price
     * of its executions for FIX reporting. It also updates the
     * available quantity.
     * 
     * @param qty Quantity of execution
     * @param px Price of Execution
     */
    public void Execute ( double qty, double px )
    {
		if ( Double.compare( AveragePrice, 0D ) == 0 ) {
			AveragePrice = px;
		} else {
			Double TotalQuantity = qty + CumulativeQuantity;
			AveragePrice = ( ( AveragePrice * CumulativeQuantity )  +
			                 ( px * qty ) )  / TotalQuantity ;
		}
		
		AvailableQuantity -= qty;
		CumulativeQuantity += qty;
    }

    /**
     * This function is used by the MatchingEnginer for logging and
     * socket communication.
     *
     * @return Order's private variables as a comma-separated String
     */
    public String toString ()
    {
        String returnVal = "OrderID=" + OrderID + "," +
                           "InternalID=" + InternalID + "," +
                           "Customer=" + Customer + "," +
                           "Source=" + Source + "," +
                           "Symbol=" + Symbol + "," +
                           "Side=" + Side + "," +
                           "Price=" + Price + "," +
                           "Quantity=" + Quantity + "," +
                           "AvailableQuantity=" + AvailableQuantity + "," +
                           "TIF=" + TIF + "," +
                           "ArriveDate=" + ArriveDate + "," +
                           "Currency=" + Currency + "," +
                           "MinFillQuantity=" + MinFillQuantity;
        return returnVal;
    }

}

