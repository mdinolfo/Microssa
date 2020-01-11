/*
 * FIXInterface.java
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
import quickfix.*;
import quickfix.field.*;
import java.util.Map;
import java.util.HashMap;

import Microssa.Configuration;
import Microssa.Logger;
import Microssa.Order;

/**
 * This class is responsible for reading orders and writing matches
 * and order events using the FIX protocol via the FIXApp class.
 * 
 */
public class FIXInterface extends quickfix.MessageCracker implements quickfix.Application {
    
    /** A reference to the FIX Session */
    private SessionID session;

    /** Reference to MatchingEngine object. */
    private MatchingEngine ME;
    
    /** Default FIX customer if one is not specified in the Account (tag 1) field. */
    private String DefaultCustomer;
    
    /** Execution ID for 35=8 messages*/
    private int execID;
    
    /** Constructor */
    public FIXInterface ( MatchingEngine me ) {
        session = null;
        
        ME = me;

        ME.SetFIXInterface( this );
        
        DefaultCustomer = Configuration.getInstance().getString("DEFAULTFIXCUST");
        
        //OrderSessionMap = new HashMap<>();
        
        execID = 1;
    }

    /**
     * Stores the session information and writes to the log.
     */
    public void onCreate(SessionID sessionId) {
        session = sessionId;
        
        try {
            Logger.getInstance().write("FIX session created.");
        }
        catch ( Exception e ) {
            // do nothing
        }
    }
    
    public void onLogon(SessionID sessionId) {
    
    }
    
    public void onLogout(SessionID sessionId) {
    
    }
    
    public void toAdmin(Message message, SessionID sessionId) {
    
    }
    
    public void toApp(Message message, SessionID sessionId) throws DoNotSend  {
    
    }
    
