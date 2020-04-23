package ordertaking.services

import ordertaking.PublicTypes._
import zio._

object AddressValidator {

  sealed trait AddressValidationError
  case object InvalidFormat extends AddressValidationError
  case object AddressNotFound extends AddressValidationError

  case class CheckedAddress(address: UnvalidatedAddress)

  trait Service {
    def checkAddress(unvalidateAddress: UnvalidatedAddress): IO[AddressValidationError, CheckedAddress]
  }

  val dummy: ZLayer[Any, Nothing, Has[AddressValidator.Service]] = ZLayer.succeed {
    new Service {
      def checkAddress(unvalidatedAddress: UnvalidatedAddress): IO[AddressValidationError, CheckedAddress] =
        IO.succeed(CheckedAddress(unvalidatedAddress))
    }
  }

  def checkAddress(
      unvalidateAddress: UnvalidatedAddress
  ): ZIO[Has[AddressValidator.Service], AddressValidationError, CheckedAddress] =
    ZIO.accessM(_.get.checkAddress(unvalidateAddress))

}
