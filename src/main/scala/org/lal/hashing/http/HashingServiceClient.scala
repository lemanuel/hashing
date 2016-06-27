package org.lal.hashing.http

import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.Uri.Authority
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import org.lal.hashing.Configuration
import org.lal.hashing.domain.{HashingJob, JsonHashingJob}

import scala.concurrent.Future

class HashingServiceClient(implicit system: ActorSystem, materializer: Materializer) extends JsonHashingJob{
  import system.dispatcher

  def createRequest(hashingJob: HashingJob, configuration: Configuration): Future[HttpRequest] =
    for {
      entity <- Marshal(hashingJob).to[RequestEntity]
      authority = Authority(host = Uri.Host(configuration.RestServiceHost), port = configuration.RestServicePort)
      uri = Uri(scheme = "http", authority=authority, path=Uri.Path("/api/service"))
    } yield HttpRequest(HttpMethods.POST, uri=uri, entity=entity)

  def checkResponse(response: HttpResponse): Future[ResponseEntity] = response match {
    case HttpResponse(StatusCodes.OK, _, entity, _) => Future.successful(entity)
    case HttpResponse(statusCode, _, _, _) => Future.failed(new RuntimeException(s"Error status code:  $statusCode"))
  }

  def getResponse(hashingJob: HashingJob, configuration: Configuration, exe: HttpRequest => Future[HttpResponse]): Future[HashingJob] =
    for {
      request <- createRequest(hashingJob, configuration)
      response <- exe(request)
      entity <- checkResponse(response)
      entity <- Unmarshal(entity).to[HashingJob]
    } yield entity
}
