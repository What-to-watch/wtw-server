package services

import api.schema.MovieSchema.{Movie, MoviesConnection, MoviesQueryArgs}
import zio.{Has, IO, ULayer, ZIO, ZLayer, console}

package object movies {

  type MoviesService = Has[MoviesService.MovieService]

  object MoviesService {
    trait MovieService {
      def getMovie(id: Int): IO[Nothing, Movie]
      def getMovies(queryArgs: Option[MoviesQueryArgs]): IO[Nothing, MoviesConnection]
    }
    val test: ULayer[MoviesService] = ZLayer.fromFunction(_ => new MovieService {
      override def getMovie(id: Int): IO[Nothing, Movie] = ???
      override def getMovies(queryArgs: Option[MoviesQueryArgs]): IO[Nothing, MoviesConnection] = ???
    })
  }

  def getMovie(id: Int): ZIO[MoviesService, Nothing, Movie] =
    ZIO.accessM(_.get.getMovie(id))
  def getMovies(queryArgs: Option[MoviesQueryArgs]): ZIO[MoviesService, Nothing, MoviesConnection] =
    ZIO.accessM(_.get.getMovies(queryArgs))
}
