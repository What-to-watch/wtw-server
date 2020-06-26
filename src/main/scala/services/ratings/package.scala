package services

import java.sql.Timestamp

import api.schema.ratings.{AverageRatingInfo, YearlyRatingInfo}
import config.MLApiConfig
import doobie.util.transactor.Transactor
import org.http4s.client.Client
import persistence.TaskTransactor
import utils.HttpClient
import utils.HttpClient.HttpClient
import zio.config.Config
import zio.{Has, RIO, RLayer, Task, ULayer, ZIO, ZLayer}

package object ratings {

  case class Rating(userId: Int, movieId: Int, rating: Double, timestamp: Timestamp)

  type RatingsService = Has[Ratings.Service]

  object Ratings {

    trait Service {
      def getAverageForMovie(movieId: Int): Task[AverageRatingInfo]
      def getYearlyAverageForMovie(movieId: Int): Task[List[YearlyRatingInfo]]
      def getMovieRatingFromUser(movieId:Int, userId:Int): Task[Option[Double]]
      def postRating(rating: Rating): Task[Rating]
    }

    val test: ULayer[RatingsService] = ZLayer.succeed(new Service {
      override def getAverageForMovie(movieId: Int): Task[AverageRatingInfo] = ???
      override def getYearlyAverageForMovie(movieId: Int): Task[List[YearlyRatingInfo]] = ???
      override def postRating(rating: Rating): Task[Rating] = ???
      override def getMovieRatingFromUser(movieId: Int, userId: Int): Task[Option[Double]] = ???
    })

    val live: RLayer[TaskTransactor with Has[MLApiConfig] with HttpClient, RatingsService] =
      ZLayer.fromServices[Transactor[Task], MLApiConfig, HttpClient.Service, Ratings.Service](
        (transactor: Transactor[Task], mlConf: MLApiConfig, client: HttpClient.Service) => RatingsServiceLive(transactor, mlConf.url, client.client))
  }

  def getAverageForMovie(movieId: Int): RIO[RatingsService, AverageRatingInfo] =
    ZIO.accessM(_.get.getAverageForMovie(movieId))
  def getYearlyAverageForMovie(movieId: Int): RIO[RatingsService, List[YearlyRatingInfo]] =
    ZIO.accessM(_.get.getYearlyAverageForMovie(movieId))
  def getRating(movieId: Int, userId: Int): RIO[RatingsService, Option[Double]] =
    ZIO.accessM(_.get.getMovieRatingFromUser(movieId, userId))
  def postRating(rating: Rating): RIO[RatingsService, Rating] =
    ZIO.accessM(_.get.postRating(rating))
}

