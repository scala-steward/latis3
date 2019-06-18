package latis.output

import java.io.OutputStream
import latis.model.Dataset
import latis.util.StreamUtils._
import cats.effect.IO
import fs2._

case class TextWriter(out: OutputStream) {
  
  def write(dataset: Dataset): Unit =
    TextEncoder.encode(dataset)
               .through(text.utf8Encode)
               .through(OutputStreamWriter.unsafeFromOutputStream[IO](out).write)
               .compile.drain.unsafeRunSync()

}

object TextWriter {

  def apply(): TextWriter = TextWriter(System.out)
}