package services

import api.schema.GraphqlEncodableError
import persistence.TaskTransactor
import zio.{Has, RIO, RLayer, Task, ULayer, ZLayer}

package object watchlists {

  case class Watchlist(
                        id: Int,
                        name: String,
                        icon: Option[String],
                        userId: Int,
                        isPublic: Boolean,
                        movie_ids: List[Int]
                      )
  object Watchlist {
    def apply(name: String, icon: Option[String], userId: Int, isPublic: Boolean, movie_ids: List[Int]): Watchlist =
      new Watchlist(-1, name, icon, userId, isPublic,  movie_ids)
    def apply(name: String, icon: Option[String], userId: Int, isPublic: Boolean): Watchlist =
      new Watchlist(-1, name, icon, userId, isPublic, List())
  }

  sealed trait WatchlistError extends GraphqlEncodableError
  case object MovieAlreadyAdded extends WatchlistError {
    override def errorCode: String = "MOVIE-ALREADY-ADDED"
  }
  case object ForbiddenWatchlistModification extends WatchlistError {
    override def errorCode: String = "FORBIDDEN"
  }

  type WatchlistService = Has[Watchlists.Service]

  object Watchlists {
    trait Service {
      def createWatchlist(watchlist: Watchlist): Task[Watchlist]
      def getWatchlist(id: Int): Task[Watchlist]
      def getPublicWatchlists: Task[List[Watchlist]]
      def getUserWatchlists(userId: Int): Task[List[Watchlist]]
      def addMovieToWatchlist(watchlistId: Int, userId: Int, movieId: Int): Task[Unit]
    }

    val live: RLayer[TaskTransactor, WatchlistService] = ZLayer.fromService(tnx => LiveWatchlistService(tnx))

    val test: ULayer[WatchlistService] = ZLayer.succeed{
      new Service {
        override def createWatchlist(watchlist: Watchlist): Task[Watchlist] = ???
        override def getWatchlist(id: Int): Task[Watchlist] = ???
        override def getUserWatchlists(userId: Int): Task[List[Watchlist]] = ???
        override def addMovieToWatchlist(watchlistId: Int, userId: Int, movieId: Int): Task[Unit] = ???
        override def getPublicWatchlists: Task[List[Watchlist]] = ???
      }
    }
  }

  def createWatchlist(watchlist: Watchlist): RIO[WatchlistService, Watchlist] =
    RIO.accessM(_.get.createWatchlist(watchlist))
  def getWatchlist(id: Int): RIO[WatchlistService, Watchlist] =
    RIO.accessM(_.get.getWatchlist(id))
  def getPublicWatchlists: RIO[WatchlistService, List[Watchlist]] =
    RIO.accessM(_.get.getPublicWatchlists)
  def getUserWatchlists(userId: Int): RIO[WatchlistService, List[Watchlist]] =
    RIO.accessM(_.get.getUserWatchlists(userId))
  def addMovieToWatchlist(watchlistId: Int, userId: Int, movieId: Int): RIO[WatchlistService, Unit] =
    RIO.accessM(_.get.addMovieToWatchlist(watchlistId, userId, movieId))
}
