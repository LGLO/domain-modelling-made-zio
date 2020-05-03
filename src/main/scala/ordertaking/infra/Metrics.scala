package ordertaking.infra

import zio._

//TODO: use it...
object Metrics {
  type Metrics = Has[Metrics.Service]

  trait Service {
    def incrementCounter(counter: String): UIO[Unit]
  }
}
