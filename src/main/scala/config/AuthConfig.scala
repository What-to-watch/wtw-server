package config
import zio.config._, ConfigDescriptor._

case class AuthConfig(auth: SecretConfig)

case class SecretConfig(secret: String)

object AuthConfig {
  val secretConfig: ConfigDescriptor[SecretConfig] = string("secret")(SecretConfig.apply, SecretConfig.unapply)

  val descriptor: ConfigDescriptor[AuthConfig] = (nested("auth") {
    secretConfig
  }) (AuthConfig.apply, AuthConfig.unapply)
}