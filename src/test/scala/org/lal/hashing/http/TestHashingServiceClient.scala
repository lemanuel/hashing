package org.lal.hashing.http
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.lal.hashing.Configuration
import org.lal.hashing.domain.{HashingJob, JsonHashingJob}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.SpanSugar
import org.scalatest.{FunSuite, Inside, Matchers}

import scala.collection.JavaConverters._
import scala.concurrent.Future

class TestHashingServiceClient extends FunSuite
  with Inside
  with ScalaFutures
  with Matchers
  with JsonHashingJob
  with SpanSugar {

  implicit val system = ActorSystem("test-client")
  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher

  def fixture =
    new {
      val job = HashingJob(id="job-1", lines = List("Test line", "Another one."))
      val client = new HashingServiceConnection()
      val configuration = new Configuration(ConfigFactory.load)
    }

  test("create the proper request") {
    println("Test for proper creation of request")
    val f = fixture
    import f._
    val request = client.createRequest(job, configuration)

    inside(request.futureValue) { case HttpRequest(HttpMethods.POST, uri, _, entity, _) =>
        uri.path should equal (Uri.Path("/api/service"))
        entity.contentType should equal (ContentTypes.`application/json`)
        Unmarshal(entity).to[HashingJob].futureValue should equal (job)
    }
  }

  test("Receive the proper response") {
    println("Test for proper response")
    val f = fixture
    import f._

    val jobResponse = HashingJob(id=job.id, lines = List("Hashed line", "Hashed line 2"))
    val exe: HttpRequest => Future[HttpResponse] = { _ =>
      for {
        entity <- Marshal(jobResponse).to[ResponseEntity]
      } yield HttpResponse(StatusCodes.Accepted, entity=entity)
    }

    client.getResponse(job, configuration, exe).futureValue should equal (jobResponse)
  }

  test("receive an different status code") {
    println("test for status code")
    val f = fixture
    import f._

    val jobResponse = HashingJob(id=job.id, lines = List("Hashed line", "Hashed line 2"))
    val exe: HttpRequest => Future[HttpResponse] = { _ =>
      Marshal(StatusCodes.ServiceUnavailable -> "error").to[HttpResponse]
    }

    val response = client.getResponse(job, configuration, exe)
    response.failed.futureValue(timeout(5 seconds), interval(5 millis)).isInstanceOf[RuntimeException]
  }

  test("integration test with hashing-as-a-service") {
    println("test for integration")
    val f = fixture
    import f._

    val response = client.getResponse(job, configuration, Http().singleRequest(_))
    whenReady(response, timeout(5 seconds), interval(5 millis)) { response =>
      response.id should equal (job.id)
      response.lines should have length job.lines.size
    }
  }
}
