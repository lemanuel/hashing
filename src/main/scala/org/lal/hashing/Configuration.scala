package org.lal.hashing

import com.typesafe.config.Config

class Configuration(config: Config) {
  val RestServiceHost = config.getString("service.http.host")
  val RestServicePort = config.getInt("service.http.port")
}
