package api

import api.schema.movies.Schema
import api.schema.user.UserSchema
import api.schema.watchlists.WatchlistSchema
import caliban.CalibanError.{ExecutionError, ParsingError, ValidationError}
import caliban.ResponseValue.ObjectValue
import caliban.Value.StringValue
import caliban.{CalibanError, GraphQL, GraphQLInterpreter}
import services.auth.Auth
import services.genres.GenresService
import services.movies.MoviesService
import services.ratings.RatingsService
import services.users.UsersService
import services.watchlists.WatchlistService
import zio.IO

package object schema {

  trait GraphqlEncodableError extends Throwable {
    def errorCode: String
  }

  type SchemaEnv = MoviesService with GenresService with RatingsService with Auth with UsersService with WatchlistService

  object WtWApi {
    val schema: GraphQL[SchemaEnv] =
      UserSchema.api |+| GenreSchema.api |+| Schema.api |+| WatchlistSchema.api

    val interpreter: IO[ValidationError, GraphQLInterpreter[SchemaEnv, CalibanError]] = for {
      interpreter <- schema.interpreter
    } yield withErrorHandling(interpreter)

    private def withErrorHandling(interpreter: GraphQLInterpreter[SchemaEnv, CalibanError]): GraphQLInterpreter[SchemaEnv, CalibanError] = interpreter.mapError{
      case err @ ExecutionError(_, _, _, Some(encodableError: GraphqlEncodableError), _) =>
        err.copy(extensions = Some(ObjectValue(List(("errorCode", StringValue(encodableError.errorCode))))))
      case err: ExecutionError =>
        err.copy(extensions = Some(ObjectValue(List(("errorCode", StringValue("EXECUTION_ERROR"))))))
      case err: ValidationError =>
        err.copy(extensions = Some(ObjectValue(List(("errorCode", StringValue("VALIDATION_ERROR"))))))
      case err: ParsingError =>
        err.copy(extensions = Some(ObjectValue(List(("errorCode", StringValue("PARSING_ERROR"))))))
    }
  }

}