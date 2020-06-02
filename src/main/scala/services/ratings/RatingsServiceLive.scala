package services.ratings

import api.schema.movies.MovieSchema.MovieNotFound
import api.schema.ratings.{AverageRatingInfo, YearlyRatingInfo}
import doobie.free.connection.ConnectionIO
import doobie.util.query.Query0
import doobie.implicits._
import doobie.util.transactor.Transactor
import zio.Task
import zio.interop.catz._

final class RatingsServiceLive(tnx: Transactor[Task]) extends Ratings.Service {

  import RatingsServiceLive._

  override def getAverageForMovie(movieId: Int): Task[AverageRatingInfo] = SQL
    .getAvgRating(movieId)
    .option
    .transact(tnx)
    .foldM(
      err => Task.fail(err),
      maybeMovie => Task.require(MovieNotFound)(Task.succeed(maybeMovie))
    )

  override def getYearlyAverageForMovie(movieId: Int): Task[List[YearlyRatingInfo]] =
    SQL.yearlyAverageRating(movieId).transact(tnx).foldM(
      { err => println(err); Task.fail(err) },
      { movies => Task.succeed(movies) }
    )
}

object RatingsServiceLive {

  object SQL {
    def getAvgRating(movieId: Int): Query0[AverageRatingInfo] = {
      val sql = fr"SELECT AVG(rating) AS avg_rating, COUNT(rating) as num_rating " ++
        fr"FROM ratings WHERE movie_id = $movieId"
      sql.query[AverageRatingInfo]
    }

    def yearlyAverageRating(movieId: Int): ConnectionIO[List[YearlyRatingInfo]] = {
      val sql = fr"SELECT EXTRACT(YEAR FROM timestamp) AS year, AVG(rating) AS avg_rating, COUNT(rating) as num_rating" ++
        fr"FROM ratings WHERE movie_id = $movieId GROUP BY EXTRACT(YEAR FROM timestamp);"
      sql
        .query[YearlyRatingInfo]
        .to[List]
    }
  }

}
