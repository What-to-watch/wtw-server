package services.movies

import api.schema.movies.MovieSchema
import api.schema.movies.MovieSchema.{MovieNotFound, MovieSortField, MovieSortOrder, MoviesQueryArgs}
import cats.data.NonEmptyList
import doobie.free.connection.ConnectionIO
import doobie.util.transactor.Transactor
import zio.{Task, ZIO}
import zio.interop.catz._
import doobie.implicits._
import doobie.util.fragment.Fragment
import doobie.util.query.Query0
import doobie.util.fragments.{and, in, parentheses, whereAndOpt}
import org.http4s.client.Client
import org.http4s.circe.CirceEntityCodec._
import org.http4s.implicits._
import io.circe.generic.auto._
import org.http4s.Uri
import services.Cursor
import services.ratings.RatingsServiceLive

final case class MoviesServiceLive(tnx: Transactor[Task], mlModelUrl: String, httpClient: Client[Task]) extends MoviesService.Service {

  private val mlApiUri = ZIO.fromTry(Uri.fromString(mlModelUrl).toTry)

  import MoviesServiceLive._

  override def getMovie(id: Int): Task[Movie] =
    SQL
      .getMovie(id)
      .option
      .transact(tnx)
      .foldM(
        err => Task.fail(err),
        maybeMovie => Task.require(MovieNotFound)(Task.succeed(maybeMovie))
      )

  override def getMovies(queryArgs: Option[MoviesQueryArgs]): Task[List[Movie]] = {
    val title = queryArgs.flatMap(_.title)
    val genres = queryArgs.flatMap(_.genres).map(_.map(genre => genre.id))
    val sortField = queryArgs.flatMap(_.sortField)
    val sortOrder = queryArgs.flatMap(_.sortOrder)
    val first = queryArgs.flatMap(_.first)
    val after = queryArgs.flatMap(_.after)
    val last = queryArgs.flatMap(_.last)
    val before = queryArgs.flatMap(_.before)
    SQL.getMovies(title, genres, sortField, sortOrder, first, after, last, before)
      .transact(tnx).foldM(
      {err => println(err);Task.fail(err)},
      {movies => Task.succeed(movies)}
    )
  }

  override def getQueryCount(queryArgs: Option[MoviesQueryArgs]): Task[Int] = {
    val title = queryArgs.flatMap(_.title)
    val genres = queryArgs.flatMap(_.genres).map(_.map(genre => genre.id))
    SQL
      .getQueryCount(title, genres)
      .unique
      .transact(tnx)
      .foldM(
        {err => println(err);Task.fail(err)},
        {movies => Task.succeed(movies)}
      )
  }

  override def getTopListing(n: Int, genreId: Option[Long]): Task[List[Movie]] = {
    val query = genreId.fold({
      SQL.getTopListing(n)
    })(genreId => SQL.getTopListingByGenre(n, genreId))
    query
      .transact(tnx).foldM(
        {err => println(err);Task.fail(err)},
        {movies => println(movies);Task.succeed(movies)}
    )
  }

  override def getTopRecommendedListing(n: Int, userId: Int): Task[Option[List[RecommendedMovie]]] = for {
    count <- RatingsServiceLive.SQL.getUserRatingCount(userId).unique.transact(tnx)
    listing <- if (count >= 5) getUserListing(n, userId) else Task.succeed(List())
  } yield Option.when(listing.nonEmpty)(listing)

  private def getUserListing(n: Int, userId: Int): Task[List[RecommendedMovie]] = for {
    uri <- mlApiUri
    recommendations <- httpClient.expect[MovieRecommendations](uri.withPath(s"/topN/$userId?n=$n")).mapError({ err => println(err);err })
    movies <- Task.foreach(recommendations.movies)(movie => SQL.getMovie(movie.movie_id).unique.transact(tnx).map(m => RecommendedMovie(m,Some(movie.prediction))))
  } yield movies
}

object MoviesServiceLive {

  object SQL {

    val movieFields = fr"SELECT movies.id, movies.title, movies.overview, movies.release_date, movies.budget, movies.poster_url, movies.genres_string"

