package ordertaking

import org.http4s.client.Client
import zio._

object HttpClient {

  type HttpClient = Has[HttpClient.Service]

  trait Service {
    def client: Client[Task]
  }

  case class WrappedClient(client: Client[Task]) extends Service

  def client = ZIO.access[HttpClient](has => has.get.client)

}
