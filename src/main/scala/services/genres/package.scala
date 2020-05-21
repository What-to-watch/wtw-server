package services

import persistence.TaskTransactor
import zio.{Has, RIO, RLayer, Task, ULayer, ZIO, ZLayer}

package object genres {

  case class Genre(id: Long, name: String)

  type GenresService = Has[GenreService.Service]

  object GenreService {
    trait Service {
      def getGenres: Task[List[Genre]]
      def getMovieGenres(movieId: Int): Task[List[Genre]]
    }

    val test: ULayer[GenresService] = ZLayer.fromFunction(_ => new Service {
      override def getGenres: Task[List[Genre]] = ???
      override def getMovieGenres(movieId: Int): Task[List[Genre]] = ???
    })

    val live: RLayer[TaskTransactor, GenresService] =
      ZLayer.fromService(transactor => new GenresServiceLive(transactor))
  }

  def getGenres: RIO[GenresService, List[Genre]] =
    ZIO.accessM(_.get.getGenres)
  def getMovieGenres(movieId: Int): RIO[GenresService, List[Genre]] =
    ZIO.accessM(_.get.getMovieGenres(movieId))
}
