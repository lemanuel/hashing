package org.lal.hashing.system

import akka.actor.{Actor, ActorLogging, ActorRef, Status}
import org.lal.hashing.domain.HashingJob

class Master(workersPool: ActorRef) extends Actor with ActorLogging{
  log.debug(s"Master created")

  override def receive: Receive = {
    case StartJob(document, noOfMaximumJobs, batchSize) =>
      startJob(document, noOfMaximumJobs, batchSize)
  }

  def supervisor(documentBatches: DocumentBatches, noOfActiveJobs: Int, nextId: Int): Receive = {
    case _: StartJob =>
      log.info("Receive another document but I am already working => refuse the work.")
      sender ! Status.Failure(new Exception("Already working. Unable to process another document. Try later."))
    case IDroppedJob(jobId) =>
      log.error(s"Actor with job ${jobId} has stop his job because it tried several times, but it did not succeed.")
      stop
    case WorkersAvailable(jobsToSubmit) if (noOfActiveJobs <= 1) =>
      stop
    case WorkersAvailable(noOfFreeJobs) =>
      submitNewJobs(documentBatches, noOfActiveJobs, nextId, noOfFreeJobs)
   }


  def submitNewJobs(documentBatches: DocumentBatches, noOfActiveJobs: Int, nextId: Int, noOfFreeJobs: Int) = {
    log.info(s"${noOfFreeJobs} jobs can be submitted")
    val activeCount = noOfActiveJobs - noOfFreeJobs
    val (submittedJobs, newId) = submitBatches(documentBatches, noOfFreeJobs, nextId)
    log.info(s"Number of active jobs is ${submittedJobs + activeCount}")

    if (activeCount + submittedJobs > 0)
      context become supervisor(documentBatches, submittedJobs + activeCount, newId)
    else
      stop
  }


  def stop = {
    log.info("No more work to be done. Stop")
    context stop self
  }

  def createBatches(document: Document, batchSize: Int): DocumentBatches =
    document grouped batchSize

  def submitBatches(batches: DocumentBatches, noOfJobs: Int, startJobId: Int): (Int,Int) = {
    def iterate(submittedJobs: Int, nextId: Int): (Int, Int) = {
      if (noOfJobs == submittedJobs)
        (submittedJobs, nextId)
      else if (batches.hasNext) {
        workersPool ! HashingJob(nextId.toString, batches.next())
        iterate(submittedJobs + 1, nextId + 1)
      } else
        (submittedJobs, nextId)
    }

    iterate(0, startJobId)
  }

  def startJob(document: Document, noOfMaximumJobs: Int, batchSize: Int) = {
    log.info("Receive the document. Start to process.")
    val documentBatches = createBatches(document, batchSize)
    val (submittedJobs, nextId) = submitBatches(documentBatches, noOfMaximumJobs, 0)
    log.info(s"${submittedJobs} jobs have be submitted")
    context become supervisor(documentBatches, submittedJobs, nextId)
  }
}
