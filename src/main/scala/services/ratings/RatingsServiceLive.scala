package services.ratings

import api.schema.movies.MovieSchema.MovieNotFound
import api.schema.ratings.{AverageRatingInfo, YearlyRatingInfo}
import doobie.free.connection.ConnectionIO
import doobie.util.query.Query0
import doobie.implicits._
import doobie.implicits.javasql._
import doobie.postgres._
import doobie.postgres.implicits._
import doobie.util.transactor.Transactor
import org.http4s.{Status, Uri}
import org.http4s.client.Client
import zio.{Task, ZIO}
import zio.interop.catz._

final case class RatingsServiceLive(tnx: Transactor[Task], mlModelUrl: String, httpClient: Client[Task]) extends Ratings.Service {
  import RatingsServiceLive._

  private val mlApiUri = ZIO.fromTry(Uri.fromString(mlModelUrl).toTry)

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

  override def postRating(rating: Rating): Task[Rating] = for {
    createdRating <- SQL.postRating(rating).transact(tnx).mapError({ err => println(err);err })
    count <- SQL.getUserRatingCount(rating.userId).unique.transact(tnx)
    _ <- if(count > 0 && count % 5 == 0) update_model() else Task.succeed(Status.Ok)
  } yield createdRating

  private def update_model(): Task[Status] = for {
    uri <- mlApiUri
    status <- httpClient.statusFromUri(uri.withPath("/train"))
  } yield status

  override def getMovieRatingFromUser(movieId: Int, userId: Int): Task[Option[Double]] =
    SQL.getRating(movieId, userId).transact(tnx).map(_.map(r => r.rating)).mapError({ err => println(err);err})
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

    def getRating(movieId: Int, userId: Int): doobie.ConnectionIO[Option[Rating]] =
      sql"SELECT * FROM ratings WHERE user_id = $userId AND movie_id = $movieId".query[Rating].option

    def postRating(rating: Rating): ConnectionIO[Rating] =
      sql"""INSERT INTO ratings (user_id, movie_id, rating, timestamp)
           VALUES (${rating.userId}, ${rating.movieId}, ${rating.rating}, ${rating.timestamp})
           ON CONFLICT (user_id, movie_id) DO UPDATE SET rating = EXCLUDED.rating, timestamp = EXCLUDED.timestamp"""
        .update
        .withUniqueGeneratedKeys("user_id", "movie_id", "rating", "timestamp")

    def getUserRatingCount(userId: Int): Query0[Int] = sql"SELECT COUNT(*) FROM ratings WHERE user_id = $userId".query[Int]

  }
}
