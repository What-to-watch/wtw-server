package services

import api.schema.movies.MovieSchema.{MoviesConnection, MoviesQueryArgs}
import api.schema.movies.{MovieSchema, Schema}
import persistence.TaskTransactor
import zio.{Has, RIO, RLayer, Task, ULayer, ZIO, ZLayer}

package object movies {

  type MoviesService = Has[MoviesService.Service]

  case class Movie(
                    id: Int,
                    title: String,
                    releaseDate: Option[String],
                    budget: Option[Int],
                    posterUrl: Option[String]
                  )

  object MoviesService {
    trait Service {
      def getMovie(id: Int): Task[Movie]
      def getMovies(queryArgs: Option[MoviesQueryArgs]): Task[List[Movie]]
    }

    val test: ULayer[MoviesService] = ZLayer.fromFunction(_ => new Service {
      override def getMovie(id: Int): Task[Movie] = ???
      override def getMovies(queryArgs: Option[MoviesQueryArgs]): Task[List[Movie]] = ???
    })

    val live: RLayer[TaskTransactor, MoviesService] =
      ZLayer.fromService(transactor => new MoviesServiceLive(transactor))

  }

  def getMovie(id: Int): RIO[MoviesService, Movie] =
    RIO.accessM(_.get.getMovie(id))
  def getMovies(queryArgs: Option[MoviesQueryArgs]): RIO[MoviesService, List[Movie]] =
    ZIO.accessM(_.get.getMovies(queryArgs))
}
