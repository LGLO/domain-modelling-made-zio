package ordertaking.services

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

import com.sksamuel.avro4s._
import ordertaking.Dto.PlaceOrderEventDto
import ordertaking.PublicTypes._
import ordertaking.Types.OrderId
import org.apache.kafka.clients.producer.ProducerRecord
import zio._
import zio.blocking.Blocking
import zio.kafka.producer._
import zio.kafka.serde._
import zio.logging._

object KafkaEventPublisher {
  type KafkaEventPublisher = Has[Service]

  final case class Config(bootstrapHosts: List[String], topic: String)

  trait Service {
    def publishEvents(events: List[PlaceOrderEvent]): ZIO[Blocking, Throwable, Unit]
  }

  def live: ZLayer[Has[Config] with Logging, Throwable, KafkaEventPublisher] = {

    val keySer: Serializer[Any, OrderId] =
      Serializer((_, _, orderId) => ZIO.succeed(orderId.value.getBytes(StandardCharsets.US_ASCII)))

    val valueSer: Serializer[Any, List[PlaceOrderEvent]] = {

      val schema = AvroSchema[PlaceOrderEventDto]

      Serializer[Any, List[PlaceOrderEvent]]((_, _, events) =>
        ZIO.effect {
          val baos = new ByteArrayOutputStream()
          val os = AvroOutputStream.binary[PlaceOrderEventDto].to(baos).build(schema)
          events.foreach(event => os.write(PlaceOrderEventDto.fromDomain(event)))
          os.close()
          baos.toByteArray
        }
      )
    }

    ZLayer.fromServicesManaged[Config, Logger[String], Any, Throwable, Service] { (cfg, log) =>
      Producer.make(ProducerSettings(cfg.bootstrapHosts), keySer, valueSer).build.map { hasProducer =>
        new LivePublisher(hasProducer.get, log, cfg.topic)
      }
    }
  }

  def publishEvents(events: List[PlaceOrderEvent]): ZIO[Has[Service] with Blocking, Throwable, Unit] =
    ZIO.accessM(_.get.publishEvents(events))

  //TODO: Avro, because JSON sucks
  class LivePublisher(
      producer: Producer.Service[Any, OrderId, List[PlaceOrderEvent]],
      log: Logger[String],
      topic: String
  ) extends Service {
    def publishEvents(events: List[PlaceOrderEvent]): ZIO[Blocking, Throwable, Unit] = {
      val orderId = events.head match {
        case op: OrderPlaced              => op.pricedOrder.orderId
        case bop: BillableOrderPlaced     => bop.orderId
        case oas: OrderAcknowledgmentSent => oas.orderId
      }
      val producerRecord = new ProducerRecord(topic, orderId, events.toList)
      producer
        .produce(producerRecord)
        .flatMap(identity)
        .foldM(
          th => log.error(s"Not published $th") *> ZIO.fail(th),
          meta => log.info(s"Published $meta")
        )
    }
  }

}
