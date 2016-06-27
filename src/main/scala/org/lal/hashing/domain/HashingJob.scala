package org.lal.hashing.domain

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import spray.json.DefaultJsonProtocol

case class HashingJob(id: String, lines: Seq[String])

trait JsonHashingJob extends SprayJsonSupport with DefaultJsonProtocol {
  implicit val jsonFormat = jsonFormat2(HashingJob)
}
