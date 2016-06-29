package org.lal.hashing.system

import java.io.{File, PrintWriter}

import akka.actor._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.routing.RoundRobinPool
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import org.lal.hashing.Configuration
import org.lal.hashing.http.HashingServiceConnection

import scala.concurrent.Future

class Supervisor(in: String, out: String) extends Actor with ActorLogging {
  val WriterName = "writer"
  val WorkersPoolName = "workers"
  val MasterName = "master"

  lazy val source = io.Source.fromFile(in)
  lazy val output = new PrintWriter(new File(out))
  implicit val system = context.system
  implicit val materializer = ActorMaterializer()

  override def receive: Receive = {
    case StartSystem(configuration) =>
      startUpSystem(configuration)
    case Terminated(_) =>
      shutDownSystem
  }


  def createSinkFunction(out: java.io.Writer): OutputSink = { batch =>
    out.write(batch.mkString("\n"))
    out.write("\n")
  }

  def createExecutor(implicit configuration: Configuration): HttpRequest => Future[HttpResponse] = {
    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] =
      Http().outgoingConnection(configuration.RestServiceHost, configuration.RestServicePort)

    val executor: HttpRequest => Future[HttpResponse] = (request) =>
      Source.single(request)
        .via(connectionFlow)
        .runWith(Sink.head)

    executor
  }

  def createWriter = {
    val sinkOutput = createSinkFunction(output)
    context.actorOf(Props(classOf[Writer], sinkOutput), WriterName)
  }

  def createWorkersPool(writer: ActorRef, connection: HashingServiceConnection)
                       (implicit configuration: Configuration) = {

    val executor = createExecutor
    context.actorOf(
      RoundRobinPool(5).props(
        Props(classOf[Worker], writer, connection, configuration, executor)), WorkersPoolName)
  }

  def createMaster(workersPool: ActorRef) =
    context.actorOf(Props(classOf[Master], workersPool), MasterName)


  def startUpSystem(implicit configuration: Configuration) = {
    val connection = new HashingServiceConnection()
    val writer = createWriter
    val workersPool = createWorkersPool(writer, connection)
    val master = createMaster(workersPool)
    startUpMaster(master)
  }

  def startUpMaster(master: ActorRef)(implicit configuration: Configuration) = {
    val document: Document = source.getLines
    master ! StartJob(document, configuration.NoOfActiveJobs, configuration.BatchSize)
    context watch master
  }

  def shutDownSystem = {
    log.info("The master has terminated.")
    output.close()
    source.close()
    context.stop(context.parent)
  }
}
