mapreduce-java
==============

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
    
    
Project status
--------------

Goals
~~~~~
* Provide a real world MapReduce example that uses the Genomics API (in Java).
* Prove that a MapReduce is both feasible and a good idea for Genomics data. 
  The resulting analysis should be useful.


Current status
~~~~~~~~~~~~~~
This code is in active development. Currently, it generates a similarity matrix for Variant data. 
The Variant APIs are currently in trusted tester mode, so this sample isn't widely runnable at this time.

TODOs include:

* Running over all Variants in a Dataset, not just a small shard of data
* Outputting the PCA data as JSON so that a d3 graph can be displayed to the user 
* Performance improvements
* The ability to run locally!
* Better parameterization of user specific values (bucket, apikey, etc)
