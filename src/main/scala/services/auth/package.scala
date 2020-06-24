package services

import api.schema.GraphqlEncodableError
import config.AuthConfig
import zio.config.{Config, config}
import zio.{Has, RIO, RLayer, Task, ZIO, ZLayer}

package object auth {

  sealed trait Credentials
  case class JwtCredentials(rawToken: String, id: Int) extends Credentials
  case class BasicCredentials(username: String, password: String) extends Credentials

  sealed trait AuthError extends GraphqlEncodableError
  case object InvalidJwt extends AuthError {
    override def errorCode: String = "INVALID-JWT"
  }
  case object ExpiredJwt extends AuthError {
    override def errorCode: String = "EXPIRED-JWT"
  }

  type Auth = Has[Auth.Service]

  object Auth {
    trait Service {
      def getSecret:Task[String]
      def getCredentials:Task[Option[Credentials]]
      def isAuthenticated: Task[Boolean] = getCredentials.map(optCred => optCred.map {
        case JwtCredentials(_, _) => true
        case _ => false
      }).map(_.fold({false})(identity))
      def getUserId: Task[Option[Int]] = getCredentials.map(_.flatMap {
        case JwtCredentials(_, id) => Some(id)
        case _ => None
      })
      def doesIdMatch(id:Int): Task[Boolean] = getCredentials
        .map(_.flatMap {
          case JwtCredentials(_, credId) => Some(credId == id)
          case _ => None
        })
        .map(_.fold({false})(identity))
    }

    def live(credentials: Task[Option[Credentials]]): RLayer[Config[AuthConfig], Auth] = ZLayer.fromEffect(for {
      authConfig <- config[AuthConfig]
    } yield SimpleAuthService(authConfig.auth.secret, credentials))
  }

  def getSecret: RIO[Auth, String] = ZIO.accessM(_.get.getSecret)
  def getCredentials: RIO[Auth, Option[Credentials]] = ZIO.accessM(_.get.getCredentials)

  def isAuthenticated: RIO[Auth, Boolean] = ZIO.accessM(_.get.isAuthenticated)
  def getUserId: RIO[Auth, Option[Int]] = ZIO.accessM(_.get.getUserId)
  def doesIdMatch(id: Int): RIO[Auth, Boolean] = ZIO.accessM(_.get.doesIdMatch(id))
}