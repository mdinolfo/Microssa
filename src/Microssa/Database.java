/*
 * Database.java
 *
 * Copyright (C) 2017 Michael Dinolfo
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

import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.ResultSet;

import Microssa.Configuration;
import Microssa.Logger;
import Microssa.Order;

/**
 * This class is responsible for writing MatchingEngine events to
 * a database.
 * 
 */
public class Database implements Runnable {
    
    /** Thread object for this class. */
    private Thread T;

    /** Label of the Thread. */
    private String ThreadName;
    
    /** Whether we are able to write to the database connection. */
    private boolean Connected;

    /** Reference to MatchingEngine object. */
    private MatchingEngine ME;
    
    /** Database Connection. */
    private Connection DbConnection;

    /** How often to scan the database for new orders, in milliseconds */
    private int DbScan;


    /**
     * The constructor stores information for when the Thread is started,
     * but does not start it on its own.
     *
     * @param name A string identifier for the future Thread
     * @param me A reference to the MatchingEngine this socket is
     * supposed to connect
     */
    public Database ( String name, MatchingEngine me )
    {
        T = null;
        Connected = false;
        ME = me;
        ThreadName = name;

        DbScan = Configuration.getInstance().getInt("DBSCAN") * 1000;
    }
    
    public void WriteMatch ( Order a, Order p, double px, double qty ) {
        
        if ( !Connected )
            return;

        String OrderID, InternalID, Symbol, Customer, Currency, Query;
        char Side, Role;
        Double Price, Quantity;
        
        // insert aggressive order
        OrderID = a.getOrderID();
        InternalID = a.getInternalID();
        Symbol = a.getSymbol();
        Customer = a.getCustomer();
        Currency = a.getCurrency();
        Side = a.getSide();
        Role = 'A';
        Price = px;
        Quantity = qty;
        
        Query = "insert into trades (order_id,internal_id, symbol,customer,currency," +
        "side,price,quantity,role) values ('" + OrderID + "','" + InternalID + "','" +
        Symbol + "','" + Customer + "','" + Currency  + "','" +  Side +
        "','" + Price + "','" + Quantity + "','" + Role + "')";
        
        try {
            Statement stmt = DbConnection.createStatement();
            stmt.execute( Query );
        }
        catch ( SQLException e ) {
            // do nothing for now
            System.out.println( "WARNING: " + e.getMessage() );
        }
        
        // insert passive order
        OrderID = p.getOrderID();
        InternalID = p.getInternalID();
        Symbol = p.getSymbol();
        Customer = p.getCustomer();
        Currency = p.getCurrency();
        Side = p.getSide();
        Role = 'P';
        
        Query = "insert into trades (order_id,internal_id, symbol,customer,currency," +
        "side,price,quantity,role) values ('" + OrderID + "','"  + InternalID + "','" +
        Symbol + "','" + Customer + "','" + Currency  + "','" +  Side +
        "','" + Price + "','" + Quantity + "','" + Role + "')";
        
        try {
            Statement stmt = DbConnection.createStatement();
            stmt.execute( Query );
        }
        catch ( SQLException e ) {
            // do nothing for now
            System.out.println( "WARNING: " + e.getMessage() );
        }
    }

    /**
     * Notifies the MatchingEngine that this interface exists.  Scans the
     * inbound_order table for order actions and forwards these actions
     * to the Matching Engine.  Deleted completed order actions.
     * This function never exits, which requires killing Microssa to stop.
     */
    public void run ()
    {
        ME.SetDatabase( this );
        
        Statement stmt;
        ResultSet rs = null;
        
        String OrderID, Symbol, Customer, ArriveDate, TIF, Currency;
        double Price, Quantity, AvailableQuantity, MinFillQuantity;
        char Side, Action;
        
        String Source = "DB";
        
        String Query = "select action,order_id,symbol,customer," +
        "arrive_date,tif,currency,side,price,quantity,available_quantity," +
        "min_fill_quantity,sequence_number from inbound_orders";
        
        String RemoveList;
        
        while ( true ) {
            
            RemoveList = "";
            
            try {
                stmt = DbConnection.createStatement();
                
                if ( stmt.execute( Query ) )
                    rs = stmt.getResultSet();
                    
                while ( rs.next() ) {
                    
                    if ( !RemoveList.equals("") )
                        RemoveList += ",";
                    
                    Action            = rs.getString(1).charAt(0);
                    OrderID           = rs.getString(2);
                    Symbol            = rs.getString(3);
                    Customer          = rs.getString(4);
                    ArriveDate        = rs.getString(5);
                    TIF               = rs.getString(6);
                    Currency          = rs.getString(7);
                    if ( rs.wasNull() )
                        Currency = "";
                    Side              = rs.getString(8).charAt(0);
                    Price             = rs.getDouble(9);
                    Quantity          = rs.getDouble(10);
                    AvailableQuantity = rs.getDouble(11);
                    if ( rs.wasNull() )
                        AvailableQuantity = Quantity;
                    MinFillQuantity   = rs.getDouble(12);
                    if ( rs.wasNull() )
                        MinFillQuantity = 0D;
                    
                    Order o = new Order(OrderID,Symbol,Customer,Source,ArriveDate,
                                        Price,Quantity,AvailableQuantity,Side,TIF,
                                        Currency,MinFillQuantity);
                    
                    switch ( Action ) {
                        case 'N': ME.NewOrder(o);
                                  break;
                        case 'A': ME.AmendOrder(o);
                                  break;
                        case 'C': ME.CancelOrder(o);
                                  break;
                        default:
                    }
                    
                    RemoveList += rs.getString(13);
                }
                
                if ( !RemoveList.equals("") )
                    stmt.execute( "delete from inbound_orders where " +
                    "sequence_number in (" + RemoveList +")" );
            }
            catch ( Exception e ) {
                // do nothing for now
                System.out.println( "WARNING: " + e.getMessage() );
            }
            
            try {
                Thread.sleep( DbScan );
            }
            catch ( Exception e ) {
                // do nothing for now, force clean exit later
            }

        }
        
    }
    
    /**
     * Starts the Thread.  Attempts to connect to the database. Sends a
     * message to Logger to indicate successful start.
     *
     * @throws SQLException Failure to connect to database
     * @throws IOException Passthrough from Logger
     */
    public void start () throws IOException, SQLException
    {
        String ErrorMessage = null;
        
        if ( T == null )
        {
            // gather credentials
            String DbType     = Configuration.getInstance().getString("DBTYPE");
            String DbUsername = Configuration.getInstance().getString("DBUSERNAME");
            String DbPassword = Configuration.getInstance().getString("DBPASSWORD");
            String DbServer   = Configuration.getInstance().getString("DBSERVER");
            String DbSchema   = Configuration.getInstance().getString("DBSCHEMA");

            // attempt to connect
            try
            {
                DbConnection =
                DriverManager.getConnection("jdbc:" + DbType + "://"+
                    DbServer + "/"+ DbSchema + "?user=" + DbUsername +
                    "&password=" + DbPassword );

                Connected = true;
            }
            catch ( SQLException e ) {
                
                ErrorMessage = e.getMessage();
                Connected = false;
                
            }
            
            if ( !Connected ) {
                     
                throw new SQLException("FATAL: Unable to connect to database - " + ErrorMessage);
                
            }
            
            try
            {
                Logger.getInstance().write("Database connection established.");
            }
            catch ( IOException e )
            {
                throw e;
            }

            T = new Thread( this, ThreadName );
            T.start();
        }
    }

}
