mapreduce-java
==============

This sample is a work in progress.


Getting started
---------------

To use, you will need to download `Apache Maven <http://maven.apache.org/download.cgi>`_. Then run a local server::

  cd mapreduce-java
  mvn appengine:devserver

(note: currently authentication fails when running locally. fix coming)

Before deploying, make sure the constants in the top of MainServlet are set to correct values
(bucket_name, api_key, etc). The application tag in appengine-web.xml also needs to be set to a valid
app engine project ID.

Once that's done, deploy with::

  mvn appengine:update


Code layout
-----------

Most of the Java code is a generated client library. This includes everything under
``com/google/api/services``. (This will eventually be replaced by a Maven dependency)

MainServlet.java:
    currently all of the mapreduce code is in this file.

WEB-INF/appengine-web.xml:
    is the appengine specific config, make sure to replace the dummy app engine project ID with your own value.

WEB-INF/web.xml
    sets up the 3 servlets used by this application. 2 are handled by common app engine code.