import zio.IO

package object ordertaking {
  type Result[E, T] = Either[E, T]
  type Error[E, T] = Left[E, T]
  type Ok[E, T] = Right[E, T]

  implicit class EitherOps[E, T](a: Either[E, T]) {
    def mapError[E1](f: E => E1): Result[E1, T] = a.fold(e => Left(f(e)), _ => a.asInstanceOf[Result[E1, T]])

    def toAsyncResult: IO[E, T] = a.fold(e => IO.fail(e), v => IO.succeed(v))
  }
}
