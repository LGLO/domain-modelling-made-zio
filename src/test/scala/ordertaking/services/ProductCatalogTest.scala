package ordertaking.services

import ProductCatalog.{checkProductExists, getProductPrice}
import ordertaking.Types.GizmoCode
import ordertaking.Types.Price
import ordertaking.Types.WidgetCode
import zio._
import zio.test._
import zio.test.Assertion._

object ProductCatalogTest extends DefaultRunnableSpec {

  val validConfig = ZLayer.succeed(ProductCatalog.Config("/test-catalog"))

  def catalog = (validConfig >>> ProductCatalog.live).mapError(TestFailure.fail(_))

  val g007 = GizmoCode("G007")
  val g321 = GizmoCode("G321")
  val w0005 = WidgetCode("W0005")
  val w9999 = WidgetCode("W9999")
  override def spec =
    suite("File based ProductCatalog")(
      testM("loads from config files") {
        assertM(ProductCatalog.live.build.fold(_ => 1, _ => 0).use(ZIO.succeed(_)))(equalTo(0))
      }.provideCustomLayer(validConfig),
      testM("fails if path is wrong from config files") {
        assertM(ProductCatalog.live.build.fold(_ => 1, _ => 0).use(ZIO.succeed(_)))(equalTo(1))
      }.provideCustomLayer(ZLayer.succeed(ProductCatalog.Config("/bad-folder"))),
      testM("checks if gizmo code exists") {
        assertM(checkProductExists(g321).zip(checkProductExists(g007)))(equalTo(true, false))
      },
      testM("checks if widget code exists") {
        assertM(checkProductExists(w0005).zip(checkProductExists(w9999)))(equalTo(true, false))
      },
      testM("get gizmo price") {
        assertM(getProductPrice(g321))(equalTo(Price(BigDecimal(64.0))))
      },
      testM("get widget price") {
        assertM(getProductPrice(w0005))(equalTo(Price(BigDecimal(100.11))))
      }
    ).provideCustomLayer(catalog)

}
