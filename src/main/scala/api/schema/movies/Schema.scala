package api.schema.movies

import api.schema.GenreSchema.Genre
import api.schema.PageInfo
import api.schema.movies.MovieSchema.{Movie, MovieArgs, MovieEdge, MovieSortField, MoviesConnection, MoviesError, MoviesQueryArgs, TopListingArgs, TopRecommendedListingArgs}
import api.schema.ratings.{AverageRatingInfo, YearlyRatingInfo}
import caliban.GraphQL.graphQL
import caliban.{GraphQL, RootResolver}
import caliban.schema.GenericSchema
import services.{Cursor, auth}
import services.auth.{Auth, JwtCredentials, getCredentials}
import services.genres.{GenresService, getMovieGenres}
import services.movies.{MoviesService, getMovie, getMovies, getQueryCount, getTopListing, getTopRecommendedListing}
import services.ratings.{getAverageForMovie, getRating, getYearlyAverageForMovie}
import services.ratings.RatingsService
import services.users.LoginError
import zio.{RIO, Task, ZIO}

object Schema extends GenericSchema[Auth with MoviesService with GenresService with RatingsService] {

  type MovieIO[A] = RIO[Auth with MoviesService with GenresService with RatingsService, A]

  case class Queries(
                      movie: MovieArgs => MovieIO[Movie],
                      movies: Option[MoviesQueryArgs] => MovieIO[MoviesConnection],
                      topListing: TopListingArgs => MovieIO[List[Movie]],
                      topRecommendedListing: TopRecommendedListingArgs => MovieIO[Option[List[Movie]]]
                    )

  implicit val averageRatingInfoSchema = gen[AverageRatingInfo]
  implicit val yearlyRatingInfoSchema = gen[YearlyRatingInfo]
  implicit val genreSchema = gen[Genre]
  implicit val movieSchema = gen[Movie]
  implicit val movieEdge = gen[MovieEdge]
  implicit val moviesConnection = gen[MoviesConnection]
  implicit val movieErrorsSchema = gen[MoviesError]
  implicit val movieArgsSchema = gen[MovieArgs]
  implicit val moviesQueryArgsSchema = gen[MoviesQueryArgs]


  val api: GraphQL[Auth with MoviesService with GenresService with RatingsService] =
    graphQL(RootResolver(
      Queries(
        GetMovie,
        moviesArgs => getMovies(moviesArgs).map { movies =>
          MoviesConnection(
            getQueryCount(moviesArgs),
            movies.map(m => MovieEdge(node = Movie(
              m.id,
              m.title,
              m.overview,
              Task.succeed(m.genres.split("\\|").map(g => Genre(-1, g)).toList),
              m.releaseDate,
              m.budget,
              m.posterUrl,
              getAverageForMovie(m.id),
              getYearlyAverageForMovie(m.id)
            ), cursor = getCursorFromMovie(m, moviesArgs.flatMap(_.sortField)))),
            PageInfo(
              hasPreviousPage = true,
              hasNextPage = true,
              startCursor = movies.headOption.fold({
                ""
              })(m => getCursorFromMovie(m, moviesArgs.flatMap(_.sortField))),
              endCursor = "")
          )
        },
        TopListing,
        TopRecommendedListing
      )
    ))

  def GetMovie(movieArgs: MovieArgs): MovieIO[Movie] = for {
    maybeCreds <- getCredentials
    maybeId <- maybeCreds.fold[Task[Option[Int]]]({ Task.succeed(None) }) {
      case auth.JwtCredentials(_, id) => Task.succeed(Some(id))
      case auth.BasicCredentials(_, _) => Task.succeed(None)
    }
    movieDB <- getMovie(movieArgs.id)
    rating <- maybeId match {
      case Some(userId) =>  getRating(movieDB.id, userId)
      case None => ZIO.succeed(Option.empty)
    }
  } yield Movie(
      movieDB.id,
      movieDB.title,
      movieDB.overview,
      getMovieGenres(movieArgs.id).map(_.map(genreDb => Genre(genreDb.id, genreDb.name))),
      movieDB.releaseDate,
      movieDB.budget,
      movieDB.posterUrl,
      getAverageForMovie(movieDB.id),
      getYearlyAverageForMovie(movieDB.id),
      rating
    )

  def TopListing(listingArgs: TopListingArgs): MovieIO[List[Movie]] = {
    val n = listingArgs.n.fold({100})(identity)
    getTopListing(n, listingArgs.genreId)
      .map(movies =>
        movies.map(m => Movie(
          m.id,
          m.title,
          m.overview,
          Task.succeed(m.genres.split("\\|").map(g => Genre(-1, g)).toList),
          m.releaseDate,
          m.budget,
          m.posterUrl,
          getAverageForMovie(m.id),
          getYearlyAverageForMovie(m.id)
        )))
  }

  def TopRecommendedListing(listingArgs: TopRecommendedListingArgs): MovieIO[Option[List[Movie]]] = for {
    maybeCreds <- getCredentials
    creds <- maybeCreds.fold[Task[JwtCredentials]]({ Task.fail(LoginError) }) {
      case cred @ auth.JwtCredentials(_, _) => Task.succeed(cred)
      case auth.BasicCredentials(_, _) => Task.fail(LoginError)
    }
    n = listingArgs.n getOrElse 5
    movies <- getTopRecommendedListing(n, creds.id)
  } yield movies.map(movies =>
    movies.map(m => Movie(
      m.id,
      m.title,
      m.overview,
      Task.succeed(m.genres.split("\\|").map(g => Genre(-1, g)).toList),
      m.releaseDate,
      m.budget,
      m.posterUrl,
      getAverageForMovie(m.id),
      getYearlyAverageForMovie(m.id),
      expectedRating = m.recommendedRating
    )))

  def getCursorFromMovie(movie: services.movies.Movie, sortField: Option[MovieSortField]): String = {
    val cursorField = sortField.fold(Option(movie.id.toString)) {
      case MovieSchema.Title => Option(movie.title)
      case MovieSchema.ReleaseDate => movie.releaseDate.map(identity)
      case MovieSchema.Budget => movie.budget.map(_.toString)
    }
    Cursor.encode(cursorField, movie.id.toString)
  }
}