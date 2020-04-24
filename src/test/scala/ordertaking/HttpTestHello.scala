package ordertaking

import org.http4s._
import org.http4s.implicits._
import zio.Task
import zio.test.Assertion.equalTo
import zio.test.{DefaultRunnableSpec, assertM, suite, testM}

object HttpTestHello extends DefaultRunnableSpec {

  override def spec = suite("routes suite")(
    testM("test get") {
      ClientTest.testClientM { client =>
        val req = Request[Task](Method.GET, uri"http://localhost:8080/")
        assertM(client.status(req))(equalTo(Status.Ok))
      }
    }
  )

}
