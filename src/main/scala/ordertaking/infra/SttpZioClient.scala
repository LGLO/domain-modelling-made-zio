package ordertaking.infra

import sttp.client.SttpBackendOptions
import sttp.client.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.client.asynchttpclient.zio.SttpClient
import zio._

import scala.concurrent.duration.FiniteDuration

object SttpZioClient {

  case class Config(connectionTimeout: FiniteDuration)

  val live: ZLayer[Has[Config], Throwable, SttpClient] =
    ZLayer.fromServiceManaged((c: Config) =>
      AsyncHttpClientZioBackend.managed(SttpBackendOptions.Default.connectionTimeout(c.connectionTimeout))
    )
}
