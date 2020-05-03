package ordertaking.services

import java.net.URI

import AcknowledgeSender._
import ordertaking.PublicTypes.PricedOrder
import ordertaking.TestValues._
import ordertaking.Types.OrderId
import sttp.client.Response
import sttp.client.StringBody
import sttp.client.SttpBackend
import sttp.client.asynchttpclient.zio.AsyncHttpClientZioBackend
import sttp.client.impl.zio.TaskMonadAsyncError
import sttp.client.monad.MonadError
import sttp.client.ws.WebSocketResponse
import zio._
import zio.logging.Logging
import zio.test._
import zio.test.Assertion.equalTo

object AcknowledgeSenderTests extends DefaultRunnableSpec {

  val config = Config(URI.create("http://this-is-test.com/some-path"))

  val orderOk: PricedOrder = PricedOrder(
    orderId = OrderId("test-ord-1"),
    customerInfo = abCustomerInfo,
    shippingAddress = magnoliaStreet42,
    billingAddress = devNull0,
    amountToBill = billingAmount1,
    lines = List.empty
  )

  val orderWrong = orderOk.copy(orderId = OrderId("won-t-match"))

  val dummySttp: ZLayer[Any, Nothing, Has[SttpBackend[Task, Nothing, Nothing]]] = ZLayer.succeed {
    AsyncHttpClientZioBackend.stub
      .whenRequestMatches { r =>
        r.uri.path.headOption.contains("some-path") &&
        r.body.isInstanceOf[StringBody] &&
        r.body.asInstanceOf[StringBody].s.contains(orderOk.orderId.value)
      }
      .thenRespond("Cool!")
      .whenRequestMatches { r =>
        r.body.isInstanceOf[StringBody] &&
        r.body.asInstanceOf[StringBody].s.contains(orderWrong.orderId.value)
      }
      .thenRespondNotFound()
  }

  //Perhaps useful in other places, candidate to extract
  val failingSttp = ZLayer.succeed(
    new SttpBackend[Task, Nothing, Nothing] {
      override def send[T](request: sttp.client.Request[T, Nothing]): zio.Task[Response[T]] =
        ZIO.fail(new Exception("Boom!"))
      override def close(): zio.Task[Unit] = ZIO.succeed(())
      override def openWebsocket[T, WS_RESULT](
          request: sttp.client.Request[T, Nothing],
          handler: Nothing
      ): zio.Task[WebSocketResponse[WS_RESULT]] = ZIO.never
      override def responseMonad: MonadError[zio.Task] = TaskMonadAsyncError
    }
  )

  val consoleLogging = Logging.console((_, s) => s)

  val senderDependencies = ZLayer.succeed(config) ++ dummySttp ++ Letters.live

  val layer = (senderDependencies >>> AcknowledgeSender.live) ++ consoleLogging

  val failingSttpDependencies = ZLayer.succeed(config) ++ failingSttp ++ Letters.live

  val failingSttpLayer = (failingSttpDependencies >>> AcknowledgeSender.live) ++ consoleLogging

  override def spec =
    suite("AcknowledgeSender")(
      testM("Returns 'Sent' when request succeeds") {
        assertM(sendAcknowledgment(orderOk))(equalTo(Sent))
      },
      testM("Returns 'NotSent' when request result isn't success") {
        assertM(sendAcknowledgment(orderWrong))(equalTo(NotSent))
      },
      testM("Returns 'NotSent' when request fails") {
        assertM(sendAcknowledgment(orderOk))(equalTo(NotSent))
      }.provideCustomLayer(failingSttpLayer)
    ).provideCustomLayer(layer)
}
