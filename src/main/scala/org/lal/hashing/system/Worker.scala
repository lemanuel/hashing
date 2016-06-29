package org.lal.hashing.system

import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import org.lal.hashing.Configuration
import org.lal.hashing.domain.HashingJob
import org.lal.hashing.http.HashingServiceConnection

import scala.concurrent.Future
import scala.util.{Failure, Success}

class Worker(writer: ActorRef,
             connection: HashingServiceConnection,
             configuration: Configuration,
             executor: HttpRequest => Future[HttpResponse]) extends Actor with ActorLogging{
  log.info("Worker created")

  override def receive: Receive = {
    case job: HashingJob =>
      log.info(s"receive job with id ${job.id} and ${job.lines.size} lines.")
      makeRequest(sender, job, configuration.NoOfAttempts)
  }

  def makeRequest(master: ActorRef, job: HashingJob, noOfAttempt: Int): Unit = {
    implicit val ec = context.dispatcher

    if (noOfAttempt == 0) {
      master ! IDroppedJob(job.id.toInt)
    } else {
      val response = connection.getResponse(job, configuration, executor)

      response onComplete {
        case Success(r) =>
          log.info(s"Finish hashing job with id ${r.id}")
          writer ! WriteJob(r, master)

        case Failure(e) =>
          log.error(s"Receive an error from service. ${e.getMessage}")
          makeRequest(master, job, noOfAttempt - 1)
      }
    }
  }

}
