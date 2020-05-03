package ordertaking.infra

import sttp.client.SttpBackend
import sttp.client.SttpBackendOptions
import sttp.client.asynchttpclient.zio.AsyncHttpClientZioBackend
import zio._

import scala.concurrent.duration.FiniteDuration

object SttpZioClient {

  case class Config(connectionTimeout: FiniteDuration)

  val live: ZLayer[Has[Config], Throwable, Has[SttpBackend[Task, Nothing, Nothing]]] =
    ZLayer.fromServiceManaged((c: Config) =>
      AsyncHttpClientZioBackend.managed(SttpBackendOptions.Default.connectionTimeout(c.connectionTimeout))
    )
}
