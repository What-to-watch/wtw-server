import cats.effect.Blocker
import config.PostgresConfig
import doobie.util.transactor.Transactor
import zio.blocking.{Blocking, blocking}
import zio.config.Config
import zio.config.config
import zio.{Has, RIO, RLayer, Task, ZIO, ZLayer}

package object persistence {

  type TaskTransactor = Has[Transactor[Task]]

  val postgres: RLayer[Config[PostgresConfig], TaskTransactor] = ZLayer.fromEffect{
    mkTransactor.provideSomeLayer[Config[PostgresConfig]](Blocking.live)
  }

  private def mkTransactor: RIO[Blocking with Config[PostgresConfig], Transactor[Task]] = for {
    config <- config[PostgresConfig]
    transactEC <- blocking { ZIO.descriptor.map(_.executor.asEC) }
    tnx <- Task.effect {
      import zio.interop.catz._
      Transactor.fromDriverManager[Task](
        "org.postgresql.Driver",
        s"jdbc:postgresql://${config.db.host}:${config.db.port}/${config.db.dbname}",
        config.db.user,
        config.db.password,
        Blocker.liftExecutionContext(transactEC)) }
  } yield tnx

}
