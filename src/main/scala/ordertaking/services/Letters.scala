package ordertaking.services

import ordertaking.PublicTypes.PricedOrder
import zio._

object Letters {
  type Letters = Has[Service]

  case class HtmlString(value: String) extends AnyVal

  trait Service {
    def acknowledgeLetter(pricedOrder: PricedOrder): HtmlString
  }

  val dummy = ZLayer.succeed {
    new Service {
      def acknowledgeLetter(pricedOrder: PricedOrder): HtmlString =
        HtmlString(s"<p>${pricedOrder.orderId.value}</p>")
    }
  }

  def acknowledgeLetter(pricedOrder: PricedOrder): ZIO[Has[Service], Nothing, HtmlString] =
    ZIO.access(_.get.acknowledgeLetter(pricedOrder))

}
