package ordertaking

import com.sksamuel.avro4s._
import ordertaking.Dto.PlaceOrderEventDto
import zio._
import zio.kafka.consumer._
import zio.kafka.serde.Deserializer

object PlaceOrderEventsKafkaConsumer extends App {

  val keyDeser = Deserializer.string
  val valueDeser = Deserializer((_, _, bytes) => {
    ZIO.effect {
      val schema = AvroSchema[PlaceOrderEventDto]
      val input = AvroInputStream.binary[PlaceOrderEventDto].from(bytes).build(schema)
      val result = List.from(input.iterator)
      input.close()
      result
    }
  })

  val consumer = Consumer.consumeWith(
    ConsumerSettings(bootstrapServers = List("172.17.0.1:29092")).withGroupId("test-consumers"),
    Subscription.topics("place-order-events"),
    keyDeser,
    valueDeser
  )((key, value) => console.putStrLn(s"$key: ${value.toString}"))

  override def run(args: List[String]): ZIO[zio.ZEnv, Nothing, Int] = {
    consumer
      .provideLayer(ZEnv.live)
      .foldM(
        err => console.putStrLn(s"Error happened: $err").as(1),
        _ => ZIO.succeed(0)
      )
  }

}
