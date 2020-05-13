package api.schema

import caliban.{GraphQL, RootResolver}
import caliban.GraphQL.graphQL
import caliban.schema.GenericSchema
import services.genres.GenreService
import services.genres.getGenres
import zio.URIO

object GenreSchema extends GenericSchema[GenreService] {

  case class Genre(name: String)

  case class Queries(genres: URIO[GenreService, List[Genre]])

  val api: GraphQL[GenreService] = graphQL(
    RootResolver(Queries(getGenres))
  )
}
