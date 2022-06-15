/*
 * OrderSocket.java
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

import java.io.*;
import java.net.*;

import Microssa.MatchingEngine;
import Microssa.Order;
import Microssa.Logger;
import Microssa.Configuration;

/**
 * This class creates a TCP socket interface to allow new orders,
 * amends, and cancel requests to be sent to the MatchingEngine.  The
 * MatchingEngine replies to these requests, as
 * well as sending match, complete, and expiration notifications.
 * This socket runs on its own thread.
 *
 * @see MatchingEngine
 */
public class OrderSocket implements Runnable {

    /** Thread object for this class. */
    private Thread T;

    /** Label of the Thread. */
    private String ThreadName;

    /** Port number to listen. */
    private int PortNumber;

    /** Reference to MatchingEngine object. */
    private MatchingEngine ME;

    /** Buffered output to be written to the connection. */
    private String OutputBuffer;

    /** Number of erroneous messages.  Three in a row results in a
     *  disconnect. */
    private int ErrorCount;
    
    /** Milliseconds between PING messages.  These messages can be
     *  ignored on the client side. */
    private int PingMS;

    /**
     * The constructor stores information for when the Thread is started,
     * but does not start it on its own.
     *
     * @param Name A string identifier for the future Thread
     * @param me A reference to the MatchingEngine this socket is
     * supposed to connect
     */
    public OrderSocket ( String Name, MatchingEngine me )
    {
        ThreadName = Name;
        PortNumber = Configuration.getInstance().getInt("ORDERPORT");
        PingMS = Configuration.getInstance().getInt("PINGMS");
        ME = me;
        T = null;
        ErrorCount = 0;
        OutputBuffer = "";
    }

    /**
     * Interface for the MatchingEngine to queue a reply on the
     * connection.
     *
     * @param s The message to be sent out
     */
    public void WriteReply ( String s )
    {
        if ( OutputBuffer.length() > 0 )
            OutputBuffer += "\n" + s;
        else
            OutputBuffer = s;
    }

    /**
     * Parses the input received on the connection.  Will disconnect
     * when receiving a "BYE", otherwise will attempt to parse the
     * message as a new/amend/cancel request.  If all fails, returns
     * an error message and increments the ErrorCount.  Disconnects
     * if there are three consecutive errors.
     *
     * @param InputLine The message received on the connection
     * @return An error message, or blank string if there was no error
     */
    private String ProcessInput ( String InputLine )
    {
        if ( InputLine.equals("END") )
            return "BYE";

        if ( InputLine.equals("PING") )
            return "";

        String[] token = InputLine.split(",");
        String cmd = token[0];

        if ( cmd.matches("(NEW|AMEND|CANCEL)") ) {
            String OrderID, Symbol, Customer, Source, ArriveDate, TIF;
            String Currency;
            double Price, Quantity, AvailableQuantity, MinFillQuantity;
            char Side;

            OrderID = Symbol = Customer = Source = ArriveDate = "";
            TIF = Currency = "";
            Price = Quantity = AvailableQuantity = MinFillQuantity = 0D;
            Side = ' ';

            // valid command, reset disconnect counter
            ErrorCount = 0;

            for ( int x=1; x<token.length; x++)
            {
                String[] parameter = token[x].split("=");
                if ( parameter.length == 2 )
                {
                    if ( parameter[0].equalsIgnoreCase("OrderID") )
                        OrderID = parameter[1];
                    else if ( parameter[0].equalsIgnoreCase("Symbol") )
                        Symbol = parameter[1];
                    else if ( parameter[0].equalsIgnoreCase("Customer") )
                        Customer = parameter[1];
                    else if ( parameter[0].equalsIgnoreCase("ArriveDate") )
                        ArriveDate = parameter[1];
                    else if ( parameter[0].equalsIgnoreCase("TIF") )
                        TIF = parameter[1];
                    else if ( parameter[0].equalsIgnoreCase("Price") )
                        Price = Double.parseDouble(parameter[1]);
                    else if ( parameter[0].equalsIgnoreCase("Quantity") )
                        Quantity = Double.parseDouble(parameter[1]);
                    else if ( parameter[0].equalsIgnoreCase("AvailableQuantity") )
                        AvailableQuantity = Double.parseDouble(parameter[1]);
                    else if ( parameter[0].equalsIgnoreCase("Side") )
                        Side = parameter[1].charAt(0);
                    else if ( parameter[0].equalsIgnoreCase("Currency") )
                        Currency = parameter[1];
                    else if ( parameter[0].equalsIgnoreCase("MinFillQuantity") )
                        MinFillQuantity = Double.parseDouble(parameter[1]);
                }
            }
            
            Source = "OS";

            if ( Double.compare(Quantity,0D) > 0 && Double.compare(AvailableQuantity,0D) == 0)
                AvailableQuantity = Quantity;

            try
            {
                String RejectText = "";

                // create order
                Order o = new Order(OrderID,Symbol,Customer,Source,ArriveDate,
                                    Price,Quantity,AvailableQuantity,Side,TIF,
                                    Currency,MinFillQuantity);

                // send to matching engine
                if ( cmd.equals("NEW") )
                    RejectText = ME.NewOrder(o);
                else if ( cmd.equals("AMEND") )
                    RejectText = ME.AmendOrder(o);
                else
                    RejectText = ME.CancelOrder(o);

                return "";
            }
            catch ( Exception e )
            {
                return "REJECT" + InputLine + ",RejectText=" + e.getMessage();
            }

        }

        ErrorCount++;
        if ( ErrorCount >= 3 )
            return "BYE";
        else
            return "UNKNOWN COMMAND";
    }

