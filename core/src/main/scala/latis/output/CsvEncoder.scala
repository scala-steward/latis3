package latis.output

import scala.util.Properties.lineSeparator
import cats.effect.IO
import fs2.Stream
import latis.data.Data
import latis.data.Sample
import latis.model.DataType
import latis.model.Dataset
import latis.model.Function
import latis.model.Scalar
import latis.ops.Uncurry

class CsvEncoder extends Encoder[IO, String] {

  /**
   * Encodes the Stream of Samples from the given Dataset as a Stream
   * of Strings with comma separated values.
   * @param dataset dataset to encode
   */
  override def encode(dataset: Dataset): Stream[IO, String] = {
    val uncurriedDataset = Uncurry()(dataset)
    // Encode each Sample as a String in the Stream
    uncurriedDataset.data.streamSamples
      .map(encodeSample(uncurriedDataset.model, _) + lineSeparator)
  }

  /**
   * Encodes a single Sample to a String of comma separated values.
   */
  def encodeSample(model: DataType, sample: Sample): String = {
    (model, sample) match {
      case (Function(domain, range), Sample(ds, rs)) => {
        val scalars = domain.getScalars ++ range.getScalars
        val datas = ds ++ rs
        (scalars zip datas).map {
          case (s: Scalar, d: Data) =>
            s.formatValue(d)
        }.mkString(",")
      }
    }
  }
}