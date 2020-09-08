package latis.dataset

import java.net.URI

import cats.effect.IO
import fs2.Stream

import latis.data.Sample
import latis.input.Adapter
import latis.metadata.Metadata
import latis.model.DataType
import latis.ops.UnaryOperation

/**
 * Defines a Dataset with data provided via an Adapter.
 */
class AdaptedDataset(
  _metadata: Metadata,
  _model: DataType,
  adapter: Adapter,
  uri: URI,
  operations: Seq[UnaryOperation] = Seq.empty
) extends AbstractDataset(
  _metadata,
  _model,
  operations
) {

  /**
   * Returns a copy of this Dataset with the given Operation
   * appended to its sequence of operations.
   */
  def withOperation(operation: UnaryOperation): Dataset =
    new AdaptedDataset(
      _metadata,
      _model,
      adapter: Adapter,
      uri: URI,
      operations :+ operation
    )

  /**
   * Invokes the Adapter to return data as a SampledFunction.
   * Note that this could still be lazy, wrapping a unreleased
   * resource.
   * Contrast to "unsafeForce".
   */
  def tap(): TappedDataset = {
    // Separate leading operation that the adapter can handle
    // from the rest. Note that we must preserve the order for safety.
    //TODO: "compile" the Operations to optimize the order of application
    //val adapterOps = operations.takeWhile(adapter.canHandleOperation(_))
    //val otherOps   = operations.drop(adapterOps.length)

    // Hack to allow smart adapter to handle any operation without concern
    // about order. Otherwise, simple processing instructions could prevent
    // other optimizations.
    val (adapterOps, otherOps) = operations.partition(adapter.canHandleOperation(_))

    //TODO: add prov for adapter handled ops

    // Apply the adapter handled operations to the model
    // since the Adapter can't.
    val model2 = adapterOps.foldLeft(_model)((mod, op) => op.applyToModel(mod))

    // Delegate to the Adapter to get the (potentially lazy) data.
    val data = adapter.getData(uri, adapterOps)

    // Construct the new Dataset
    new TappedDataset(_metadata, model2, data, otherOps)
  }

  /**
   * Returns a Stream of Samples from this Dataset.
   */
  def samples: Stream[IO, Sample] = tap().samples

  /**
   * Transforms this TappedDataset into a MemoizedDataset.
   * Operations will be applied and the resulting samples
   * will be read into a MemoizedFunction.
   */
  def unsafeForce(): MemoizedDataset = tap().unsafeForce()
}
