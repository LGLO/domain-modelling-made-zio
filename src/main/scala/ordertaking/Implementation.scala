package ordertaking

import PublicTypes._
import Types._
import ordertaking._
import ordertaking.services.AcknowledgeSender._
import ordertaking.services.AddressValidator._
import ordertaking.services.ProductCatalog._
import ordertaking.services.{AcknowledgeSender, AddressValidator, ProductCatalog}
import zio.ZIO
import zio.logging.Logging

object Implementation {

  case class ValidatedOrderLine(
      orderLineId: OrderLineId,
      productCode: ProductCode,
      quantity: OrderQuantity
  )

  case class ValidatedOrder(
      orderId: OrderId,
      customerInfo: CustomerInfo,
      shippingAddress: Address,
      billingAddress: Address,
      lines: List[ValidatedOrderLine]
  )

// ======================================================
// Section 2 : Implementation
// ======================================================

  def toCustomerInfo(unvalidatedCustomerInfo: UnvalidatedCustomerInfo): Result[ValidationError, CustomerInfo] =
    for {
      firstName <- String50
        .create("FirstName", unvalidatedCustomerInfo.firstName)
        .mapError(ValidationError(_))
      lastName <- String50
        .create("LastName", unvalidatedCustomerInfo.lastName)
        .mapError(ValidationError(_))
      emailAddress <- EmailAddress
        .create("EmailAddress", unvalidatedCustomerInfo.emailAddress)
        .mapError(ValidationError(_))
    } yield CustomerInfo(
      PersonalName(firstName, lastName),
      emailAddress
    )

// ---------------------------
// ValidateOrder step
// ---------------------------

  def toAddress(checkedAddress: CheckedAddress): Result[ValidationError, Address] = {
    val address = checkedAddress.address
    for {
      addressLine1 <- String50
        .create("AddressLine1", address.addressLine1)
        .mapError(ValidationError(_))
      addressLine2 <- String50
        .createOption("AddressLine1", address.addressLine2)
        .mapError(ValidationError(_))
      addressLine3 <- String50
        .createOption("AddressLine1", address.addressLine3)
        .mapError(ValidationError(_))
      addressLine4 <- String50
        .createOption("AddressLine1", address.addressLine4)
        .mapError(ValidationError(_))
      city <- String50
        .create("City", address.city)
        .mapError(ValidationError(_))
      zipCode <- ZipCode
        .create("ZipCode", address.zipCode)
        .mapError(ValidationError(_))
    } yield Address(
      addressLine1,
      addressLine2,
      addressLine3,
      addressLine4,
      city,
      zipCode
    )
  }

  // Call the checkAddress and convert the error to a ValidationError
  def toCheckedAddress(address: UnvalidatedAddress): ZIO[AddressValidator, ValidationError, CheckedAddress] =
    AddressValidator.checkAddress(address).mapError {
      case AddressNotFound => ValidationError("Address not found")
      case InvalidFormat   => ValidationError("Address has bad format")
    }

  def toOrderId(orderId: String): Result[ValidationError, OrderId] =
    OrderId
      .create("OrderId", orderId)
      .mapError(
        ValidationError(_)
      ) // convert creation error into ValidationError

// Helper function for validateOrder
  def toOrderLineId(orderLineId: String): Result[ValidationError, OrderLineId] =
    OrderLineId
      .create("OrderLineId", orderLineId)
      .mapError(
        ValidationError(_)
      ) // convert creation error into ValidationError

  // Helper function for validateOrder
  def toProductCode(
      productCode: String
  ): ZIO[ProductCatalog, ValidationError, ProductCode] = {
    // create a ProductCode -> Result<ProductCode,...> function
    // suitable for using in a pipeline
    def checkProduct(productCode: ProductCode) =
      ProductCatalog.checkProductExists(productCode).flatMap { exists =>
        if (exists) ZIO.succeed(productCode)
        else ZIO.fail(ValidationError(s"Invalid: $productCode"))
      }

    // assemble the pipeline
    ProductCode
      .create("ProductCode", productCode)
      .mapError(ValidationError)
      .toAsyncResult
      .flatMap(checkProduct)
  }

  // Helper function for validateOrder
  def toOrderQuantity(
      productCode: ProductCode,
      quantity: BigDecimal
  ): Result[ValidationError, OrderQuantity] =
    OrderQuantity
      .create("OrderQuantity", productCode, quantity)
      .mapError(
        ValidationError(_)
      ) // convert creation error into ValidationError

// Helper function for validateOrder
  def toValidatedOrderLine(
      unvalidatedOrderLine: UnvalidatedOrderLine
  ): ZIO[ProductCatalog, ValidationError, ValidatedOrderLine] =
    for {
      orderLineId <- toOrderLineId(unvalidatedOrderLine.orderLineId).toAsyncResult
      productCode <- toProductCode(unvalidatedOrderLine.productCode)
      quantity <- toOrderQuantity(productCode, unvalidatedOrderLine.quantity).toAsyncResult
    } yield ValidatedOrderLine(orderLineId, productCode, quantity)

