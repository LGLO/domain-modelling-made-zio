package ordertaking

import zio.Has
import ordertaking.services

object Layers {
  type ProductCatalog = Has[services.ProductCatalog.Service]
  type AddressValidator = Has[services.AddressValidator.Service]
  type Letters = Has[services.Letters.Service]
  type AcknowledgeSender = Has[services.AcknowledgeSender.Service]
}
