package services.watchlists

import doobie.implicits._
import doobie.free.connection.ConnectionIO
import doobie.util.query.Query0
import doobie.util.transactor.Transactor
import doobie.util.update.Update0
import zio.{Task, ZIO}
import zio.interop.catz._

case class WatchlistDB(
                      id: Int,
                      name: String,
                      icon: Option[String],
                      userId: Int,
                      isPublic: Boolean)

final case class LiveWatchlistService(tnx: Transactor[Task]) extends Watchlists.Service {
  import LiveWatchlistService.SQL

  override def createWatchlist(watchlist: Watchlist): Task[Watchlist] = {
    val query = for {
      createdWatchlist <- SQL.createWatchlist(watchlist)
    } yield createdWatchlist
    query.transact(tnx)
      .map { dbWatchlist =>
        Watchlist(
          id = dbWatchlist.id,
          name = dbWatchlist.name,
          icon = dbWatchlist.icon,
          userId = dbWatchlist.userId,
          isPublic = dbWatchlist.isPublic,
          movie_ids = List()
        )
      }.mapError{ err => println(err);err }
  }


  override def getWatchlist(id: Int): Task[Watchlist] = {
    val query = for {
      dbWatchlist <- SQL.getWatchlist(id).unique
      movies <- SQL.getWatchlistMovies(dbWatchlist.id)
    } yield Watchlist(
      id = dbWatchlist.id,
      name = dbWatchlist.name,
      icon = dbWatchlist.icon,
      userId = dbWatchlist.userId,
      isPublic = dbWatchlist.isPublic,
      movie_ids = movies
    )
    query.transact(tnx).mapError{ err => println(err);err }
  }

  override def getPublicWatchlists: Task[List[Watchlist]] = {
    val watchlists = SQL.getPublicWatchlists.transact(tnx)
    watchlists.flatMap(dbWatchlists =>
      ZIO.foreach(dbWatchlists){dbWatchlist =>
        SQL.getWatchlistMovies(dbWatchlist.id)
          .transact(tnx)
          .map(movies => Watchlist(
            id = dbWatchlist.id,
            name = dbWatchlist.name,
            icon = dbWatchlist.icon,
            userId = dbWatchlist.userId,
            isPublic = dbWatchlist.isPublic,
            movie_ids = movies
          ))
      }
    )
  }

  override def getUserWatchlists(userId: Int): Task[List[Watchlist]] = {
    val userWatchlists = SQL.getUserWatchlists(userId).transact(tnx)
    userWatchlists.flatMap(dbWatchlists =>
      ZIO.foreach(dbWatchlists){dbWatchlist =>
        SQL.getWatchlistMovies(dbWatchlist.id)
          .transact(tnx)
          .map(movies => Watchlist(
            id = dbWatchlist.id,
            name = dbWatchlist.name,
            icon = dbWatchlist.icon,
            userId = dbWatchlist.userId,
            isPublic = dbWatchlist.isPublic,
            movie_ids = movies
          ))
      }
    )
  }

  override def addMovieToWatchlist(watchlistId: Int, userId: Int, movieId: Int): Task[Unit] = for {
    dbWatchlist <- SQL.getWatchlist(watchlistId).unique.transact(tnx)
    _ <- if (dbWatchlist.userId != userId) Task.fail(ForbiddenWatchlistModification) else Task.unit
    _ <- SQL.addMovieToWatchlist(watchlistId, movieId).run.transact(tnx)
  } yield ()
}

object LiveWatchlistService {
  object SQL {
    def createWatchlist(watchlist: Watchlist): ConnectionIO[WatchlistDB] =
      sql"INSERT INTO watchlists(name, icon, user_id, is_public) VALUES (${watchlist.name}, ${watchlist.icon}, ${watchlist.userId}, ${watchlist.isPublic})"
      .update
      .withUniqueGeneratedKeys("watchlist_id", "name", "icon", "user_id", "is_public")

    def getWatchlist(id: Int): Query0[WatchlistDB] =
      sql"SELECT watchlist_id, name, icon, user_id, is_public FROM watchlists WHERE watchlist_id = $id".query

    def getPublicWatchlists: ConnectionIO[List[WatchlistDB]] =
      sql"SELECT watchlist_id, name, icon, user_id, is_public FROM watchlists WHERE is_public = TRUE LIMIT 50"
      .query[WatchlistDB]
      .to[List]

    def getWatchlistMovies(watchlistId: Int): ConnectionIO[List[Int]] =
      sql"SELECT movie_id FROM watchlists_movies WHERE watchlist_id = $watchlistId"
      .query[Int]
      .to[List]

    def getUserWatchlists(userId: Int): ConnectionIO[List[WatchlistDB]] =
      sql"SELECT watchlist_id, name, icon, user_id, is_public FROM watchlists WHERE user_id = $userId"
        .query[WatchlistDB]
        .to[List]

    def addMovieToWatchlist(watchlistId: Int, movieId: Int): Update0 =
      sql"""INSERT INTO watchlists_movies(watchlist_id, movie_id) VALUES ($watchlistId, $movieId)
            ON CONFLICT(watchlist_id, movie_id) DO NOTHING
           """.update
  }
}
