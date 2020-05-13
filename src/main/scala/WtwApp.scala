import api.schema.{GenreSchema, MovieSchema}
import caliban.Http4sAdapter
import cats.data.Kleisli
import cats.effect.{Blocker, ExitCode}
import org.http4s.StaticFile
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.CORS
import persistence.genres.GenresPersistence
import persistence.movies.MoviesPersistence
import services.genres.GenreService
import services.movies.MoviesService
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.putStrLn
import zio.{App, IO, RIO, ZIO}
import zio.interop.catz._

import scala.concurrent.ExecutionContext

object WtwApp extends App {
  import org.http4s.implicits._

  type AppEnv = Clock with Blocking with GenreService with MoviesService
  type AppTask[A] = RIO[AppEnv, A]

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    val program = for {
      blocker <- ZIO.access[Blocking](_.get.blockingExecutor.asEC).map(Blocker.liftExecutionContext)
      interpreter <- (GenreSchema.api |+| MovieSchema.api).interpreter
      server <- ZIO.runtime[AppEnv].flatMap { implicit rts =>
        BlazeServerBuilder[AppTask](ExecutionContext.global)
          .bindHttp(3000, "0.0.0.0")
          .withHttpApp(
            Router[AppTask](
              "/api/graphql" -> CORS(Http4sAdapter.makeHttpService(interpreter)),
              "/graphiql" -> Kleisli.liftF(StaticFile.fromResource("/graphql-playground.html", blocker, None))
            ).orNotFound
          )
          .serve
          .compile[AppTask, AppTask, ExitCode]
          .drain
      }
    } yield server

    val persistenceLayer = MoviesService.test ++ GenreService.test
    program.provideSomeLayer[zio.ZEnv](persistenceLayer).foldM(
      err => putStrLn(s"Execution failed with: $err") *> IO.succeed(1),
      _ => IO.succeed(0)
    )
  }
}
