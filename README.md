Hashing-as-a-Service
=====================

This is a simple application that can hash entire files line by line.
The system is reading the data in batches and will generate for each batch the corresponding hashes and then will write the date in a new file.
To be more practical, the application is using a REST service, "Hashing-as-a-Service", to compute the hashes.

Configuration
-------------
In the file application.conf there are some configuration that can be made.  
1. service/http/host -- The address for the Hashing-as-a-Service" service  
2. service/http/port -- The port for the Hashing-as-a-Service" service  
3. app/noOfActiveJobs -- How many jobs can be started concurrently  
4. app/batchSize      -- The batch size, given in number of lines that will be read  
5. app/noOfAttempts   -- The number of attempts a worker will make for the same job, if some error exists.  
                      If the worker exceeds this limit, the system will be stopped.  


How To Run
----------

Please ensure that you have [SBT](http://www.scala-sbt.org/) installed, along with its dependencies.
Then start the "Hashing-as-a-Service" service by following the instruction from ["Hashing-as-a-Service"](https://github.com/adilakhter/hashing-as-a-service).

After that:

```
$ git clone https://github.com/lemanuel/hashing.git
$ cd hashing
$ sbt "run <inputFile> <outputFile> "

```
