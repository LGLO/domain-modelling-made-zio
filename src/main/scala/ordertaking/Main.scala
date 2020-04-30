package ordertaking

import ordertaking.infra.AppConfig
import ordertaking.infra.SttpZioClient
import ordertaking.services.AcknowledgeSender
import ordertaking.services.AcknowledgeSender.AcknowledgeSender
import ordertaking.services.AddressValidator
import ordertaking.services.AddressValidator.AddressValidator
import ordertaking.services.KafkaEventPublisher
import ordertaking.services.Letters
import ordertaking.services.ProductCatalog
import org.http4s.server.blaze._
import sttp.client.asynchttpclient.zio.SttpClient
import zio._
import zio.config.syntax._
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.logging._

object Main extends zio.App {

  import AppConfig._

  val logging = Logging.console(
    format = (_, entry) => entry,
    rootLoggerName = Some("order-taking")
  )
  val sttp: Layer[Throwable, SttpClient] = cfg.narrow(_.http) >>> SttpZioClient.live
  val addressValidator: Layer[Throwable, AddressValidator] = (cfg.narrow(_.addresses) ++ sttp) >>> AddressValidator.live
  val acknowledgeSender: Layer[Throwable, AcknowledgeSender] =
    (cfg.narrow(_.acknowledgeSender) ++ sttp ++ Letters.dummy) >>> AcknowledgeSender.live
  val productCatalog = (cfg.narrow(_.productCatalog)) >>> ProductCatalog.live
  val kafkaEventPublisher = (cfg.narrow(_.kafka) ++ logging) >>> KafkaEventPublisher.live

  val layers = productCatalog ++ addressValidator ++ acknowledgeSender ++ kafkaEventPublisher ++ logging

  val server: ZIO[ZEnv, Throwable, Unit] = ZIO
    .runtime[ZEnv]
    .flatMap { implicit rts =>
      ZIO
        .environment[Api.Dependencies]
        .flatMap { deps =>
          BlazeServerBuilder[Task]
            .bindHttp(8080, "localhost")
            .withHttpApp(Api.service(deps))
            .serve
            .compile
            .drain
        }
        .provideCustomLayer(ZEnv.live >>> layers)
    }

  def run(args: List[String]) =
    server.fold(e => {
      e.printStackTrace()
      1
    }, _ => 0)
}
