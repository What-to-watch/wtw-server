package services

import api.schema.GraphqlEncodableError
import api.schema.user.UserRegistrationArgs
import persistence.TaskTransactor
import zio.{Has, RIO, RLayer, Task, ZIO, ZLayer}

package object users {

  type UsersService = Has[Users.Service]

  case class User(id:Int, username: String, email: String)
  case class UserDB(id: Int, username: String, email: String, password: String) {
    def toUser: User = User(id, username, email)
  }

  sealed trait UserError extends GraphqlEncodableError
  case object RegisterError extends UserError {
    override def errorCode: String = "REGISTER-ERROR"
  }
  case object LoginError extends UserError {
    override def errorCode: String = "LOGIN-ERROR"
  }
  case object UnauthorizedError extends UserError {
    override def errorCode: String = "UNAUTHORIZED-ERROR"
  }

  object Users {
    trait Service {
      def registerUser(userRegistrationArgs: UserRegistrationArgs): Task[User]
      def loginUser(email: String, password: String): Task[User]
    }

    val live: RLayer[TaskTransactor, UsersService] =
      ZLayer.fromService(transactor => LiveUsersService(transactor))

  }

  def registerUser(userRegistrationArgs: UserRegistrationArgs): RIO[UsersService, User] =
    ZIO.accessM(_.get.registerUser(userRegistrationArgs))

  def loginUser(email: String, password: String): RIO[UsersService, User] =
    ZIO.accessM(_.get.loginUser(email, password))
}