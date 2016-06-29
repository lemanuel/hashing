package org.lal.hashing.system

import akka.actor.{Status, Props, ActorSystem}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{StatusCodes, HttpResponse, HttpRequest}
import akka.stream.ActorMaterializer
import akka.testkit.{TestProbe, ImplicitSender, TestKit}
import com.typesafe.config.ConfigFactory
import org.lal.hashing.Configuration
import org.lal.hashing.domain.{JsonHashingJob, HashingJob}
import org.lal.hashing.http.HashingServiceConnection
import org.scalatest.{BeforeAndAfterAll, WordSpecLike, MustMatchers}
import scala.concurrent.Future
import scala.concurrent.duration._


class ActorsTest extends TestKit(ActorSystem("testSystem"))
  with MustMatchers
  with WordSpecLike
  with BeforeAndAfterAll
  with ImplicitSender
  with JsonHashingJob {

  override def afterAll {
    TestKit.shutdownActorSystem(system)
  }

  implicit val materializer = ActorMaterializer()
  implicit val ec = system.dispatcher
  implicit val ac = system

  def fixture =
    new {
      val client = new HashingServiceConnection()
      val configuration = new Configuration(ConfigFactory.load())
      val sinkOutput: OutputSink = { _ => () }
      val writer = system.actorOf(Props(classOf[Writer], sinkOutput))
    }

  def workerFixtureError =
    new {
      val superFixture = fixture
      import superFixture._

      val emptyExecutor: HttpRequest => Future[HttpResponse] = { _ =>
        println("One request")
        Marshal(StatusCodes.ServiceUnavailable -> "error").to[HttpResponse]
      }
      val worker = system.actorOf(Props(classOf[Worker], testActor, client, configuration, emptyExecutor))
    }

  def workerFixture =
    new {
      val superFixture = fixture
      import superFixture._

      val emptyExecutor: HttpRequest => Future[HttpResponse] = { request =>
        println(s"One request: ${request}")
        Marshal(StatusCodes.Accepted -> request.entity).to[HttpResponse]
      }
      val worker = system.actorOf(Props(classOf[Worker], testActor, client, configuration, emptyExecutor))
    }

  def masterFixture =
    new {
      val superFixture = fixture
      import superFixture._

      val master = system.actorOf(Props(classOf[Master], testActor))
      val longDocument: Document = ((1 to 10).toList map { e => e.toString}).iterator
      val smallDocument: Document = List("l1").iterator
    }

  "An Writer Actor" should {
    "forward to output sink" in {
      val f = fixture
      import f._
      within(100 millis) {
        writer ! WriteJob(HashingJob("0", List("")), testActor)
        expectMsg(WorkersAvailable(1))
      }
    }

    "buffer the output and return with the needed key" in {
      val f = fixture
      import f._
      within(100 millis) {
        writer ! WriteJob(HashingJob("1", List("")), testActor)
        writer ! WriteJob(HashingJob("2", List("")), testActor)
        writer ! WriteJob(HashingJob("0", List("")), testActor)
        expectMsg(WorkersAvailable(3))
      }
    }

    "forward only the consecutive ids" in {
      val f = fixture
      import f._
      within(100 millis) {
        writer ! WriteJob(HashingJob("1", List("")), testActor)
        writer ! WriteJob(HashingJob("5", List("")), testActor)
        writer ! WriteJob(HashingJob("8", List("")), testActor)
        writer ! WriteJob(HashingJob("0", List("")), testActor)
        expectMsg(WorkersAvailable(2))
      }
    }
  }

  "A Worker" should {
    "Stop after it exceed the attempt number" in {
      val f = workerFixtureError
      import f._
      within(10 seconds) {
        worker ! HashingJob("0", List(""))
        expectMsg(IDroppedJob(0))
      }
    }

    "Send job to writer" in {
      val f = workerFixture
      import f._
      within(10 seconds) {
        worker ! HashingJob("0", List(""))
        expectMsgType[WriteJob]
      }
    }
  }

  "A Master" should {
    "Not start 2 jobs" in {
      val f = masterFixture
      import f._
      import superFixture._

      within(10 seconds) {
        master ! StartJob(smallDocument, configuration.NoOfActiveJobs, configuration.BatchSize)
        expectMsgType[HashingJob]
        master ! StartJob(longDocument, configuration.NoOfActiveJobs, configuration.BatchSize)
        expectMsgType[Status.Failure]
      }
    }

    "Submit jobs after starting" in {
      val f = masterFixture
      import f._
      import superFixture._

      within(10 seconds) {
        master ! StartJob(longDocument, configuration.NoOfActiveJobs, configuration.BatchSize)
        expectMsg(HashingJob("0", Seq("1","2")))
        receiveN(2)
      }
    }

    "Stop after receiving IDroppedJob" in {
      val f = masterFixture
      import f._
      import superFixture._

      val testProbe = TestProbe()
      testProbe watch master
      master ! StartJob(longDocument, configuration.NoOfActiveJobs, configuration.BatchSize)
      master ! IDroppedJob(0)
      testProbe.expectTerminated(master, 10 seconds)
    }

    "Stop after the job was completed" in {
      val f = masterFixture
      import f._
      import superFixture._

      val testProbe = TestProbe()
      testProbe watch master
      master ! StartJob(smallDocument, configuration.NoOfActiveJobs, configuration.BatchSize)
      master ! WorkersAvailable(1)
      testProbe.expectTerminated(master, 10 seconds)
    }

    "Submit new jobs after the old ones" in {
      val f = masterFixture
      import f._
      import superFixture._

      within(10 seconds) {
        master ! StartJob(longDocument, configuration.NoOfActiveJobs, configuration.BatchSize)
        receiveN(3)
        master ! WorkersAvailable(2)
        receiveN(2)
      }
    }

  }

}
