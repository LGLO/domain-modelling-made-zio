package ordertaking

import org.http4s.server.blaze._
import zio._
import zio.interop.catz._
import zio.interop.catz.implicits._
import zio.logging._

object Main extends zio.App {

  val logger = zio.logging.slf4j.Slf4jLogger.make { (context, message) =>
    val correlationId = LogAnnotation.CorrelationId.render(
      context.get(LogAnnotation.CorrelationId)
    )
    "[correlationId = %s] %s".format(correlationId, message)
  }

  val deps: Api.Dependencies = ???

  val server: ZIO[ZEnv, Throwable, Unit] = ZIO
    .runtime[ZEnv]
    .flatMap { implicit rts =>
      BlazeServerBuilder[Task]
        .bindHttp(8080, "localhost")
        .withHttpApp(Api.service(deps))
        .serve
        .compile
        .drain
    }

  def run(args: List[String]) = server.fold(_ => 1, _ => 0)
}
