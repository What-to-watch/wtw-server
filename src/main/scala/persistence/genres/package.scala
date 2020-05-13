package persistence

import zio.{Has, UIO, ULayer, URIO, ZIO, ZLayer}

package object genres {

  case class Genre(name: String)

  type GenresPersistence = Has[GenresPersistence.Service]

  object GenresPersistence {
    trait Service {
      def getGenres: UIO[List[Genre]]
    }

    val test: ULayer[GenresPersistence] = ZLayer.fromFunction { _ =>
      new Service {
        override def getGenres: UIO[List[Genre]] = ???
      }
    }

    def getGenres: URIO[GenresPersistence, List[Genre]] =
      ZIO.accessM(_.get.getGenres)
  }
}
