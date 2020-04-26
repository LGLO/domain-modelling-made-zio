package ordertaking.services

import ordertaking.Types.Price
import ordertaking.Types.ProductCode
import zio._

import scala.io.Source

object ProductCatalog {
  type ProductCatalog = Has[Service]

  case class Config(catalogPath: String)
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

  val live: ZLayer[Has[Config], Throwable, Has[Service]] = {
    def readCatalog(fileName: String): Task[Map[ProductCode, Price]] = ZIO.effect {
      Source
        .fromInputStream(getClass().getResourceAsStream(fileName))
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
        path <- ZIO.access[Has[Config]](_.get.catalogPath)
        gizmos <- readCatalog(path + "gizmos")
        widgets <- readCatalog(path + "widgets")
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
