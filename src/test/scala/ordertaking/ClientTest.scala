package ordertaking

import ordertaking.HttpClient.HttpClient
import ordertaking.HttpClient.WrappedClient
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import zio.interop.catz
import zio.interop.catz._
import zio.test.TestResult
import zio.test._
import zio.{Task, ZIO, ZLayer, ZManaged}

object ClientTest {

  def testClientM[R](
      fClient: Client[Task] => Task[TestResult]
  ): Task[TestResult] =
    ZIO.runtime[Any].flatMap { implicit rts =>
      val exec = rts.platform.executor.asEC
      BlazeClientBuilder[Task](exec).resource.use { client => fClient(client) }
    }

  def clientManaged = {
    val zioManaged: ZIO[Any, Throwable, ZManaged[Any, Throwable, Client[Task]]] =
      ZIO.runtime[Any].map { rts =>
        val exec = rts.platform.executor.asEC

        implicit def rr = rts

        catz
          .catsIOResourceSyntax(BlazeClientBuilder[Task](exec).resource)
          .toManaged
      }
    // for our test we need a ZManaged, but right now we've got a ZIO of a ZManaged. To deal with
    // that we create a Managed of the ZIO and then flatten it
    val zm = zioManaged.toManaged_ // toManaged_ provides an empty release of the rescoure
    zm.flatten
  }

  def clientLive: ZLayer[Any, Throwable, HttpClient] =
    ZLayer.fromManaged(clientManaged.map(WrappedClient(_)))

}
