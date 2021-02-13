package latis.lambda

import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.Executors

import scala.concurrent.ExecutionContext

import cats.effect.Blocker
import cats.effect.ContextShift
import cats.effect.IO
import cats.syntax.all._
import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestStreamHandler
import fs2.Stream
import io.circe.Json

final class LatisLambdaHandler extends RequestStreamHandler {

  private implicit val cs: ContextShift[IO] = {
    val ec = ExecutionContext.fromExecutor(Executors.newSingleThreadExecutor())
    IO.contextShift(ec)
  }

  override def handleRequest(
    is: InputStream,
    os: OutputStream,
    ctx: Context
  ): Unit = Blocker[IO].use { blocker =>
    parseInput(is, blocker).flatMap(writeOutput(os, blocker, _))
  }.void.unsafeRunSync()

  private def parseInput(is: InputStream, blocker: Blocker): IO[Json] =
    fs2.io.readInputStream(is.pure[IO], 4096, blocker)
      .through(fs2.text.utf8Decode)
      .compile
      .string
      .flatMap(s => IO.fromEither(io.circe.parser.parse(s)))

  private def writeOutput(
    os: OutputStream,
    blocker: Blocker,
    output: Json
  ): IO[Unit] = Stream.emit(output)
    .map(_.spaces2)
    .through(fs2.text.utf8Encode)
    .through(fs2.io.writeOutputStream(os.pure[IO], blocker))
    .compile
    .drain

}