    def getMovie(id: Int): Query0[Movie] =
      sql"""SELECT id, title, overview, release_date, budget, poster_url, genres_string FROM movies WHERE id = $id"""
        .query[Movie]

    def getQueryCount(title: Option[String], genres: Option[List[Long]]): Query0[Int] = {
      val fields = fr"SELECT count(*)"
      val movies = fields ++ fr"FROM movies"

      val select = genres.flatMap(NonEmptyList.fromList).fold((movies, Option.empty[Fragment]))(ls =>
        (fields ++ fr"FROM movies, movie_genres" , Option(and(in(fr"movie_genres.genre_id", ls), fr"movies.id = movie_genres.movie_id"))))

      val maybeTitle = title.map(titleQuery => Fragment.const(s"movies.title LIKE '%$titleQuery%'"))

      val where = whereAndOpt(select._2, maybeTitle)

      val statement = select._1 ++ where
      statement
        .query[Int]
    }

    def getMovies(title: Option[String], genres: Option[List[Long]], sortField: Option[MovieSortField],
                  sortOrder: Option[MovieSortOrder], first: Option[Int], after: Option[String],
                  last: Option[Int], before: Option[String]): ConnectionIO[List[Movie]] = {

      val movies = movieFields ++ fr"FROM movies"

      val select = genres.flatMap(NonEmptyList.fromList).fold((movies, Option.empty[Fragment]))(ls =>
        (movieFields ++ fr"FROM movies, movie_genres" , Option(and(in(fr"movie_genres.genre_id", ls), fr"movies.id = movie_genres.movie_id"))))

      val maybeTitle = title.map(titleQuery => Fragment.const(s"movies.title LIKE '%$titleQuery%'"))

      val maybeSortField = sortField.map(sortFieldToSql)
      val pagingInfo = getPagingInfo(after, isForward = true) orElse getPagingInfo(before, isForward = false)

      val sortCursorCondition = for {
        sortColumn <- sortField
        (cursor, isForward) <- pagingInfo
      } yield {
        val comparisonOperator = getComparisonOperator(isForward, sortOrder)
        cursor.field match {
          case Some(value) =>  {
            sortFieldComparison(sortField = sortColumn, operator = comparisonOperator, value = value,
              id = cursor.id.toInt, orderOpt = sortOrder)
          }
          case None => fr"movies.id" ++ comparisonOperator ++ fr"${cursor.id.toInt}"
        }
      }
      val onlyCursorCondition = pagingInfo.map{ case (cursor, isForward) =>
        val comparisonOperator = getComparisonOperator(isForward, sortOrder)
        fr"movies.id" ++ comparisonOperator ++ fr"${cursor.id.toInt}"
      }

      val cursorCondition = sortCursorCondition orElse onlyCursorCondition

      val where = whereAndOpt(select._2, maybeTitle, cursorCondition)

      val isForward = pagingInfo.fold({true})(_._2)
      val maybeSort = for {
        field <- maybeSortField
      } yield sortOrder match {
        case Some(order) => fr"ORDER BY" ++ field ++ sortOrderToSql(order, isForward) ++ fr", movies.id" ++ sortOrderToSql(order, isForward)
        case None => fr"ORDER BY" ++ field ++ fr", movies.id"
      }
      val sort = maybeSort.fold(Fragment.empty)(identity)

      val maybeN = first orElse last
      val limit = maybeN.fold(Fragment.empty)(n => fr"LIMIT $n")

      val base_statement = select._1 ++ where ++ sort ++ limit

      val statement = sortField.filter(_ => !isForward).map { field =>
        val sortingBy = field match {
          case MovieSchema.Title => fr"q.title"
          case MovieSchema.ReleaseDate => fr"q.release_date"
          case MovieSchema.Budget => fr"q.budget"
        }
        val orderBy =sortOrder match {
          case Some(order) => fr"ORDER BY" ++ sortingBy ++ sortOrderToSql(order, isForward = true) ++ fr", q.id" ++ sortOrderToSql(order, isForward = true)
          case None => fr"ORDER BY" ++ sortingBy ++ fr", q.id"
        }
        fr"SELECT * FROM" ++ parentheses(base_statement) ++ fr"AS q" ++ orderBy
      }.fold({base_statement})(identity)

      println(statement)
      statement.query[Movie].to[List]
    }

