package ordertaking

import org.http4s.server.blaze._
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.logging._

import zio.config.typesafe._
import zio.config.syntax._
import zio.config.magnolia.DeriveConfigDescriptor
import ordertaking.services.AddressValidator
import ordertaking.services.AddressValidator.AddressValidator
import ordertaking.services.AcknowledgeSender.AcknowledgeSenderConfig
import ordertaking.infra.SttpZioClient
import scala.concurrent.duration.FiniteDuration

import sttp.model.Uri
import zio.config.magnolia.DeriveConfigDescriptor.Descriptor
import java.net.URI
import zio.duration.Duration
import java.util.concurrent.TimeUnit
import ordertaking.services.AcknowledgeSender
import ordertaking.services.AcknowledgeSender.AcknowledgeSender
import ordertaking.services.Letters
import ordertaking.services.ProductCatalog
import sttp.client.asynchttpclient.zio.SttpClient

object Main extends zio.App {

  val logger = zio.logging.slf4j.Slf4jLogger.make { (context, message) =>
    val correlationId = LogAnnotation.CorrelationId.render(
      context.get(LogAnnotation.CorrelationId)
    )
    "[correlationId = %s] %s".format(correlationId, message)
  }

  case class AppConfig(
      addresses: AddressValidator.Config,
      acknowledgeSender: AcknowledgeSenderConfig,
      http: SttpZioClient.Config,
      productCatalog: ProductCatalog.Config
  )
//  implicit val implicitUriDesc: Descriptor[URI]                     = Descriptor(uriDesc)
//  protected def uriDesc: ConfigDescriptor[String, String, URI]                     = uri
//val uri: ConfigDescriptor[String, String, URI] = ConfigDescriptor.Source(ConfigSource.empty, PropertyType.UriType) ?? "value of type uri"

  implicit val sttpUriDescriptor: Descriptor[Uri] =
    Descriptor(implicitly[Descriptor[URI]].apply(Uri(_), uri => Some(uri.toJavaUri)))
  //implicit val finiteDurationDescriptor = implicitly[Descriptor[Duration]].apply(z => FiniteDuration(z.toNanos, TimeUnit.NANOSECONDS), s => Some(Duration.fromNanos(s)))
  implicit val finiteDurationDescriptor: Descriptor[FiniteDuration] =
    Descriptor(
      implicitly[Descriptor[Duration]]
        .apply[FiniteDuration](z => FiniteDuration(z.toNanos, TimeUnit.NANOSECONDS), s => Some(Duration.fromScala(s)))
    )

  val configDescription = DeriveConfigDescriptor.descriptor[AppConfig]
  val cfg = TypesafeConfig.fromDefaultLoader(configDescription)

  val sttp: Layer[Throwable, SttpClient] = cfg.narrow(_.http) >>> SttpZioClient.live
  val addressValidator: Layer[Throwable, AddressValidator] = (cfg.narrow(_.addresses) ++ sttp) >>> AddressValidator.live
  val acknowledgeSender: Layer[Throwable, AcknowledgeSender] =
    (cfg.narrow(_.acknowledgeSender) ++ sttp ++ Letters.dummy) >>> AcknowledgeSender.live
  val productCatalog = (cfg.narrow(_.productCatalog)) >>> ProductCatalog.live

  val logging = Logging.console(
    format = (_, entry) => entry,
    rootLoggerName = Some("order-taking")
  )

  val layers = productCatalog ++ addressValidator ++ acknowledgeSender ++ logging

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
        .provideLayer(ZEnv.live ++ (ZEnv.live >>> layers))
    }

  def run(args: List[String]) =
    server.fold(e => {
      e.printStackTrace()
      1
    }, _ => 0)
}