    public void fromAdmin(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, RejectLogon  {
    
    }
    
    public void fromApp(Message message, SessionID sessionId) throws FieldNotFound, IncorrectDataFormat, IncorrectTagValue, UnsupportedMessageType  {
        
        crack(message, sessionId);
    
    }

    public void onMessage(quickfix.fix44.NewOrderSingle message, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {    

        parseMessage( message, sessionID, 'N' );
    
    }
    
    public void onMessage(quickfix.fix44.OrderCancelReplaceRequest message, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {    

        parseMessage( message, sessionID, 'A' );
    
    }
    
    public void onMessage(quickfix.fix44.OrderCancelRequest message, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {    

        parseMessage( message, sessionID, 'C' );
    
    }
    
    public void onMessage(quickfix.fix44.ExecutionReport message, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {    

        // dump message
    
    }
    
    public void onMessage(quickfix.fix44.BusinessMessageReject message, SessionID sessionID) throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {    

        // dump message
    
    }
    
    private void parseMessage( Message message, SessionID sessionID, char operation )  throws FieldNotFound, UnsupportedMessageType, IncorrectTagValue {
		
        String orderID, symbol, customer, source, arriveDate, TIF;
        String currency, origOrderID, RejectText;
        double price, quantity, availableQuantity, minFillQuantity;
        char side;

        orderID = symbol = customer = arriveDate = TIF = "";
        currency = origOrderID = RejectText = "";
        price = quantity = availableQuantity = minFillQuantity = 0D;
        side = ' ';
        
        source = "FIX";
                    
        // OrderID
        ClOrdID oi = new ClOrdID();
        message.getField(oi);
        orderID = oi.getValue();

        // Symbol
        Symbol s = new Symbol();
        message.getField(s);
        symbol = s.getValue();
        
        // Customer
        Account a = new Account();
        if( message.isSetField(a) ){
            message.getField(a);
            customer = a.getValue();
        }
        else {
            customer = DefaultCustomer;
        }

        // ArriveDate
        TradeDate td = new TradeDate();
        if( message.isSetField(td) ){
            message.getField(td);
            arriveDate = td.getValue();
        }        
        
        // TIF
        TimeInForce tif = new TimeInForce();
        if( message.isSetField(tif) ){
            message.getField(tif);
            
            if ( tif.getValue() == TimeInForce.DAY ) {
                TIF = "DAY";
            } else if ( tif.getValue() == TimeInForce.IMMEDIATE_OR_CANCEL ) {
                TIF = "IOC";
            } else {
                TIF = "UNSUPPORTED";
            }
            
        } else {
            
            TIF = "DAY";
        
        }
        
        // Currency
        Currency c = new Currency();
        if( message.isSetField(c) ){
            message.getField(c);
            currency = c.getValue();
        }
        
        // Price
        Price p = new Price();
        if( message.isSetField(p) ) {
            message.getField(p);
            price = p.getValue();
        }
        
        // Quantity
        OrderQty oq = new OrderQty();
        if ( message.isSetField(oq) ) {
			message.getField(oq);
			quantity = oq.getValue();
		}
        
        // MinFillQuantity
        MinQty mq = new MinQty();
        if( message.isSetField(mq) ){
            message.getField(mq);
            minFillQuantity = mq.getValue();
        }
        
        // Side
        Side si = new Side();
        message.getField(si);
        if ( message.isSetField(si) ) {
			if ( si.getValue() == Side.BUY ) {
				side = 'B';
			}
			else if ( si.getValue() == Side.SELL ) {
				side = 'S';
			}
		}
        
        // Original Order ID
        OrigClOrdID ocoi = new OrigClOrdID();
        if( message.isSetField(ocoi) ){
            message.getField(ocoi);
            origOrderID = ocoi.getValue();
        }

        // AvailableQuantity
        Order oldOrder = ME.FindOrder( origOrderID, symbol );
        
        if ( oldOrder != null ) {
			availableQuantity = oldOrder.getAvailableQuantity();
		} else {
			availableQuantity = quantity;
		}
        
        // Create order and send to matching engine
		if ( operation == 'C'  ) {
			try {
				Order o = new Order(origOrderID,symbol,customer,source,arriveDate,
									price,quantity,availableQuantity,side,TIF,
									currency,minFillQuantity);

				ME.CancelOrder( o, sessionID );

			} catch ( Exception e ) {
				System.out.println( e.getMessage() );
			}
		}

		if ( operation == 'A' ) {
			try {
				Order o = new Order(orderID,symbol,customer,source,arriveDate,
									price,quantity,availableQuantity,side,TIF,
									currency,minFillQuantity);
									
				ME.AmendOrder( o, origOrderID, sessionID);
				
			} catch ( Exception e ) {
				System.out.println( e.getMessage() );
			}
								
		}

		
		if ( operation == 'N'  ) {
			try {
				
				Order o = new Order(orderID,symbol,customer,source,arriveDate,
									price,quantity,availableQuantity,side,TIF,
									currency,minFillQuantity);
			
				ME.NewOrder( o, sessionID );
			
			} catch ( Exception e ) {
				System.out.println( e.getMessage() );
			}
								
			
		}

    }
    
    public void SendReject ( String orderID, String RejectText, SessionID sessionID ) {

		quickfix.fix44.Reject reply = new quickfix.fix44.Reject();
		
		reply.set( new Text( "OrderID="+orderID+" :: "+RejectText ) );
		
		try {
			Session.sendToTarget( reply, sessionID );
		}
		catch ( SessionNotFound e ) {
			// do nothing
		}
            
	}
	
    public void SendExecutionReport ( Order o, char operation, SessionID sessionID ) {

        // send an accept message
        quickfix.fix44.ExecutionReport reply = new quickfix.fix44.ExecutionReport();

		reply.set( new ClOrdID( o.getOrderID() ) );
		reply.set( new OrderID( o.getInternalID() ) );
		reply.set( new ExecID( Integer.toString( execID++ ) ) );
		reply.set( new Side( o.getSide() ) );
		reply.set( new LeavesQty( o.getAvailableQuantity() ) );
		reply.set( new CumQty( o.getCumulativeQuantity() ) );
		reply.set( new AvgPx( o.getAveragePrice() ) );
		reply.set( new Symbol( o.getSymbol() ) );
		
		if ( operation == 'F' && Double.compare(o.getAvailableQuantity(),0D) == 0 ) {
			operation = 'D';
		}
		
		switch ( operation ) {
			case 'N':	reply.set( new OrdStatus( OrdStatus.NEW ) );
						reply.set( new ExecType( ExecType.NEW ) );
						break;

			case 'C':	reply.set( new OrdStatus( OrdStatus.CANCELED ) );
						reply.set( new ExecType( ExecType.CANCELED ) );
						break;

			case 'A':	reply.set( new OrdStatus( OrdStatus.REPLACED ) );
						reply.set( new ExecType( ExecType.REPLACE ) );
						break;

			case 'E':	reply.set( new OrdStatus( OrdStatus.EXPIRED ) );
						reply.set( new ExecType( ExecType.EXPIRED ) );
						break;

			case 'F':	reply.set( new OrdStatus( OrdStatus.PARTIALLY_FILLED ) );
						reply.set( new ExecType( ExecType.FILL ) );
						break;

			case 'D':	reply.set( new OrdStatus( OrdStatus.FILLED ) );
						reply.set( new ExecType( ExecType.FILL ) );
						break;
		}
		
        
		try {
			Session.sendToTarget( reply, sessionID );
		}
		catch ( SessionNotFound e ) {
			// do nothing
		}
        
 
	}
    
}
