package services.genres

import doobie.free.connection.ConnectionIO
import doobie.implicits._
import doobie.util.transactor.Transactor
import zio.Task
import zio.interop.catz._
class GenresServiceLive(tnx: Transactor[Task]) extends GenreService.Service {

  import GenresServiceLive._

  override def getGenres: Task[List[Genre]] =
    SQL.getAll
    .transact(tnx)

  override def getMovieGenres(movieId: Int): Task[List[Genre]] =
    SQL.getGenres(movieId)
    .transact(tnx)
}

object GenresServiceLive {

  object SQL {

    val getAll: ConnectionIO[List[Genre]] =
      sql"SELECT genres.id, genres.genre FROM genres"
        .query[Genre]
        .to[List]

    def getGenres(movieId: Int): ConnectionIO[List[Genre]] =
      sql"SELECT genres.id, genres.genre FROM movie_genres, genres WHERE movie_id = $movieId AND genre_id = genres.id ORDER BY genres.genre"
        .query[Genre]
        .to[List]
  }

}
