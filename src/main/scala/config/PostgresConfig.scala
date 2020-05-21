package config

import zio.config._, ConfigDescriptor._

case class PostgresConfig(db: DatabaseConfig)

case class DatabaseConfig(host: String, port: Int, dbname: String, user: String, password: String)

object PostgresConfig {
  val dbConfig: ConfigDescriptor[DatabaseConfig] = (string("host") |@| int("port") |@| string("dbname")
    |@| string("user") |@| string("password")) (DatabaseConfig.apply, DatabaseConfig.unapply)

  val descriptor: ConfigDescriptor[PostgresConfig] = (nested("postgres") {
    dbConfig
  }) (PostgresConfig.apply, PostgresConfig.unapply)
}
