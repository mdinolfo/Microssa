/*
 * PriceSocket.java
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
 * This class creates a TCP socket interface to allow market data
 * subscribe/unsubscribe requests to be sent to the MatchingEngine.  The
 * MatchingEngine replies with snapshots of the current prices in
 * the books.
 * This socket runs on its own thread.
 *
 * @see MatchingEngine
 */

public class PriceSocket implements Runnable {

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
    public PriceSocket ( String Name, MatchingEngine me )
    {
        ThreadName = Name;
        PortNumber = Configuration.getInstance().getInt("PRICEPORT");
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
     * message as a subscribe/unsubscribe request.  If all fails, returns
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

        if ( cmd.matches("(SUB|UNSUB)") ) {

            // valid command, reset disconnect counter
            ErrorCount = 0;

            for ( int x=1; x<token.length; x++)
            {
                if ( cmd.equals("SUB") )
                    ME.MarketDataSubscribe( token[x] );
                else
                    ME.MarketDataUnsubscribe( token[x] );
            }

            return "";
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
        ME.SetPriceSocket(this);

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
                e.printStackTrace();
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
                Logger.getInstance().write("Listening for price subscriptions on port " + PortNumber);
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

