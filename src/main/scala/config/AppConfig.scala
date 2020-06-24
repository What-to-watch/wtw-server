package config

import zio.config._

case class AppConfig(apiConfig: EndpointConfig, postgresConfig: PostgresConfig, authConfig: AuthConfig)
object AppConfig {
  val descriptor: ConfigDescriptor[AppConfig] =
    (ApiConfig.descriptor |@| PostgresConfig.descriptor |@| AuthConfig.descriptor)(AppConfig.apply, AppConfig.unapply)
}
