package persistence

import api.schema.MovieSchema.{Movie, MoviesError, MoviesQueryArgs}
import zio.{Has, IO, ULayer, URIO, ZIO, ZLayer}

package object movies {

  type MoviesPersistence = Has[MoviesPersistence.Service]

  object MoviesPersistence {
    trait Service {
      def getMovies: IO[Nothing, List[Movie]]
      def getMovies(query: MoviesQueryArgs): IO[Nothing, List[Movie]]
      def getMovie(id: Int): IO[MoviesError, Movie]
    }

    val test: ULayer[MoviesPersistence] = ZLayer.fromFunction { _ =>
      new Service {
        override def getMovies: IO[Nothing, List[Movie]] = ???
        override def getMovies(query: MoviesQueryArgs): IO[Nothing, List[Movie]] = ???
        override def getMovie(id: Int): IO[MoviesError, Movie] = ???
      }
    }

    def getMovies: URIO[MoviesPersistence, List[Movie]] =
      ZIO.accessM(_.get.getMovies)
    def getMovies(query: MoviesQueryArgs): URIO[MoviesPersistence, List[Movie]] =
      ZIO.accessM(_.get.getMovies(query))
    def getMovie(id: Int): ZIO[MoviesPersistence, MoviesError, Movie] =
      ZIO.accessM(_.get.getMovie(id))
  }
}
