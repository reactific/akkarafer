# akkarafer
An SBT Plugin to facilitate creation of projects that integrate Akka, Karaf Cellar, Felix and Hazelcast. This 
combination of libraries provides a common foundation for clustered data grid applications that have REST interfaces,
process streams, must have high availability, and utilize in-memory storage for high throughput and low latency. The
intent is to make these Java tools more easily accessible from Scala and buildable by SBT while also providing optional 
integration assistance in the form of additional dependent packages that can be added to the resulting project.
