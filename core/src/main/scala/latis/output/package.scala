package latis

import io.circe.Json
import io.circe.{Encoder as CEncoder}
import io.circe.syntax.*
import latis.data.*
import latis.data.Data.*

package object output {

  /** Instance of io.circe.Encoder for Sample. */
  implicit val encodeSample: CEncoder[Sample] = new CEncoder[Sample] {
    final def apply(s: Sample): Json = (s.domain ++ s.range).asJson
  }

  /** Instance of io.circe.Encoder for Data. */
  implicit val encodeData: CEncoder[Data] = new CEncoder[Data] {
    final def apply(value: Data): Json = value match {
      case NullData        => Json.Null
      case x: ShortValue   => x.value.asJson
      case x: IntValue     => x.value.asJson
      case x: LongValue    => x.value.asJson
      case x: FloatValue   => x.value.asJson
      case x: DoubleValue  => x.value.asJson
      case x: StringValue  => x.value.asJson
      case x: BooleanValue => x.value.asJson
      case x               => x.toString.asJson
    }
  }

}
