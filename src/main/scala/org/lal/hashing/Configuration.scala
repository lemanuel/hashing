package org.lal.hashing

import com.typesafe.config.Config

class Configuration(config: Config) {
  val RestServiceHost = config.getString("service.http.host")
  val RestServicePort = config.getInt("service.http.port")

  val NoOfActiveJobs = config.getInt("app.noOfActiveJobs")
  val BatchSize = config.getInt("app.batchSize")
  val NoOfAttempts = config.getInt("app.noOfAttempts")
}
