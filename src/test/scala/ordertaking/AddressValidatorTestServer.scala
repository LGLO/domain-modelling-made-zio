package ordertaking

import org.http4s.server.blaze._
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._

object AddressValidatorTestServer extends App {
  
private val dsl = Http4sDsl[Task]
  import dsl._

  private implicit def addressFormDtoDecoder = jsonOf[Task, AddressDto]

  val service =
    HttpRoutes
      .of[Task] {
        case req @ POST -> Root / "check_address" =>
        req.as[AddressDto].map { address =>
          if (address.zipCode.startsWith("00"))
            NotFound("Address doesn't exists")
          else
            Ok("Address exists")
        }
      }
      .orNotFound

  
  def run(args: List[String]): ZIO[ZEnv,Nothing,Int] = ZIO
    .runtime[ZEnv]
    .flatMap { implicit rts =>
      BlazeServerBuilder[Task]
        .bindHttp(27001, "localhost")
        .withHttpApp(service)
        .serve
        .compile
        .drain
    }.fold(_ => 1, _ => 0)
}