package latis.ops

import munit.CatsEffectSuite

import latis.data.DomainData
import latis.data.RangeData
import latis.data.Sample
import latis.data.SampledFunction
import latis.dataset.Dataset
import latis.dsl.*

class DropSuite extends CatsEffectSuite {

  test("drop the first n samples of a simple dataset") {
    val ds: Dataset = DatasetGenerator("a -> b")
    val dsDrop      = ds.withOperation(Drop(1))
    val samples     = dsDrop.samples.compile.toList
    samples.assertEquals(
      List(
        Sample(DomainData(1), RangeData(1)),
        Sample(DomainData(2), RangeData(2))
      )
    )
  }

  test("drop the first n samples of a dataset with a nested function") {
    val ds      = DatasetGenerator("(a, b) -> c").curry(1).withOperation(Drop(1))
    val samples = ds.samples.compile.toList
    val sf = SampledFunction(
      Seq(
        Sample(DomainData(0), RangeData(3)),
        Sample(DomainData(1), RangeData(4)),
        Sample(DomainData(2), RangeData(5))
      )
    )
    samples.assertEquals(List(Sample(DomainData(1), Seq(sf))))
  }
}
