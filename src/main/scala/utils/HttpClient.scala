package utils

import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import zio.interop.catz
import zio.interop.catz._
import zio.{Has, RIO, Task, URIO, ZIO, ZLayer, ZManaged}

object HttpClient {

  type HttpClient = Has[Service]

  trait Service {
    def client: Client[Task]
  }

  case class SimpleClient(client: Client[Task]) extends Service

  private def clientManaged = {
    val zioManaged: ZIO[Any, Throwable, ZManaged[Any, Throwable, Client[Task]]] = ZIO.runtime[Any].map { implicit rts =>
      val exec = rts.platform.executor.asEC

      catz.catsIOResourceSyntax(BlazeClientBuilder[Task](exec).resource).toManaged
    }
    // for our test we need a ZManaged, but right now we've got a ZIO of a ZManaged. To deal with
    // that we create a Managed of the ZIO and then flatten it
    val zm = zioManaged.toManaged_ // toManaged_ provides an empty release of the rescoure
    zm.flatten
  }
  def clientLive:ZLayer[Any, Throwable, HttpClient] = ZLayer.fromManaged(clientManaged.map(x => SimpleClient(x)))

  def httpClient: URIO[HttpClient, Client[Task]] = ZIO.access[HttpClient](_.get.client)

}
