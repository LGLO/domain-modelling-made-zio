package ordertaking.infra

import java.net.URI
import java.util.concurrent.TimeUnit

import ordertaking.infra.SttpZioClient
import ordertaking.services.AcknowledgeSender.AcknowledgeSenderConfig
import ordertaking.services.AddressValidator
import ordertaking.services.ProductCatalog
import sttp.model.Uri
import zio.config.magnolia.DeriveConfigDescriptor
import zio.config.magnolia.DeriveConfigDescriptor.Descriptor
import zio.config.typesafe._
import zio.duration.Duration

import scala.concurrent.duration.FiniteDuration

case class AppConfig(
    addresses: AddressValidator.Config,
    acknowledgeSender: AcknowledgeSenderConfig,
    http: SttpZioClient.Config,
    productCatalog: ProductCatalog.Config
)

object AppConfig {
  implicit val sttpUriDescriptor: Descriptor[Uri] =
    Descriptor(implicitly[Descriptor[URI]].apply(Uri(_), uri => Some(uri.toJavaUri)))

  implicit val finiteDurationDescriptor: Descriptor[FiniteDuration] =
    Descriptor(
      implicitly[Descriptor[Duration]]
        .apply[FiniteDuration](z => FiniteDuration(z.toNanos, TimeUnit.NANOSECONDS), s => Some(Duration.fromScala(s)))
    )

  val configDescription = DeriveConfigDescriptor.descriptor[AppConfig]
  val cfg = TypesafeConfig.fromDefaultLoader(configDescription)
}
