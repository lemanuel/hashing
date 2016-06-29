package org.lal.hashing

import akka.actor.{ActorSystem, Props}
import akka.stream.ActorMaterializer
import com.typesafe.config.{ConfigException, ConfigFactory}
import org.lal.hashing.system.{StartSystem, Supervisor}

import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.control.Exception._

/**
  * The entry point for application.
  * Will create the ActorSystem, and then send the Start Signal.
  */
object ComputeHash {
  case class Arguments(in: String = "", out: String = "")

  implicit val system = ActorSystem("compute-hash")
  implicit val materializer = ActorMaterializer()

  lazy val parser = createArgumentsParser

  def createArgumentsParser = new scopt.OptionParser[Arguments]("compute-hash") {
    arg[String]("in").action( (x, c) => c.copy(in = x) ).
      text("in is a required string property")
    arg[String]("out").action( (x, c) => c.copy(out = x) ).
      text("out is a required string property")

    help("help").text("prints this usage text")
  }

  def startUpSystem(configuration: Configuration, arguments: Arguments) = {
    val supervisor = system.actorOf(Props(classOf[Supervisor], arguments.in, arguments.out), "supervisor")
    supervisor ! StartSystem(configuration)
  }

  def shutDownSystem(error: Throwable) = {
    println(s"Unable to start system. ${error.getMessage}")
    system.terminate()
    Await.result(system.whenTerminated, 30 seconds)
  }

  def main(args: Array[String]): Unit = {
    parser.parse(args, Arguments()) match {
      case Some(args) =>
        catching(classOf[ConfigException]).either(new Configuration(ConfigFactory.load)) match {
          case Right(configuration) =>
            startUpSystem(configuration, args)
          case Left(error) =>
            shutDownSystem(error)
        }
      case None =>
        shutDownSystem(new RuntimeException("Missing arguments"))
    }
  }
}
