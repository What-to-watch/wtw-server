package config

import zio.config._, ConfigDescriptor._

case class MLApiConfig(url: String)
object MLApiConfig {
  val descriptor: ConfigDescriptor[MLApiConfig] =
    string("ml_api").default("localhost:5000")(MLApiConfig.apply, MLApiConfig.unapply)
}
