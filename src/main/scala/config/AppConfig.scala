package config

import zio.config._

case class AppConfig(apiConfig: EndpointConfig, mlApiConfig: MLApiConfig, postgresConfig: PostgresConfig, authConfig: AuthConfig)
object AppConfig {
  val descriptor: ConfigDescriptor[AppConfig] =
    (ApiConfig.descriptor |@| MLApiConfig.descriptor |@| PostgresConfig.descriptor |@| AuthConfig.descriptor)(AppConfig.apply, AppConfig.unapply)
}