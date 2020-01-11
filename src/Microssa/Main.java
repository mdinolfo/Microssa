/*
 * Main.java
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

import quickfix.*;
import java.io.FileInputStream;

import Microssa.MatchingEngine;
import Microssa.Logger;

/**
 * Static class that initializes the MatchingEngine, Logger, OrderSocket
 * and PriceSocket objects.
 *
 * @see MatchingEngine
 * @see Logger
 * @see OrderSocket
 * @see PriceSocket
 */
public class Main
{
    private static boolean keepRunning = true;
    
	public static void main (String args[])
    {
        try
        {
            final Thread mainThread = Thread.currentThread();
            Runtime.getRuntime().addShutdownHook( new Thread() {
                public void run(){
                    try {
                        keepRunning = false;
                        mainThread.join();
                    } catch ( Exception e ) {
                        // do nothing
                    }
                }  
            } );
            
            Configuration.initialize();
            Logger.initialize();

            MatchingEngine me = new MatchingEngine();

            OrderSocket os = new OrderSocket( "OS-MAIN", me );
            PriceSocket ps = new PriceSocket( "PS-MAIN", me );

            os.start();
            ps.start();

            if ( Configuration.getInstance().getString("USEDB").toUpperCase().equals("TRUE") ) {
                
                Database db = new Database( "DB-MAIN", me );
                
                db.start();
                
            }
            
            if ( Configuration.getInstance().getString("USEFIX").toUpperCase().equals("TRUE") ) {
                
                String FIXSettingsFilename = "../cfg/fix-session.cfg";
                
                Application application = new FIXInterface( me );
                
                SessionSettings settings = new SessionSettings( new FileInputStream( FIXSettingsFilename ) );
                MessageStoreFactory storeFactory = new FileStoreFactory( settings );
                LogFactory logFactory = new FileLogFactory( settings );
                MessageFactory messageFactory = new DefaultMessageFactory();
                Acceptor acceptor = new SocketAcceptor
                    ( application, storeFactory, settings, logFactory, messageFactory );
                
                acceptor.start();
                
                Logger.getInstance().write("Microssa is up.");

                while ( keepRunning ) {
                    Thread.sleep ( 100 );
                }
                
                Logger.getInstance().write("Received termination signal.");

                // orderly shutdown steps
                acceptor.stop();
                Logger.getInstance().write("Microssa shutdown complete.");
            }

		}
        catch ( Exception e )
        {
            System.err.println("Error initializing:\n" + e.getMessage() + "\n");
            e.printStackTrace();
            System.exit(1);
        }
        
	}
}

