package api.schema

import java.sql.Timestamp
import java.time.Instant
import java.util.concurrent.TimeUnit

import api.schema.user.UserSchema.UserIO
import caliban.{GraphQL, RootResolver}
import caliban.GraphQL.graphQL
import caliban.schema.GenericSchema
import services.auth
import services.auth.{Auth, BasicCredentials, JwtCredentials, getCredentials}
import services.ratings.{Rating, RatingsService, postRating}
import services.users.{LoginError, UnauthorizedError, UsersService, loginUser, registerUser}
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

  case class RatingArgs(movieId: Int, rating: Double)
  case class Rating(movieId: Int, rating: Double, timestamp: Long)

  object UserSchema extends GenericSchema[Auth with UsersService with RatingsService] {

    type UserIO[A] = RIO[Auth with UsersService with RatingsService, A]

    case class Mutation(
                         register: UserRegistrationArgs => UserIO[Token],
                         login: UserIO[Token],
                         rate: RatingArgs => UserIO[Rating]
                       )

    val api: GraphQL[Auth with UsersService with RatingsService] = graphQL(RootResolver(
      Option.empty[Unit],
      Some(Mutation(
        register,
        login,
        rate
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

  def rate(ratingArgs: RatingArgs): UserIO[Rating] = for {
    maybeCreds <- getCredentials
    userId <- maybeCreds.fold[Task[Int]]({ Task.fail(UnauthorizedError) }) {
      case cred @ auth.JwtCredentials(_, _) => Task.succeed(cred).map(_.id)
      case auth.BasicCredentials(_, _) => Task.fail(UnauthorizedError)
    }
    postedRating <- postRating(services.ratings.Rating(userId, ratingArgs.movieId, ratingArgs.rating, Timestamp.from(Instant.now())))
  } yield Rating(
    postedRating.movieId,
    postedRating.rating,
    TimeUnit.MILLISECONDS.toSeconds(postedRating.timestamp.getTime)
  )

}