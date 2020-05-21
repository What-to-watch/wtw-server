package api.schema

import caliban.GraphQL.graphQL
import caliban.schema.GenericSchema
import caliban.{GraphQL, RootResolver}
import services.genres.{GenresService, getGenres}
import zio.RIO

object GenreSchema extends GenericSchema[GenresService] {

  type GenreIO[A] = RIO[GenresService, A]

  case class Genre(id: Long, name: String)

  case class Queries(genres: GenreIO[List[Genre]])

  val api: GraphQL[GenresService] = graphQL(
    RootResolver(Queries(getGenres.map(_.map(genreDb => Genre(genreDb.id, genreDb.name)))))
  )
}
