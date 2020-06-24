package services.auth

import zio.Task

case class SimpleAuthService(secret: String, credentials: Task[Option[Credentials]]) extends Auth.Service {
  override def getSecret: Task[String] = Task.succeed(secret)
  override def getCredentials: Task[Option[Credentials]] = credentials
}