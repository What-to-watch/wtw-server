package api.schema

abstract class Edge[A](val node: A, val cursor: String)
case class PageInfo(hasNextPage: Boolean,
                    hasPreviousPage: Boolean,
                    startCursor: String,
                    endCursor: String)
abstract  class Connection[A](val edges: List[Edge[A]], val pageInfo: PageInfo)

abstract class PaginationArgs(val first: Option[Int] = None,
                              val after: Option[String] = None,
                              val last: Option[Int] = None,
                              val before: Option[String] = None)