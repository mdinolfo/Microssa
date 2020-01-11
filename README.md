# Microssa
Microssa is a free matching engine written in Java which includes a FIX engine and database integration.

This software could be used to trade FX, stocks, or Bitcoin.  It could be used to simulate a market and events for a virtual world, or to train a learning algorithm.  Ultimately, it can be used for any purpose you please.

See Documentation in docs folder.

This project is licensed under GPL v3, and was compiled and run using OpenJDK 8.

Dependencies are Quickfix/J all-1.6.4, mina 2.0.17, slf4j api/jdk 1.7.25. Please place their respective jars in the lib directory otherwise Microssa will not compile and/or run.

Optional dependencies are make, expect, texlive, the DejaVu Sans font, LibreOffice, MySQL, and MySQL Connector/J.


Version 1.4
----

* Modern Java pass


Version 1.3
----

* FIX Engine

* Tidy up directory structure, generate documentation from Makefile

* Update test cases to not to send SOURCE on orders as the source is stamped by Microssa

* Cleanup: update documentation to include details on database dependencies

* Internal order IDs

* Bugfix mysql test and order polling

* Orderly shutdown procedure for all components

* Documentation pass for FIX support including dependencies


Version 1.2
----

* Database interfaces for order entry and reporting

* Dark pool mode

* Trade reports

* Match criteria expanded to include minimum fill quantity and currency

* PDF documentation: add new fields, improve clarity of various sections

* Cleanup: shorten import lists

* Bugfix: Orders with available quantity less than min fill quantity should complete

* Bugfix: Check min fill quantity both ways before matching

* Bugfix: remove test cases that check SOURCE amend rejects


Version 1.1
----

* Configuration file for valid symbols, maximum order quantity, price port, order port, and log file name

* Matching engine support for maximum order quantity and valid symbols

* Bugfix: Modify testing suite to better identify reject messages on the order port

* Bugfix: Connections were not cleaning up if abruptly ended.  Added outbound PING messages to test if the connection is up.  The interval can be set in the configuration file


Version 1.0
----

* Product agnostic matching engine

* Partial executions

* Order expiry (IOC or GFD)

* Event hooks for custom functions (ex. Bitcoin wallet transfer, booking, broadcast)

* Order entry socket

* Market data socket

* Logging

* Code Documentation

* Product Documentation

* Automated test suite


Planned Features for Version 1.4
----

* Convert testing suite from expect to JUnit

* Convert build from make's Makefile to ant's build.xml

* Low latency pass - GC tuning


Planned Features for Version 1.5
----

* Order audit table in database to track order states

* Database resiliency for intraday disconnects

* SSL database connections

* Ciphertext password in configuration

* GTC/GTD order types

* Store non-expired orders in database at before shutdown


Planned Features for Version 1.6
----

* Multi-threaded Matching Engine

* Multiple FIX session/version support

* Automated FIX test

* Improve clarity of PDF Documentation
