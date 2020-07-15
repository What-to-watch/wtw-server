package api.schema.movies

import api.schema.{Connection, Edge, GraphqlEncodableError, PageInfo, PaginationArgs}
import api.schema.GenreSchema.Genre
import api.schema.movies.Schema.MovieIO
import api.schema.ratings.{AverageRatingInfo, YearlyRatingInfo}
import services.genres.{GenresService, getMovieGenres}
import services.movies.{Movie => ServiceMovie}
import services.ratings.{RatingsService, getAverageForMovie, getRating, getYearlyAverageForMovie}
import zio.{RIO, ZIO}

object MovieSchema {

  case class Movie(
                    id: Int,
                    title: String,
                    genres: MovieIO[List[Genre]],
                    releaseDate: Option[String],
                    budget: Option[Int],
                    posterUrl: Option[String],
                    averageRating: MovieIO[AverageRatingInfo],
                    yearlyAverageRating: MovieIO[List[YearlyRatingInfo]],
                    myRating: Option[Double] = None,
                    expectedRating: Option[Double] = None
                  )
  object Movie {
    def fromServiceMovie(movie: ServiceMovie, userIdOpt: Option[Int]): RIO[RatingsService with GenresService, Movie] = for {
      rating <- userIdOpt match {
        case Some(userId) =>  getRating(movie.id, userId)
        case None => ZIO.succeed(Option.empty)
      }
    } yield Movie(
      movie.id,
      movie.title,
      getMovieGenres(movie.id).map(_.map(genreDb => Genre(genreDb.id, genreDb.name))),
      movie.releaseDate,
      movie.budget,
      movie.posterUrl,
      getAverageForMovie(movie.id),
      getYearlyAverageForMovie(movie.id),
      rating
    )
  }
  case class MovieEdge(override val node: Movie, override val cursor: String) extends Edge[Movie](node, cursor)
  case class MoviesConnection(totalCount: MovieIO[Int], override val edges: List[MovieEdge], override val pageInfo: PageInfo) extends Connection[Movie](edges, pageInfo)

  sealed trait MoviesError extends GraphqlEncodableError
  case object MovieNotFound extends MoviesError {
    override def errorCode: String = "MOVIE-NOT-FOUND"
  }

  case class MovieArgs(id: Int)

  sealed trait MovieSortField
  case object Title extends MovieSortField
  case object ReleaseDate extends MovieSortField
  case object Budget extends MovieSortField

  sealed trait MovieSortOrder
  case object ASC extends MovieSortOrder
  case object DESC extends MovieSortOrder

  case class MoviesQueryArgs(
                              title: Option[String],
                              genres: Option[List[Genre]],
                              sortField: Option[MovieSortField],
                              sortOrder: Option[MovieSortOrder],
                              override val first: Option[Int],
                              override val after: Option[String],
                              override val last: Option[Int],
                              override val before: Option[String]
                            ) extends PaginationArgs

  case class TopListingArgs(n:Option[Int], genreId:Option[Long])
  case class TopRecommendedListingArgs(n:Option[Int])
}
