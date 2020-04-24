package ordertaking.services

import ordertaking.Dto.AddressDto
import ordertaking.PublicTypes._
import zio._
import ordertaking.PublicTypes
import sttp.client.asynchttpclient.zio._
import io.circe.generic.auto._
import io.circe.Encoder
import io.circe.syntax._
import sttp.client._
import sttp.client.circe._
import sttp.model.Uri
import sttp.client.asynchttpclient.zio.SttpClient
import sttp.client.asynchttpclient.WebSocketHandler
import AddressValidator._
import sttp.model.StatusCodes

object AddressValidator {

  type AddressValidator = Has[Service]

  sealed trait AddressValidationError
  case object InvalidFormat extends AddressValidationError
  case object AddressNotFound extends AddressValidationError

  case class CheckedAddress(address: UnvalidatedAddress)

  trait Service {
    def checkAddress(unvalidatedAddress: UnvalidatedAddress): IO[AddressValidationError, CheckedAddress]
  }

  val dummy: ZLayer[Any, Nothing, Has[Service]] = ZLayer.succeed {
    new Service {
      def checkAddress(unvalidatedAddress: UnvalidatedAddress): IO[AddressValidationError, CheckedAddress] =
        IO.succeed(CheckedAddress(unvalidatedAddress))
    }
  }

  /** clientId and secret are not used for simplicity */
  case class Config(uri: Uri, clientId: String, secret: String)

  def checkAddress(
      unvalidatedAddress: UnvalidatedAddress
  ): ZIO[Has[Service], AddressValidationError, CheckedAddress] =
    ZIO.accessM(_.get.checkAddress(unvalidatedAddress))

}

case class AddressValidatorLive(config: AddressValidator.Config, backend: SttpBackend[Task, Nothing, WebSocketHandler])
    extends AddressValidator.Service
    with StatusCodes {
  override def checkAddress(unvalidatedAddress: UnvalidatedAddress): IO[AddressValidationError, CheckedAddress] = {
    val addressDto: AddressDto = AddressDto(
      unvalidatedAddress.addressLine1,
      unvalidatedAddress.addressLine2,
      unvalidatedAddress.addressLine3,
      unvalidatedAddress.addressLine4,
      unvalidatedAddress.city,
      unvalidatedAddress.zipCode
    )
    val request = basicRequest.post(config.uri).body(addressDto)

    backend
      .send(request)
      .orDie
      .flatMap { response =>
        if (response.code.isSuccess) ZIO.succeed(CheckedAddress(unvalidatedAddress))
        else if (response.code == NotFound) ZIO.fail(AddressNotFound)
        else ZIO.fail(InvalidFormat)
      }
  }
}