  def validateOrder(
      unvalidatedOrder: UnvalidatedOrder
  ): ZIO[ProductCatalog with AddressValidator, ValidationError, ValidatedOrder] =
    for {
      orderId <- toOrderId(unvalidatedOrder.orderId).toAsyncResult
      customerInfo <- toCustomerInfo(unvalidatedOrder.customerInfo).toAsyncResult
      checkedShippingAddress <- toCheckedAddress(unvalidatedOrder.shippingAddress)
      shippingAddress <- toAddress(checkedShippingAddress).toAsyncResult
      checkedBillingAddress <- toCheckedAddress(unvalidatedOrder.billingAddress)
      billingAddress <- toAddress(checkedBillingAddress).toAsyncResult
      lines <- ZIO.collectAll(unvalidatedOrder.lines.map(toValidatedOrderLine))
    } yield ValidatedOrder(
      orderId,
      customerInfo,
      shippingAddress,
      billingAddress,
      lines
    )

// ---------------------------
// PriceOrder step
// ---------------------------

  def toPricedOrderLine(validatedOrderLine: ValidatedOrderLine): ZIO[ProductCatalog, PricingError, PricedOrderLine] = {
    val qty = validatedOrderLine.quantity.value
    (for {
      price <- ProductCatalog.getProductPrice(validatedOrderLine.productCode)
      linePrice <- Price
        .multiply(qty, price)
        .toAsyncResult
    } yield linePrice)
      .mapError(PricingError)
      .map { linePrice =>
        PricedOrderLine(
          validatedOrderLine.orderLineId,
          validatedOrderLine.productCode,
          validatedOrderLine.quantity,
          linePrice
        )
      }
  }

  def priceOrder(validatedOrder: ValidatedOrder): ZIO[ProductCatalog, PricingError, PricedOrder] =
    for {
      lines <- ZIO.collectAll(
        validatedOrder.lines.map(toPricedOrderLine)
      )
      amountToBill <- BillingAmount
        .sumPrices(lines.map(_.linePrice)) // get each line price & add them together as a BillingAmount
        .mapError(PricingError(_))
        .toAsyncResult // convert to PlaceOrderError
    } yield PricedOrder(
      validatedOrder.orderId,
      validatedOrder.customerInfo,
      validatedOrder.shippingAddress,
      validatedOrder.billingAddress,
      amountToBill,
      lines
    )

// ---------------------------
// AcknowledgeOrder step
// ---------------------------

  def acknowledgeOrder(
      pricedOrder: PricedOrder
  ): ZIO[AcknowledgeSender with Logging, Nothing, Option[OrderAcknowledgmentSent]] = {
    // if the acknowledgement was successfully sent,
    // return the corresponding event, else return None
    AcknowledgeSender.sendAcknowledgment(pricedOrder).map {
      case AcknowledgeSender.Sent =>
        val event = OrderAcknowledgmentSent(
          orderId = pricedOrder.orderId,
          emailAddress = pricedOrder.customerInfo.emailAddress
        )
        Some(event)
      case _ => None
    }
  }

// ---------------------------
// Create events
// ---------------------------

  def createOrderPlacedEvent(placedOrder: PricedOrder): OrderPlaced = OrderPlaced(placedOrder)

  def createBillingEvent(placedOrder: PricedOrder): Option[BillableOrderPlaced] = {
    val billingAmount = placedOrder.amountToBill.value
    if (billingAmount > BigDecimal(0.0))
      Some(
        BillableOrderPlaced(
          placedOrder.orderId,
          placedOrder.billingAddress,
          placedOrder.amountToBill
        )
      )
    else
      None
  }

  def createEvents(
      pricedOrder: PricedOrder,
      acknowledgmentEventOpt: Option[OrderAcknowledgmentSent]
  ): List[PlaceOrderEvent] = {
    val acknowledgmentEvents =
      acknowledgmentEventOpt.toList
    val orderPlacedEvents =
      List(createOrderPlacedEvent(pricedOrder))
    val billingEvents =
      createBillingEvent(pricedOrder).toList

    acknowledgmentEvents ++ orderPlacedEvents ++ billingEvents
  }

  // ---------------------------
// overall workflow
// ---------------------------

  def placeOrder(
      unvalidatedOrder: UnvalidatedOrder
  ): ZIO[ProductCatalog with AddressValidator with AcknowledgeSender with Logging, PlaceOrderError, List[
    PlaceOrderEvent
  ]] =
    for {
      validatedOrder <- validateOrder(unvalidatedOrder)
      pricedOrder <- priceOrder(validatedOrder)
      acknowledgementOption <- acknowledgeOrder(pricedOrder)
    } yield createEvents(pricedOrder, acknowledgementOption)

}
