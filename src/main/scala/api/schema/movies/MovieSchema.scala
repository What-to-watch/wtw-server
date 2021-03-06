package api.schema.movies

import api.schema.{Connection, Edge, PageInfo, PaginationArgs}
import api.schema.GenreSchema.Genre
import api.schema.movies.Schema.MovieIO

object MovieSchema {

  case class Movie(
                    id: Int,
                    title: String,
                    genres: MovieIO[List[Genre]],
                    releaseDate: Option[String],
                    budget: Option[Int],
                    posterUrl: Option[String]
                  )
  case class MovieEdge(override val node: Movie, override val cursor: String) extends Edge[Movie](node, cursor)
  case class MoviesConnection(totalCount: MovieIO[Int], override val edges: List[MovieEdge], override val pageInfo: PageInfo) extends Connection[Movie](edges, pageInfo)

  sealed trait MoviesError extends Throwable
  case object MovieNotFound extends MoviesError

  case class MovieArgs(id: Int)

  sealed trait MovieSortField
  case object Title extends MovieSortField
  case object ReleaseDate extends MovieSortField
  case object Budget extends MovieSortField

  sealed trait MovieSortOrder
  case object ASC extends MovieSortOrder
  case object DESC extends MovieSortOrder

  case class MoviesQueryArgs(
                              title: Option[String],
                              genres: Option[List[Genre]],
                              sortField: Option[MovieSortField],
                              sortOrder: Option[MovieSortOrder],
                              override val first: Option[Int],
                              override val after: Option[String],
                              override val last: Option[Int],
                              override val before: Option[String]
                            ) extends PaginationArgs

}
