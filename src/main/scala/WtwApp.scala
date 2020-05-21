import api.schema.GenreSchema
import api.schema.movies.Schema.{api => movieApi}
import caliban.Http4sAdapter
import cats.data.Kleisli
import cats.effect.{Blocker, ExitCode}
import org.http4s.StaticFile
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.CORS
import services.genres.{GenreService, GenresService}
import services.movies.MoviesService
import persistence.postgres
import zio.config.config
import zio.config.Config
import _root_.config.{ApiConfig, AppConfig}
import zio.blocking.Blocking
import zio.clock.Clock
import zio.console.putStrLn
import zio.{App, IO, RIO, ZIO}
import zio.interop.catz._
import zio.config.syntax._

import scala.concurrent.ExecutionContext

object WtwApp extends App {
  import org.http4s.implicits._

  type AppEnv = Clock with Blocking with GenresService with MoviesService
  type AppTask[A] = RIO[AppEnv, A]

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    val program = for {
      config <- config[ApiConfig]
      blocker <- ZIO.access[Blocking](_.get.blockingExecutor.asEC).map(Blocker.liftExecutionContext)
      interpreter <- (GenreSchema.api |+| movieApi).interpreter
      server <- ZIO.runtime[AppEnv].flatMap { implicit rts =>
        BlazeServerBuilder[AppTask](ExecutionContext.global)
          .bindHttp(config.endpoint.port, config.endpoint.host)
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

    val configLayer = Config.fromSystemEnv(AppConfig.descriptor, keyDelimiter = Some('.'))

    val persistenceLayer = configLayer.narrow(_.postgresConfig) >>> postgres >>> (MoviesService.live ++ GenreService.live)
    program.provideSomeLayer[zio.ZEnv](configLayer.narrow(_.apiConfig) ++ persistenceLayer).foldM(
      err => putStrLn(s"Execution failed with: $err") *> IO.succeed(1),
      _ => IO.succeed(0)
    )
  }
}
