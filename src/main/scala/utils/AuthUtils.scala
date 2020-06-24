package utils

import java.util.concurrent.TimeUnit

import org.http4s.Credentials.{Token => Http4sToken}
import org.http4s.{AuthScheme, Headers, BasicCredentials => Http4sBasicCredentials, Credentials => Http4sCredentials}
import org.http4s.headers.Authorization
import org.mindrot.jbcrypt.BCrypt
import pdi.jwt.{Jwt, JwtAlgorithm, JwtClaim}
import pdi.jwt.exceptions.JwtExpirationException
import services.auth.{BasicCredentials, Credentials, ExpiredJwt, InvalidJwt, JwtCredentials}
import services.users.User
import zio.clock.Clock
import zio.clock.currentTime
import zio.{RIO, Task, UIO}

case object AuthUtils {

  def hashPassword(password: String): Task[String] = Task.effect {
    BCrypt.hashpw(password, BCrypt.gensalt())
  }

  def checkPassword(plain: String, hashed: String): UIO[Boolean] = Task.effectTotal {
    BCrypt.checkpw(plain, hashed)
  }

  def buildToken(user:User): RIO[Clock, String] = for {
      now <- currentTime(TimeUnit.SECONDS)
      claims = JwtClaim()
        .by("wtw-server")
        .to("wtw-front")
        .issuedAt(now)
        .expiresAt(now + (60 * 60 * 24 * 10))
        .about(user.id.toString) ++ (("username", user.username), ("email", user.email))
      secret = "SecretForToken"
      alg = JwtAlgorithm.HS256
    token <- Task.effect{ Jwt.encode(claims, secret, alg) }
    } yield token

  def extractCredentialsFromHeaders(headers: Headers): Task[Option[Credentials]] = {
    headers.get(Authorization).fold[Task[Option[Credentials]]]({ Task.succeed(None)}) { auth =>
      auth.credentials match {
        case Http4sBasicCredentials(username, password) => Task.succeed(Some(BasicCredentials(username, password)))
        case Http4sToken(AuthScheme.Bearer, token) => parseTokenToJwtCredentials(token).map(Option(_))
        case _ => Task.succeed(None)
      }
    }.mapError {
      case _: JwtExpirationException => ExpiredJwt
      case err => err
    }
  }

  private def parseTokenToJwtCredentials(token: String): Task[JwtCredentials] = for {
    claims <- Task.fromTry(Jwt.decode(token, "SecretForToken", Seq(JwtAlgorithm.HS256)))
    subject <- Task.require(InvalidJwt)(Task.succeed(claims.subject))
  } yield JwtCredentials(token, subject.toInt)
}