package ordertaking

// import TestAspect._
// import org.http4s._
// import org.http4s.implicits._
// import zio._
// import zio.interop.catz._
// import zio.test.Assertion._
// import zio.test._

object IgnoreMe
/*object TestApiService extends DefaultRunnableSpec {
  override def spec =
    suite("routes suite")(
      testM("root request returns Ok") {
        for {
          response <- Api.service.run(Request[Task](Method.GET, uri"/"))
        } yield assert(response.status)(equalTo(Status.Ok))
      },
      testM("root request returns Ok, using assertM instead") {
        assertM(
          Api.service.run(Request[Task](Method.GET, uri"/")).map(_.status)
        )(equalTo(Status.Ok))
      },
      testM("root request returns Ok, using assertM instead") {
        assertM(
          Api.service.run(Request[Task](Method.GET, uri"/a")).map(_.status)
        )(equalTo(Status.NotFound))
      },
      testM("root request body returns hello!") {
        val io = for {
          response <- Api.service.run(Request[Task](Method.GET, uri"/"))
          body <- response.body.compile.toVector.map(x => x.map(_.toChar).mkString(""))
        } yield body
        assertM(io)(equalTo("hello1!"))
      }
    ) @@ sequential
}*/
