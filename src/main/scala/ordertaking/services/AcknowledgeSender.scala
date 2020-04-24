package ordertaking.services

import java.nio.charset.StandardCharsets
import java.{util => ju}

import io.circe.generic.auto._
import ordertaking.PublicTypes.PricedOrder
import ordertaking.Types.EmailAddress
import ordertaking.services.Letters
import ordertaking.services.Letters._
import sttp.client.SttpBackend
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.circe._
import sttp.model.Uri
import zio._
import zio.console.Console
import zio.logging._

object AcknowledgeSender {
  type AcknowledgeSender = Has[Service]

  case class OrderAcknowledgment(
      emailAddress: EmailAddress,
      letter: HtmlString
  )

// Send the order acknowledgement to the customer
// Note that this does NOT generate an Result-type error (at least not in this workflow)
// because on failure we will continue anyway.
// On success, we will generate a OrderAcknowledgmentSent event,
// but on failure we won't.

  sealed trait SendResult
  case object Sent extends SendResult
  case object NotSent extends SendResult

  //createAcknowledgmentLetter: PricedOrder => HtmlString,
  trait Service {
    def sendAcknowledgment(pricedOrder: PricedOrder): ZIO[Logging, Nothing, SendResult]
  }

  val dummy: ZLayer[Any, Nothing, Has[Service]] = ZLayer.succeed {
    new Service {
      def sendAcknowledgment(pricedOrder: PricedOrder): UIO[SendResult] = {
        UIO.succeed(Sent)
      }
    }
  }

  val halfDummy: ZLayer[Console with Letters, Nothing, Has[Service]] = ZLayer.fromFunction { env =>
    new Service {
      def sendAcknowledgment(pricedOrder: PricedOrder): UIO[SendResult] = {
        (zio.console.putStrLn(s"Acknowleding order: ${pricedOrder.orderId}") *>
          Letters.acknowledgeLetter(pricedOrder) *>
          UIO.succeed(Sent)).provide(env)
      }
    }
  }

  //val live: ZLayer[Console with Letters with SttpClient, Nothing, Has[Service]] =
  //  ZLayer.fromService((backend: SttpBackend[Task, Nothing, WebSocketHandler]))

  def sendAcknowledgment(pricedOrder: PricedOrder): ZIO[Has[Service] with Logging, Nothing, SendResult] =
    ZIO.accessM(_.get.sendAcknowledgment(pricedOrder))

  case class AcknowledgeSenderConfig(uri: Uri)

  private case class AcknowledgeDto(html: String)
  private case class AcknowledgeSenderLive(
      config: AcknowledgeSenderConfig,
      backend: SttpBackend[Task, Nothing, WebSocketHandler],
      letters: Letters.Service
  ) extends Service {

    private def makeEmail(html: String) = {
      val encodedBytes = ju.Base64.getEncoder().encode(html.getBytes(StandardCharsets.UTF_8))
      val bytesAsString = new String(encodedBytes, StandardCharsets.US_ASCII)
      AcknowledgeDto(bytesAsString)
    }

    def sendAcknowledgment(pricedOrder: PricedOrder): ZIO[Logging, Nothing, SendResult] = {
      val letter = letters.acknowledgeLetter(pricedOrder)
      val request = basicRequest.body(makeEmail(letter.value)).post(config.uri)
      backend
        .send(request)
        .foldM(
          th =>
            log.error(s"Failed to send ${pricedOrder.orderId} acknowledgement", Cause.fail(th)) *>
              ZIO.succeed(NotSent),
          response =>
            if (response.code.isSuccess)
              ZIO.succeed(Sent)
            else
              log.error(
                s"Failed to send ${pricedOrder.orderId} acknowledgement, result code: ${response.code}, message: ${response.body}"
              ) *> ZIO.succeed(NotSent)
        )
    }
  }
}
