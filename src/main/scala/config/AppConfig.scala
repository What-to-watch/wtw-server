package config

import zio.config._, ConfigDescriptor._

case class AppConfig(apiConfig: EndpointConfig, postgresConfig: PostgresConfig)
object AppConfig {
  val descriptor: ConfigDescriptor[AppConfig] =
    (ApiConfig.descriptor |@| PostgresConfig.descriptor)(AppConfig.apply, AppConfig.unapply)
}
