package ordertaking.services

import java.net.URI
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
import sttp.client.asynchttpclient.zio.SttpClient
import sttp.client.circe._
import sttp.model.Uri
import zio._
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

  val live: ZLayer[Has[Config] with SttpClient with Letters, Nothing, AcknowledgeSender] =
    ZLayer
      .fromServices[Config, SttpBackend[Task, Nothing, WebSocketHandler], Letters.Service, Service](
        AcknowledgeSenderLive(_, _, _)
      )

  def sendAcknowledgment(pricedOrder: PricedOrder): ZIO[Has[Service] with Logging, Nothing, SendResult] =
    ZIO.accessM(_.get.sendAcknowledgment(pricedOrder))

  case class Config(uri: URI)

  private case class AcknowledgeDto(html: String)
  private case class AcknowledgeSenderLive(
      config: Config,
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
      val request = basicRequest.body(makeEmail(letter.value)).post(Uri(config.uri))
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
