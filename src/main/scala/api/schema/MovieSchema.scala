package api.schema

import caliban.{GraphQL, RootResolver}
import caliban.GraphQL.graphQL
import caliban.schema.GenericSchema
import persistence.genres.{Genre}
import services.movies.MoviesService
import services.movies.{getMovie, getMovies}
import zio.ZIO

object MovieSchema extends GenericSchema[MoviesService]{

  case class Movie(
                    id: Int,
                    title: String,
                    releaseYear: Int,
                    budget: String,
                    posterUrl: String
                  )
  case class MovieEdge(override val node: Movie, override val cursor: String) extends Edge[Movie](node, cursor)
  case class MoviesConnection(override val edges: List[MovieEdge], override val pageInfo: PageInfo) extends Connection[Movie](edges, pageInfo)

  sealed trait MoviesError extends Throwable
  case object MovieNotFound extends MoviesError

  case class MovieArgs(id: Int)

  sealed trait MovieSortField
  case object Title extends MovieSortField
  case object ReleaseDate extends MovieSortField
  case object Budget extends MovieSortField

  sealed trait MovieSortOrder
  case object ASC extends MovieSortOrder
  case object DESC extends MovieSortOrder

  case class MoviesQueryArgs(
                              genres: Option[List[Genre]],
                              sortField: Option[MovieSortField],
                              sortOrder: Option[MovieSortOrder],
                              override val first: Option[Int],
                              override val after: Option[String],
                              override val last: Option[Int],
                              override val before: Option[String]
                            ) extends PaginationArgs

  case class Queries(
                      movie: MovieArgs => ZIO[MoviesService, MoviesError, Movie],
                      movies: Option[MoviesQueryArgs] => ZIO[MoviesService, Nothing, MoviesConnection]
                    )

  implicit val genreSchema = gen[Genre]
  implicit val movieSchema = gen[Movie]
  implicit val movieEdge = gen[MovieEdge]
  implicit val moviesConnection = gen[MoviesConnection]
  implicit val movieErrorsSchema = gen[MoviesError]
  implicit val movieArgsSchema = gen[MovieArgs]
  implicit val moviesQueryArgsSchema = gen[MoviesQueryArgs]

  val api: GraphQL[MoviesService] =
    graphQL(RootResolver(
      Queries(
        movieArgs => getMovie(movieArgs.id),
        moviesArgs => getMovies(moviesArgs)
      )
    ))
}