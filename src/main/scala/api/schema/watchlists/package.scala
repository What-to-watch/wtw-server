package api.schema

import api.schema.movies.MovieSchema.Movie
import caliban.GraphQL.graphQL
import caliban.schema.GenericSchema
import caliban.{GraphQL, RootResolver}
import services.auth.{Auth, Unauthorized, getUserId}
import services.genres.GenresService
import services.movies.{MoviesService, getMovie}
import services.ratings.RatingsService
import services.watchlists.{WatchlistService, addMovieToWatchlist, createWatchlist,
  getPublicWatchlists, getUserWatchlists, getWatchlist,
  deleteWatchlist, deleteMovieInWatchlist, Watchlist => ServiceWatchlist}
import zio.{RIO, Task, ZIO}

package object watchlists {

  type WatchlistIO[A] = RIO[Auth with WatchlistService with MoviesService with GenresService with RatingsService, A]

  case class Watchlist(
                      id: Int,
                      name: String,
                      icon: Option[String],
                      isPublic: Boolean,
                      movies: WatchlistIO[List[Movie]]
                      )

  case class GetWatchlistArgs(id: Int)
  case class CreateWatchlistArgs(name: String, icon: Option[String], isPublic: Boolean)
  case class DeleteWatchlistArgs(id: Int)
  case class AddMovieToWatchlistArgs(watchlistId: Int, movieId: Int)
  case class DeleteMovieInWatchlistArgs(watchlistId: Int, movieId: Int)

  object WatchlistSchema extends GenericSchema[Auth with WatchlistService with MoviesService with GenresService with RatingsService]{
    case class Query(
                    watchlist: GetWatchlistArgs => WatchlistIO[Watchlist],
                    myWatchlists: WatchlistIO[List[Watchlist]],
                    publicWatchlists: WatchlistIO[List[Watchlist]]
                    )

    case class Mutation(
                         createWatchlist: CreateWatchlistArgs => WatchlistIO[Watchlist],
                         deleteWatchlist: DeleteWatchlistArgs => WatchlistIO[Unit],
                         addMovieToWatchlist: AddMovieToWatchlistArgs => WatchlistIO[Unit],
                         deleteMovieInWatchlist: DeleteMovieInWatchlistArgs => WatchlistIO[Unit]
                       )

    val api: GraphQL[Auth with WatchlistService with MoviesService with GenresService with RatingsService] = graphQL(RootResolver(
      Query(GetWatchlist, GetMyWatchlists(), GetPublicWatchlists()),
      Mutation(CreateWatchlist, DeleteWatchlist, AddMovieToWatchlist, DeleteMovieInWatchlist)
    ))

    def GetWatchlist(args: GetWatchlistArgs): WatchlistIO[Watchlist] = for {
      userIdOpt <- getUserId
      watchlist <- getWatchlist(args.id)
    } yield Watchlist(
      watchlist.id,
      watchlist.name,
      watchlist.icon,
      watchlist.isPublic,
      ZIO.foreach(watchlist.movie_ids)(movieId => getMovie(movieId).flatMap(Movie.fromServiceMovie(_, userIdOpt)))
    )

    def GetMyWatchlists(): WatchlistIO[List[Watchlist]] = for {
      userIdOpt <- getUserId
      id <- userIdOpt match {
        case Some(id) => Task.succeed(id)
        case None => Task.fail(Unauthorized)
      }
      watchlists <- getUserWatchlists(id)
    } yield watchlists.map(watchlist => Watchlist(
      watchlist.id,
      watchlist.name,
      watchlist.icon,
      watchlist.isPublic,
      ZIO.foreach(watchlist.movie_ids)(movieId => getMovie(movieId).flatMap(Movie.fromServiceMovie(_, userIdOpt)))
    ))

    def GetPublicWatchlists(): WatchlistIO[List[Watchlist]] = for {
      userIdOpt <- getUserId
      watchlists <- getPublicWatchlists
    } yield watchlists.map(watchlist => Watchlist(
      watchlist.id,
      watchlist.name,
      watchlist.icon,
      watchlist.isPublic,
      ZIO.foreach(watchlist.movie_ids)(movieId => getMovie(movieId).flatMap(Movie.fromServiceMovie(_, userIdOpt)))
    ))

    def CreateWatchlist(args: CreateWatchlistArgs): WatchlistIO[Watchlist] = for {
      userIdOpt <- getUserId
      id <- userIdOpt match {
        case Some(id) => Task.succeed(id)
        case None => Task.fail(Unauthorized)
      }
      watchlist <- createWatchlist(ServiceWatchlist(args.name, args.icon, id, args.isPublic))
    } yield Watchlist(
      watchlist.id,
      watchlist.name,
      watchlist.icon,
      watchlist.isPublic,
      Task.succeed(List())
    )

    def DeleteWatchlist(args: DeleteWatchlistArgs): WatchlistIO[Unit] = for {
      userIdOpt <- getUserId
      id <- userIdOpt match {
        case Some(id) => Task.succeed(id)
        case None => Task.fail(Unauthorized)
      }
      _ <- deleteWatchlist(args.id, id)
    } yield ()

    def AddMovieToWatchlist(args: AddMovieToWatchlistArgs): WatchlistIO[Unit] = for {
      userIdOpt <- getUserId
      id <- userIdOpt match {
        case Some(id) => Task.succeed(id)
        case None => Task.fail(Unauthorized)
      }
      _ <- addMovieToWatchlist(args.watchlistId, id, args.movieId)
    } yield ()

    def DeleteMovieInWatchlist(args: DeleteMovieInWatchlistArgs): WatchlistIO[Unit] = for {
      userIdOpt <- getUserId
      id <- userIdOpt match {
        case Some(id) => Task.succeed(id)
        case None => Task.fail(Unauthorized)
      }
      _ <- deleteMovieInWatchlist(args.watchlistId, id, args.movieId)
    } yield ()
  }
}