    def sortFieldToSql(sortField: MovieSortField): Fragment = sortField match {
      case MovieSchema.Title => fr"movies.title"
      case MovieSchema.ReleaseDate => fr"movies.release_date"
      case MovieSchema.Budget => fr"movies.budget"
    }

    def sortFieldComparison(sortField: MovieSortField, operator: Fragment, value: String, id: Int, orderOpt: Option[MovieSortOrder]): Fragment = {
      val order = orderOpt.fold[MovieSchema.MovieSortOrder]({MovieSchema.ASC})(identity)
      sortField match {
        case MovieSchema.Title => fr"movies.title" ++ operator ++ fr"$value"
        case MovieSchema.ReleaseDate => {
          val coalesceValue = order match {
            case MovieSchema.ASC => Cursor.MAX_DATE
            case MovieSchema.DESC => Cursor.MIN_DATE
          }
          fr"(COALESCE(movies.release_date, $coalesceValue), movies.id)" ++ operator ++ fr"($value, $id)"
        }
        case MovieSchema.Budget => {
          val coalesceValue = order match {
            case MovieSchema.ASC => Cursor.MAXIMUM_BUDGET_INT.toInt
            case MovieSchema.DESC => Cursor.MINIMUM_BUDGET_INT.toInt
          }
          fr"(COALESCE(movies.budget, $coalesceValue), movies.id)" ++ operator ++ fr"(${value.toInt}, $id)"
        }
      }
    }

    def sortOrderToSql(sortOrder: MovieSortOrder, isForward: Boolean): Fragment = sortOrder match {
      case MovieSchema.ASC => if(isForward) fr"ASC" else fr"DESC NULLS LAST"
      case MovieSchema.DESC => if(isForward) fr"DESC NULLS LAST" else fr"ASC NULLS FIRST"
    }

    def getPagingInfo(cursor: Option[String], isForward: Boolean): Option[(Cursor, Boolean)] = cursor.map{ str =>
      (Cursor.decode(str), isForward)
    }

    def getComparisonOperator(isForward: Boolean, orderOpt: Option[MovieSortOrder]): Fragment = {
      val order = orderOpt.fold[MovieSchema.MovieSortOrder]({MovieSchema.ASC})(identity)
      order match {
        case MovieSchema.ASC => if(isForward) fr">" else fr"<"
        case MovieSchema.DESC =>if(isForward) fr"<" else fr">"
      }
    }

    def getTopListing(n:Int): ConnectionIO[List[Movie]] = {
      val join = fr"FROM movies, ratings WHERE movies.id = ratings.movie_id"
      val groupBy = fr"GROUP BY movies.id"
      val having = fr"HAVING COUNT(ratings.rating) >= 10"
      val orderBy = fr"ORDER BY AVG(ratings.rating) DESC, COUNT(ratings.rating) DESC LIMIT $n"

      val statement = movieFields ++ join ++ groupBy ++ having ++ orderBy
      println(statement.toString)
      statement.query[Movie].to[List]
    }

    def getTopListingByGenre(n:Int, genreId: Long): ConnectionIO[List[Movie]] = {

      val join = fr"FROM genres INNER JOIN movie_genres ON genres.id = movie_genres.genre_id " ++
        fr"INNER JOIN movies ON movie_genres.movie_id = movies.id " ++
        fr"INNER JOIN ratings ON movies.id = ratings.movie_id"
      val where = fr"WHERE genres.id = $genreId"
      val groupBy = fr"GROUP BY movies.id"
      val having = fr"HAVING COUNT(ratings.rating) >= 10"
      val orderBy = fr"ORDER BY AVG(ratings.rating) DESC, COUNT(ratings.rating) DESC LIMIT $n"

      val statement = movieFields ++ join ++ where ++ groupBy ++ having ++ orderBy
      println(statement)
      statement.query[Movie].to[List]
    }
  }
}
