package ordertaking.services

import zio._
import ordertaking.Types.ProductCode
import ordertaking.Types.Price
import scala.io.Source

object ProductCatalog {
  type ProductCatalog = Has[Service]

  trait Service {
    def checkProductExists(pc: ProductCode): Boolean

    def getProductPrice(pc: ProductCode): Price
  }

  val dummy: ZLayer[Any, Nothing, Has[ProductCatalog.Service]] =
    ZLayer.succeed {
      new Service {
        def checkProductExists(pc: ProductCode): Boolean = true

        def getProductPrice(pc: ProductCode): Price = Price(BigDecimal(1.0))
      }
    }

  val live: ZLayer[ZEnv, Throwable, Has[Service]] = {
    def readCatalog(fileName: String): Task[Map[ProductCode, Price]] = ZIO.effect {
      Source
        .fromInputStream(getClass().getResourceAsStream("/" + fileName))
        .getLines()
        .map(line => line.split(" "))
        .map {
          case Array(code, price) =>
            ProductCode.unsafeCreate("code column", code) -> Price.unsafeCreate(BigDecimal(price))
          case notPair =>
            throw new Exception(s"Illegal catalog line: ${notPair.mkString}")
        }
        .toMap
    }
    ZLayer.fromEffect(ZIO.effectSuspend {
      for {
        path <- zio.system.env("CATALOG_PATH")
        gizmos <- readCatalog(path.getOrElse("") + "/gizmos")
        widgets <- readCatalog(path.getOrElse("") + "/widgets")
        catalog = gizmos ++ widgets
      } yield new Service {
        def checkProductExists(pc: ProductCode): Boolean = catalog.contains(pc)

        def getProductPrice(pc: ProductCode): Price = catalog(pc)
      }
    })
  }

  def checkProductExists(
      pc: ProductCode
  ): ZIO[Has[ProductCatalog.Service], Nothing, Boolean] =
    ZIO.access(_.get.checkProductExists(pc))

  def getProductPrice(pc: ProductCode): ZIO[Has[ProductCatalog.Service], Nothing, Price] =
    ZIO.access(_.get.getProductPrice(pc))
}
