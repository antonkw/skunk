package example

import cats.Monad
import cats.effect.{ ExitCode, IO, IOApp, Resource }
import cats.implicits._
import skunk.Session
import skunk.implicits._
import skunk.codec.numeric.{ int4, float8 }

object Math2 extends IOApp {

  val session: Resource[IO, Session[IO]] =
    Session.single(
      host     = "localhost",
      port     = 5432,
      user     = "postgres",
      database = "world",
    )

  // An algebra for doing math.
  trait Math[F[_]] {
    def add(a: Int, b: Int): F[Int]
    def sqrt(d: Double): F[Double]
  }

  object Math {

    object Statements {
      val add  = sql"select $int4 + $int4".query(int4)
      val sqrt = sql"select sqrt($float8)".query(float8)
    }

    // `Math` implementation that delegates its work to Postgres. We prepare the statements up front
    // so users can call them as much as they like and never have to pay the preparation cost again.
    def fromSession[F[_]: Monad](sess: Session[F]): Resource[F, Math[F]] =
      for {
        pAdd  <- sess.prepare(Statements.add)
        pSqrt <- sess.prepare(Statements.sqrt)
      } yield
        new Math[F] {
          def add(a: Int, b: Int) = pAdd.unique(a ~ b)
          def sqrt(d: Double)     = pSqrt.unique(d)
        }

  }

  def run(args: List[String]): IO[ExitCode] =
    session.flatMap(Math.fromSession(_)).use { m =>
      for {
        n  <- m.add(42, 71)
        d  <- m.sqrt(2)
        d2 <- m.sqrt(42)
        _  <- IO(println(s"The answers were $n and $d and $d2"))
      } yield ExitCode.Success
    }

}