    /**
     * Notifies the MatchingEngine that this socket exists.  Opens the
     * PortNumber and listens for connections.  Establishes connections
     * and reads/writes data.  This function never exits, which requires
     * killing Microssa to stop.
     */
    public void run ()
    {
        ME.SetOrderSocket(this);

        while ( true )
        {
            try (
                ServerSocket serverSocket = new ServerSocket(PortNumber);
                Socket clientSocket = serverSocket.accept();
                PrintWriter out =
                    new PrintWriter(clientSocket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
            ) {

                String InputLine, OutputLine;

                int PingCheck = 0;
                boolean AbortSession = false;

                out.println("CONNECTED");

                // messages from a previous, disconnected session?
                if ( !OutputBuffer.equals("") )
                {
                    out.println(OutputBuffer);
                    OutputBuffer = "";
                }

                while ((InputLine = in.readLine()) != null && !AbortSession)
                {
                    OutputLine = ProcessInput(InputLine);
                    if ( !OutputLine.equals("") )
                        out.println(OutputLine);
                    if ( OutputLine.equals("BYE") )
                        break;

                    while(!in.ready() && !AbortSession)
                    {
                        OutputLine = OutputBuffer;
                        if ( OutputLine.length() > 0 )
                        {
                            out.println(OutputLine);
                            OutputBuffer = "";
                        }

                        Thread.sleep(50);
                        PingCheck += 50;

                        if (PingCheck >= PingMS) {
                            out.println("PING");
                            PingCheck = 0;
                            AbortSession = out.checkError();
                        }
                    }
                    PingCheck = 0;
                }

            } catch (Exception e)
            {
                System.err.println("Exception caught when trying to listen on port "
                    + PortNumber + " or listening for a connection");
                System.err.println(e.getMessage());
            }
        }
    }

    /**
     * Starts the Thread.  Sends a message to Logger to indicate
     * successful start and as to which PortNumber it is listening.
     *
     * @throws IOException Passthrough from Logger
     */
    public void start () throws IOException
    {
        if ( T == null )
        {
            try
            {
                Logger.getInstance().write("Listening for order flow on port " + PortNumber);
            }
            catch (IOException e)
            {
                throw e;
            }

            T = new Thread( this, ThreadName );
            T.start();
        }
    }
}

