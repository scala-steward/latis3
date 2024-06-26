package latis.ops

import cats.effect.IO
import fs2.Pipe
import fs2.Stream

import latis.data.*
import latis.model.*
import latis.util.LatisException

/**
 * An Aggregation Operation combines all the Samples of a Dataset
 * into a Dataset with a single zero-arity Sample.
 * The aggregate function can be used to aggregate the results
 * of a GroupOperation.
 */
trait Aggregation extends StreamOperation { self =>
  //TODO: Note that Aggregation can be used on a full dataset so we should be lazy
  //  Stream[Sample] => IO[Data] ?
  def aggregateFunction(model: DataType): Iterable[Sample] => Data

  override def pipe(model: DataType): Pipe[IO, Sample, Sample] =
    (samples: Stream[IO, Sample]) => samples
      .fold(List[Sample]())(_ :+ _) //TODO: avoid making in-memory List
      .map(aggregateFunction(model))
      .map(d => Sample(DomainData(), RangeData(d)))

  def compose(mapOp: MapOperation): Aggregation = new Aggregation {

    def applyToModel(model: DataType): Either[LatisException, DataType] =
      mapOp.applyToModel(model).flatMap(self.applyToModel)

    def aggregateFunction(model: DataType): Iterable[Sample] => Data = {
      val tmpModel = mapOp.applyToModel(model)
      (samples: Iterable[Sample]) => {
        val tmpSamples = samples.map(mapOp.mapFunction(model))
        self.aggregateFunction(tmpModel.fold(throw _, identity))(tmpSamples)
      }
    }
  }

}
