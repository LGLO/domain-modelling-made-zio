package ordertaking

import Dto._
import PublicTypes._
import io.circe._
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s._
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import org.http4s.implicits._
import zio._
import zio.interop.catz._
import ordertaking.services.AcknowledgeSender._
import ordertaking.services.AddressValidator._
import ordertaking.services.ProductCatalog._
import zio.logging.Logging

object Api {

  type Dependencies = ZEnv with AcknowledgeSender with ProductCatalog with AddressValidator with Logging

  implicit val encodePlaceOrderEvent: Encoder[PlaceOrderEventDto] =
    Encoder.instance {
      case op: OrderPlacedDto =>
        JsonObject("orderPlaced" -> op.asJson).asJson
      case bop: BillableOrderPlacedDto =>
        JsonObject("billableOrderPlaced" -> bop.asJson).asJson
      case oas: OrderAcknowledgmentSentDto =>
        JsonObject("orderAcknowledgmentSentDto" -> oas.asJson).asJson
    }

  //JSON things...
  private implicit def orderFormDtoDecoder = jsonOf[Task, OrderFormDto]

  private implicit def placeOrderErrorEncoder = jsonEncoderOf[Task, PlaceOrderErrorDto]

  private implicit def placeOrderEventsEncoder: EntityEncoder[Task, List[ordertaking.Dto.PlaceOrderEventDto]] =
    jsonEncoderOf[Task, List[PlaceOrderEventDto]]

  // -------------------------------
  // workflow
  // -------------------------------

  private val dsl = Http4sDsl[Task]
  import dsl._
  /// This function converts the workflow output into a HttpResponse
  def workflowResultToHttpReponse(
      result: Either[PlaceOrderError, List[PlaceOrderEvent]]
  ) = result.fold(
    err => BadRequest(PlaceOrderErrorDto.fromDomain(err)),
    events => Ok(events.map(PlaceOrderEventDto.fromDomain))
  )

  def placeOrderApi(
      req: Request[Task]
  ): ZIO[Dependencies, Throwable, Response[Task]] =
    for {
      dto <- req.as[OrderFormDto]
      reponse <- Implementation.placeOrder(dto.toUnvalidatedOrder).either
      httpResponse <- workflowResultToHttpReponse(reponse)
    } yield httpResponse

  def service(deps: Dependencies) =
    HttpRoutes
      .of[Task] {
        case req @ POST -> Root / "order" =>
          placeOrderApi(req).provide(deps)
        case GET -> Root =>
          Ok(
            OrderFormDto(
              "blah",
              CustomerInfoDto("John", "Doe", "john@doe.com"),
              AddressDto("line1", None, None, None, "Katowice", "40-000"),
              AddressDto("line1", None, None, None, "Katowice", "40-000"),
              List(
                OrderFormLineDto("ol1", "W1234", BigDecimal(3.0)),
                OrderFormLineDto("ol2", "G1234", BigDecimal(30.0))
              )
            ).asJson
          )
      }
      .orNotFound

}
