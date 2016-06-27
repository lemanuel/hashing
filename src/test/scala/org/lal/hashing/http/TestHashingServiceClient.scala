package org.lal.hashing.http
import akka.actor.ActorSystem
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.Uri.{Host, Authority}
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.lal.hashing.Configuration
import org.lal.hashing.domain.{JsonHashingJob, HashingJob}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, FunSuite, Inside}
import scala.collection.JavaConverters._

import scala.concurrent.Future

class TestHashingServiceClient extends FunSuite with Inside with ScalaFutures with Matchers with JsonHashingJob {
  implicit val system = ActorSystem("test-client")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  test("create the proper request") {
    val job = HashingJob(id="job-1", lines = List("Test line"))
    val client = new HashingServiceClient()
    val config = ConfigFactory.parseMap(
      Map("service.http.host" -> "0.0.0.0",
        "service.http.port" -> new Integer(9000)).asJava)
    val configuration = new Configuration(config)

    val request = client.createRequest(job, configuration)

    inside(request.futureValue) { case HttpRequest(HttpMethods.POST, uri, _, entity, _) =>
        uri.path should equal (Uri.Path("/api/service"))
        entity.contentType should equal (ContentTypes.`application/json`)
        Unmarshal(entity).to[HashingJob].futureValue should equal (job)
    }
  }

  test("send the proper request") {
    val job = HashingJob(id="job-1", lines = List("Test line"))
    val client = new HashingServiceClient()
    val config = ConfigFactory.parseMap(
      Map("service.http.host" -> "0.0.0.0",
        "service.http.port" -> new Integer(9000)).asJava)
    val configuration = new Configuration(config)

    val jobResponse = HashingJob(id="job-1", lines = List("Hashed line"))
    val exe: HttpRequest => Future[HttpResponse] = { _ =>
      for {
        entity <- Marshal(jobResponse).to[ResponseEntity]
      } yield HttpResponse(entity=entity)
    }

    client.getResponse(job, configuration, exe).futureValue should equal (jobResponse)
  }

}
