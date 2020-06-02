package services

import api.schema.ratings.{AverageRatingInfo, YearlyRatingInfo}
import persistence.TaskTransactor
import zio.{Has, RIO, RLayer, Task, ULayer, ZIO, ZLayer}

package object ratings {

  type RatingsService = Has[Ratings.Service]

  object Ratings {

    trait Service {
      def getAverageForMovie(movieId: Int): Task[AverageRatingInfo]
      def getYearlyAverageForMovie(movieId: Int): Task[List[YearlyRatingInfo]]
    }

    val test: ULayer[RatingsService] = ZLayer.succeed(new Service {
      override def getAverageForMovie(movieId: Int): Task[AverageRatingInfo] = ???
      override def getYearlyAverageForMovie(movieId: Int): Task[List[YearlyRatingInfo]] = ???
    })

    val live: RLayer[TaskTransactor, RatingsService] =
      ZLayer.fromService(transactor => new RatingsServiceLive(transactor))
  }

  def getAverageForMovie(movieId: Int): RIO[RatingsService, AverageRatingInfo] =
    ZIO.accessM(_.get.getAverageForMovie(movieId))
  def getYearlyAverageForMovie(movieId: Int): RIO[RatingsService, List[YearlyRatingInfo]] =
    ZIO.accessM(_.get.getYearlyAverageForMovie(movieId))

}

