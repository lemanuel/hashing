package org.lal.hashing

import akka.actor.ActorRef
import org.lal.hashing.domain.HashingJob

package object system {
  type Document = Iterator[String]
  type Batch = Seq[String]
  type DocumentBatches = Iterator[Batch]
  type OutputSink = (Batch => Unit)

  case class StartJob(document: Document, noOfMaximumJobs: Int, batchSize: Int)
  case class WriteJob(hashingJob: HashingJob, master: ActorRef)
  case class WorkersAvailable(noOfAvailableJobs: Int)
  case class StartSystem(configuration: Configuration)
  case class IDroppedJob(id: Int)

}
