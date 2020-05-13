package services

import api.schema.GenreSchema.Genre
import zio.{Has, UIO, ULayer, URIO, ZIO, ZLayer}

package object genres {

  type GenreService = Has[GenreService.Service]

  object GenreService {
    trait Service {
      def getGenres: UIO[List[Genre]]
    }

    val test: ULayer[GenreService] = ZLayer.fromFunction(_ => new Service {
      override def getGenres: UIO[List[Genre]] = ???
    })
  }

  def getGenres: URIO[GenreService, List[Genre]] =
    ZIO.accessM(_.get.getGenres)
}
