/*
 * Hooks.java
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

import Microssa.Order;

/**
 * These are customizable hooks to notify another system of the activities
 * of the MatchingEngine.  They were intentionally left blank.
 * Examples of use would be to feed a ticker, database, compliance, or
 * middle/back office system.
 *
 * @see MatchingEngine
 */
public class Hooks {

    /**
     * This is a static class.  The constructor is Protected so it
     * can never be instantiated from outside of the class.
     */
    protected Hooks ()
    {

    }

    /**
     * Called when a new Order reaches the MatchingEngine.
     *
     * @param   NewOrder    The new Order object
     */
    public static void NewOrderHook ( Order NewOrder )
    {

    }

    /**
     * Called when an order has been amended in the MatchingEngine.
     *
     * @param   OldOrder    The Order object before the amendment
     * @param   NewOrder    The Order object after the amendment
     */
    public static void AmendOrderHook ( Order OldOrder, Order NewOrder )
    {

    }

    /**
     * Called when an order has been explicitly canceled in the
     * MatchingEngine.
     *
     * @param   CanOrder    The Order object being canceled
     */
    public static void CancelOrderHook ( Order CanOrder )
    {

    }

    /**
     * Called when an IOC order has available quantity after attempting
     * to execute.  Unimplemented for GFD orders.
     *
     * @param   ExpOrder    The Order object that is expired
     */
    public static void OrderExpireHook ( Order ExpOrder )
    {

    }

    /**
     * Called when an order is fully filled.
     *
     * @param   CompOrder    The Order object that is completed
     */
    public static void OrderCompleteHook ( Order CompOrder )
    {

    }

    /**
     * Called when a new Order is rejected by the MatchingEngine.
     *
     * @param   NewOrder    The new Order object
     * @param   RejectText  Reason for the reject
     */
    public static void NewOrderRejectHook ( Order NewOrder, String RejectText )
    {

    }

    /**
     * Called when an order amendment fails.
     *
     * @param   OldOrder    The Order object before the amendment
     * @param   NewOrder    The Order object after the amendment
     * @param   RejectText  Reason for the reject
     */
    public static void AmendOrderRejectHook ( Order OldOrder, Order NewOrder, String RejectText )
    {

    }

    /**
     * Called when an order cancel fails.
     *
     * @param   CanOrder    The Order object being canceled
     * @param   RejectText  Reason for the reject
     */
    public static void CancelOrderRejectHook ( Order CanOrder, String RejectText )
    {

    }

    /**
     * Called when a match happens.
     *
     * @param   aOrder  The aggressive (or, new/amended) order
     * @param   pOrder  The passive (or, one in the book) order
     * @param   Price   The execution price.  May be different from the either
     * orders' because of price improvement
     * @param   Quantity The Amount of the execution
     */
    public static void ExecutionHook ( Order aOrder, Order pOrder,
                                       Double Price, Double Quantity)
    {

    }
}

