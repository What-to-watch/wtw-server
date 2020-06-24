package services.users
import api.schema.user.UserRegistrationArgs
import doobie.free.connection.ConnectionIO
import doobie.util.transactor.Transactor
import doobie.implicits._
import doobie.util.query.Query0
import utils.AuthUtils
import zio.Task
import zio.interop.catz._

case class LiveUsersService(tnx: Transactor[Task]) extends Users.Service {
  import LiveUsersService._

  override def registerUser(userRegistrationArgs: UserRegistrationArgs): Task[User] = for {
    hashedPassword <- AuthUtils.hashPassword(userRegistrationArgs.password)
    user <-  SQL.registerUser(userRegistrationArgs, hashedPassword)
      .transact(tnx).mapError{ err => println(err);err }
  } yield user

  override def loginUser(email: String, password: String): Task[User] = for {
    userDB <- SQL.getUser(email).unique.transact(tnx)
    _ <- AuthUtils.checkPassword(password, userDB.password).filterOrFail(identity)(LoginError)
  } yield userDB.toUser
}

object LiveUsersService {
  object SQL {

    def registerUser(userRegistrationArgs: UserRegistrationArgs, hashedPassword: String): ConnectionIO[User] = {
      sql"INSERT INTO users (username, email, password) VALUES (${userRegistrationArgs.username}, ${userRegistrationArgs.email}, $hashedPassword)"
        .update
        .withUniqueGeneratedKeys("id", "username", "email")
    }

    def getUser(email: String): Query0[UserDB] =
      sql"SELECT id, username, email, password FROM users WHERE email = $email"
      .query[UserDB]
  }
}