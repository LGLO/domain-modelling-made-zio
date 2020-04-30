package ordertaking.services

import AddressValidator._
import io.circe.generic.auto._
import ordertaking.Dto.AddressDto
import ordertaking.PublicTypes._
import sttp.client._
import sttp.client.asynchttpclient.WebSocketHandler
import sttp.client.asynchttpclient.zio._
import sttp.client.circe._
import sttp.model.StatusCodes
import sttp.model.Uri
import zio._

object AddressValidator {

  type AddressValidator = Has[Service]

  sealed trait AddressValidationError
  case object InvalidFormat extends AddressValidationError
  case object AddressNotFound extends AddressValidationError

  case class CheckedAddress(address: UnvalidatedAddress)

  trait Service {
    def checkAddress(unvalidatedAddress: UnvalidatedAddress): IO[AddressValidationError, CheckedAddress]
  }

  val dummy: ZLayer[Any, Nothing, Has[Service]] =
    ZLayer.succeed {
      new Service {
        def checkAddress(unvalidatedAddress: UnvalidatedAddress): IO[AddressValidationError, CheckedAddress] =
          IO.succeed(CheckedAddress(unvalidatedAddress))
      }
    }

  val live: ZLayer[Has[Config] with SttpClient, Nothing, AddressValidator] =
    ZLayer.fromServices[Config, SttpBackend[Task, Nothing, WebSocketHandler], Service] { (c, backend) =>
      AddressValidatorLive(c, backend)
    }

  /** clientId and secret are not used for simplicity */
  case class Config(uri: Uri, clientId: String, secret: String)

  def checkAddress(address: UnvalidatedAddress): ZIO[Has[Service], AddressValidationError, CheckedAddress] =
    ZIO.accessM(_.get.checkAddress(address))

}

case class AddressValidatorLive(config: AddressValidator.Config, backend: SttpBackend[Task, Nothing, WebSocketHandler])
    extends AddressValidator.Service
    with StatusCodes {
  override def checkAddress(address: UnvalidatedAddress): IO[AddressValidationError, CheckedAddress] = {
    val addressDto: AddressDto = AddressDto(
      address.addressLine1,
      address.addressLine2,
      address.addressLine3,
      address.addressLine4,
      address.city,
      address.zipCode
    )
    val request = basicRequest.post(config.uri).body(addressDto)

    backend
      .send(request)
      .orDie
      .flatMap { response =>
        if (response.code.isSuccess) ZIO.succeed(CheckedAddress(address))
        else if (response.code == NotFound) ZIO.fail(AddressNotFound)
        else ZIO.fail(InvalidFormat)
      }
  }
}
