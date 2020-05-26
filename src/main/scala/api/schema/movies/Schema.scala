package api.schema.movies

import api.schema.GenreSchema.Genre
import api.schema.PageInfo
import api.schema.movies.MovieSchema.{Movie, MovieArgs, MovieEdge, MovieSortField, MoviesConnection, MoviesError, MoviesQueryArgs}
import caliban.GraphQL.graphQL
import caliban.{GraphQL, RootResolver}
import caliban.schema.GenericSchema
import services.Cursor
import services.genres.{GenresService, getMovieGenres}
import services.movies.{MoviesService, getMovie, getMovies, getQueryCount}
import zio.{RIO, Task}

object Schema extends GenericSchema[MoviesService with GenresService]{

  type MovieIO[A] = RIO[MoviesService with GenresService, A]


  case class Queries(
                      movie: MovieArgs => MovieIO[Movie],
                      movies: Option[MoviesQueryArgs] => MovieIO[MoviesConnection]
                    )

  implicit val genreSchema = gen[Genre]
  implicit val movieSchema = gen[Movie]
  implicit val movieEdge = gen[MovieEdge]
  implicit val moviesConnection = gen[MoviesConnection]
  implicit val movieErrorsSchema = gen[MoviesError]
  implicit val movieArgsSchema = gen[MovieArgs]
  implicit val moviesQueryArgsSchema = gen[MoviesQueryArgs]

  val api: GraphQL[MoviesService with GenresService] =
    graphQL(RootResolver(
      Queries(
        movieArgs => getMovie(movieArgs.id).map {movieDB =>
          Movie(
            movieDB.id,
            movieDB.title,
            getMovieGenres(movieArgs.id).map(_.map(genreDb => Genre(genreDb.id, genreDb.name))),
            movieDB.releaseDate,
            movieDB.budget,
            movieDB.posterUrl
          )
        },
        moviesArgs => getMovies(moviesArgs).map { movies =>
          MoviesConnection(
            getQueryCount(moviesArgs),
            movies.map(m => MovieEdge(node = Movie(
              m.id,
              m.title,
              Task.succeed(m.genres.split("\\|").map(g => Genre(-1, g)).toList),
              m.releaseDate,
              m.budget,
              m.posterUrl
            ), cursor = getCursorFromMovie(m, moviesArgs.flatMap(_.sortField)))),
            PageInfo(
              hasPreviousPage = true,
              hasNextPage = true,
              startCursor = movies.headOption.fold({""})(m => getCursorFromMovie(m, moviesArgs.flatMap(_.sortField))),
              endCursor = "")
          )
        }
      )
    ))



  def getCursorFromMovie(movie: services.movies.Movie, sortField: Option[MovieSortField]): String = {
    val cursorField = sortField.fold(Option(movie.id.toString)) {
      case MovieSchema.Title => Option(movie.title)
      case MovieSchema.ReleaseDate => movie.releaseDate.map(identity)
      case MovieSchema.Budget => movie.budget.map(_.toString)
    }
    Cursor.encode(cursorField, movie.id.toString)
  }
}