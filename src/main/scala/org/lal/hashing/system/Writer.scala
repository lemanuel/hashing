package org.lal.hashing.system

import akka.actor.{Actor, ActorLogging}
import org.lal.hashing.domain.HashingJob

import scala.collection.mutable

/**
  * The Companion Object for the Writer.
  */
object Writer {
  type Buffer = mutable.PriorityQueue[(Int, Batch)]

  def createEmptyBuffer:Buffer = mutable.PriorityQueue()(Ordering.by[(Int, Batch), Int](_._1).reverse)

  def take(buffer: Buffer, neededKey: Int): List[Batch] = {
    def iterate(neededKey: Int, acc: List[Batch]): List[Batch] = {
      if (buffer.isEmpty || buffer.head._1 != neededKey)
        acc.reverse
      else
        iterate(neededKey + 1, buffer.dequeue()._2 :: acc)
    }

    iterate(neededKey, List())
  }
}

/**
  * The actor responsible with sending the batches computed to an OutputSink.
  * It has the responsibility to write the date in order, based on the batch id.
  * If it will receive later batches that are computed, it will create a buffer
  * from where it can extract ordered batches.
  * The Writer will notify the Master how many batches were written or not.
  * @param sinkFunction
  */
class Writer(sinkFunction: OutputSink) extends Actor with ActorLogging{
  log.debug("Writer created.")

  val buffer = Writer.createEmptyBuffer
  var neededKey = 0

  override def receive: Receive = {
    case WriteJob(job, master) =>
      log.info(s"received a job to write with id ${job.id} and connection to ${master}")
      val noOfReleasedBatches = sendBatchToSink(job)
      log.info(s"finish sending ${noOfReleasedBatches} jobs to sink")
      neededKey = neededKey + noOfReleasedBatches
      if (noOfReleasedBatches > 0)
        master ! WorkersAvailable(noOfReleasedBatches)
  }

  def sendBatchToSink(job: HashingJob): Int = {
    if (neededKey == job.id.toInt) {
      sinkFunction(job.lines)
      val availableToWrite = Writer.take(buffer, neededKey + 1)
      if (availableToWrite.isEmpty)
        1
      else {
        for(lines <- availableToWrite) sinkFunction(lines)
        1 + availableToWrite.size
      }
    } else {
      buffer += ((job.id.toInt, job.lines))
      0
    }
  }
}
