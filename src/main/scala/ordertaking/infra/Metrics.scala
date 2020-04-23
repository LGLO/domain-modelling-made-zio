package ordertaking.infra

import zio._

object Metrics {
  type Metrics = Has[Metrics.Service]

  trait Service {
    def incrementCounter(counter: String): UIO[Unit]
  }
}
