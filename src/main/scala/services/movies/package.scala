package services

import api.schema.movies.MovieSchema.{MoviesConnection, MoviesQueryArgs}
import config.MLApiConfig
import doobie.util.transactor.Transactor
import persistence.TaskTransactor
import utils.HttpClient
import utils.HttpClient.HttpClient
import zio.{Has, RIO, RLayer, Task, ULayer, ZIO, ZLayer}

package object movies {

  type MoviesService = Has[MoviesService.Service]

  case class Movie(
                    id: Int,
                    title: String,
                    releaseDate: Option[String],
                    budget: Option[Int],
                    posterUrl: Option[String],
                    genres: String
                  )
  case class RecommendedMovie(
                               id: Int,
                               title: String,
                               releaseDate: Option[String],
                               budget: Option[Int],
                               posterUrl: Option[String],
                               genres: String,
                               recommendedRating: Option[Double]
                             )
  object RecommendedMovie {
    def apply(movie: Movie, recommendedRating: Option[Double]): RecommendedMovie = RecommendedMovie(
      movie.id,
      movie.title,
      movie.releaseDate,
      movie.budget,
      movie.posterUrl,
      movie.genres,
      recommendedRating
    )
  }
  case class MovieRecommendations(movies: List[RecommendedMovieApi])
  case class RecommendedMovieApi(movie_id: Int, prediction: Double)

  object MoviesService {
    trait Service {
      def getMovie(id: Int): Task[Movie]
      def getMovies(queryArgs: Option[MoviesQueryArgs]): Task[List[Movie]]
      def getQueryCount(queryArgs: Option[MoviesQueryArgs]): Task[Int]
      def getTopListing(n: Int, genreId: Option[Long]): Task[List[Movie]]
      def getTopRecommendedListing(n: Int, userId:Int): Task[Option[List[RecommendedMovie]]]
    }

    val test: ULayer[MoviesService] = ZLayer.fromFunction(_ => new Service {
      override def getMovie(id: Int): Task[Movie] = ???
      override def getMovies(queryArgs: Option[MoviesQueryArgs]): Task[List[Movie]] = ???
      override def getQueryCount(queryArgs: Option[MoviesQueryArgs]): Task[Int] = ???
      override def getTopListing(n: Int, genreId: Option[Long]): Task[List[Movie]] = ???
      override def getTopRecommendedListing(n: Int, userId: Int): Task[Option[List[RecommendedMovie]]] = ???
    })

    val live: RLayer[TaskTransactor with Has[MLApiConfig] with HttpClient, MoviesService] =
      ZLayer.fromServices[Transactor[Task], MLApiConfig, HttpClient.Service, MoviesService.Service](
        (transactor: Transactor[Task], mlConf: MLApiConfig, client: HttpClient.Service) => MoviesServiceLive(transactor, mlConf.url, client.client))

  }

  def getMovie(id: Int): RIO[MoviesService, Movie] =
    RIO.accessM(_.get.getMovie(id))
  def getMovies(queryArgs: Option[MoviesQueryArgs]): RIO[MoviesService, List[Movie]] =
    ZIO.accessM(_.get.getMovies(queryArgs))
  def getQueryCount(queryArgs: Option[MoviesQueryArgs]): RIO[MoviesService, Int] =
    ZIO.accessM(_.get.getQueryCount(queryArgs))
  def getTopListing(n: Int, genreId: Option[Long]): RIO[MoviesService, List[Movie]] =
    ZIO.accessM(_.get.getTopListing(n, genreId))
  def getTopRecommendedListing(n: Int, userId:Int): RIO[MoviesService, Option[List[RecommendedMovie]]] =
    ZIO.accessM(_.get.getTopRecommendedListing(n, userId))
}
