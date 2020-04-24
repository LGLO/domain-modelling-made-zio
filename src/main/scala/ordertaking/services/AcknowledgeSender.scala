package ordertaking.services

import ordertaking.Types.EmailAddress
import ordertaking.PublicTypes.PricedOrder
import ordertaking.services.Letters
import ordertaking.services.Letters._
import zio._
import zio.console.Console

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
    def sendAcknowledgment(pricedOrder: PricedOrder): UIO[SendResult]
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

  def sendAcknowledgment(pricedOrder: PricedOrder): ZIO[Has[Service], Nothing, SendResult] =
    ZIO.accessM(_.get.sendAcknowledgment(pricedOrder))

}
