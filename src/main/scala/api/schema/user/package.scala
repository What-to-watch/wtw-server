package api.schema

import caliban.{GraphQL, RootResolver}
import caliban.GraphQL.graphQL
import caliban.schema.GenericSchema
import services.auth
import services.auth.{Auth, BasicCredentials, getCredentials}
import services.users.{LoginError, UsersService, loginUser, registerUser}
import utils.AuthUtils
import zio.clock.Clock
import zio.{RIO, Task}

package object user {

  case class UserRegistrationArgs(
                               username: String,
                               email: String,
                               password: String
                             )
  case class Token(token: String)

  object UserSchema extends GenericSchema[Auth with UsersService] {

    type UserIO[A] = RIO[Auth with UsersService, A]

    case class Mutation(
                         register: UserRegistrationArgs => UserIO[Token],
                         login: UserIO[Token])

    val api: GraphQL[Auth with UsersService] = graphQL(RootResolver(
      Option.empty[Unit],
      Some(Mutation(
        register,
        login
      )),
      Option.empty[Unit]
    ))

    def register(userRegisterArgs: UserRegistrationArgs): UserIO[Token] = for {
      user <- registerUser(userRegisterArgs)
      token <- AuthUtils.buildToken(user).provideLayer(Clock.live)
    } yield Token(token)

    def login: UserIO[Token] = for {
      maybeCreds <- getCredentials
      basicCreds <- maybeCreds.fold[Task[BasicCredentials]]({ Task.fail(LoginError) }) {
        case auth.JwtCredentials(_, _) => Task.fail(LoginError)
        case auth.BasicCredentials(username, password) => Task.succeed(BasicCredentials(username, password))
      }
      user <- loginUser(basicCreds.username, basicCreds.password)
      token <- AuthUtils.buildToken(user).provideLayer(Clock.live)
    } yield Token(token)
  }

}