package config

import zio.config._, ConfigDescriptor._

case class ApiConfig(endpoint: EndpointConfig)
case class EndpointConfig(host: String, port: Int)
object ApiConfig {
  val descriptor: ConfigDescriptor[EndpointConfig] =
    (string("host").default("0.0.0.0") |@| int("PORT").default(3000))(EndpointConfig.apply, EndpointConfig.unapply)
}
