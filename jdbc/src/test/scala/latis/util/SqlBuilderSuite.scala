package latis.util

import munit.FunSuite

import latis.dsl.*
import latis.metadata.Metadata
import latis.model.*
import latis.ops.*
import latis.time.*
import latis.util.Identifier.*

class SqlBuilderSuite extends FunSuite {

  private val table = "myTable"
  private val model = ModelParser.unsafeParse("(x, y) -> (a, b, c)")

  //---- SQL from Operations ----//

  test("make sql with no operations") {
    val ops = List()
    val sql = SqlBuilder.buildQuery(table, model, ops)
    assertEquals(sql, "SELECT x, y, a, b, c FROM myTable ORDER BY x, y ASC")
  }

  test("make sql with selections and projection without domain") {
    val ops = List(
      Selection.makeSelection("x > 1").toTry.get,
      Selection.makeSelection("a >= 2").toTry.get,
      Projection.fromExpression("b, c").toTry.get
    )
    val sql = SqlBuilder.buildQuery(table, model, ops)
    assertEquals(sql, "SELECT b, c FROM myTable WHERE x > 1 AND a >= 2 ORDER BY x, y ASC")
  }

  test("preserve model variable order") {
    val ops = List(
      Projection.fromExpression("b, a").toTry.get
    )
    val sql = SqlBuilder.buildQuery(table, model, ops)
    assert(sql.contains("a, b"))
  }

  test("rename twice") {
    val ops = List (
      Rename(id"b", id"B"),
      Rename(id"B", id"Z"),
    )
    val sql = SqlBuilder.buildQuery(table, model, ops)
    assert(sql.contains("b AS Z"))
  }

  test("select after rename uses original name") {
    val ops = List (
      Rename(id"x", id"z"),
      Selection.makeSelection("z = 1").toTry.get
    )
    val sql = SqlBuilder.buildQuery(table, model, ops)
    assertEquals(sql, "SELECT x AS z, y, a, b, c FROM myTable WHERE x = 1 ORDER BY x, y ASC")
  }

  test("project after rename") {
    val ops = List (
      Rename(id"a", id"z"),
      Projection(id"z")
    )
    val sql = SqlBuilder.buildQuery(table, model, ops)
    assert(sql.contains("SELECT a AS z"))
  }

  test("no order clause for index domain") {
    val scalar = ModelParser.unsafeParse("a")
    val model = Function.from(Index(id"_i"), scalar).getOrElse(fail("function not generated"))
    val sql = SqlBuilder.buildQuery(table, model)
    assert(!sql.contains("ORDER"))
  }

  test("make sql with count") {
    val ops = List(CountAggregation())
    val sql = SqlBuilder.buildQuery(table, model, ops)
    assertEquals(sql, "SELECT count(*) FROM myTable")
  }

  test("make sql with count after projection") {
    val ops = List(
      Projection(id"a"),
      CountAggregation()
    )
    val sql = SqlBuilder.buildQuery(table, model, ops)
    assertEquals(sql, "SELECT count(*) FROM myTable")
  }

  test("fail to make sql with count before projection") {
    val ops = List(
      CountAggregation(),
      Projection(id"a")
    )
    intercept[LatisException] {
      SqlBuilder.buildQuery(table, model, ops)
    }
  }

  //---- SQL with Time selections ----//

  private val modelWithNumericTime = Function.from(
    Time.fromMetadata(
      Metadata(
        "id" -> "t",
        "type" -> "int",
        "units" -> "days since 1970-01-01"
      )
    ).getOrElse(fail("failed to create time")),
    Scalar(id"a", IntValueType)
  ).getOrElse(fail("failed to create function"))

  private val modelWithTextTime = Function.from(
    Time.fromMetadata(
      Metadata(
        "id" -> "t",
        "type" -> "string",
        "units" -> "yyyy-MM-dd"
      )
    ).getOrElse(fail("failed to create time")),
    Scalar(id"a", IntValueType)
  ).getOrElse(fail("failed to create function"))

  test("numeric time selection with numeric time") {
    val ops = List(
      Selection.makeSelection("t > 1").toTry.get
    )
    val sql = SqlBuilder.buildQuery(table, modelWithNumericTime, ops)
    assert(sql.contains("t > 1"))
  }

  test("ISO time selection with numeric time") {
    val ops = List(
      Selection.makeSelection("t > 1970-01-02").toTry.get
    )
    val sql = SqlBuilder.buildQuery(table, modelWithNumericTime, ops)
    assert(sql.contains("t > 1"))
  }

  test("ISO time selection with formatted time") {
    val ops = List(
      Selection.makeSelection("t > 1970002").toTry.get
    )
    val sql = SqlBuilder.buildQuery(table, modelWithTextTime, ops)
    assert(sql.contains("t > '1970-01-02'"))
  }

  //---- Test Limit ----//

  test("no limit") {
    val ops = List()
    val sql = SqlBuilder.buildQuery(table, model, ops)
    assert(!sql.contains("FETCH") && !sql.contains("LIMIT"))
  }

  test("limit with take") {
    val ops = List(Take(10))
    val sql = SqlBuilder.buildQuery(table, model, ops)
    assert(sql.contains("10 ROWS"))
  }

  test("limit with head") {
    val ops = List(Head())
    val sql = SqlBuilder.buildQuery(table, model, ops)
    assert(sql.contains("1 ROWS"))
  }

  test("limit with take and head") {
    val ops = List(Take(10), Head())
    val sql = SqlBuilder.buildQuery(table, model, ops)
    assert(sql.contains("1 ROWS"))
  }

  test("limit with head and take") {
    val ops = List(Head(), Take(10))
    val sql = SqlBuilder.buildQuery(table, model, ops)
    assert(sql.contains("1 ROWS"))
  }

  test("limit with 0 then head") {
    val ops = List(Take(0), Head())
    val sql = SqlBuilder.buildQuery(table, model, ops)
    assert(sql.contains("0 ROWS")) //yes, oracle does allow this
  }

  test("limit with LIMIT") {
    val ops = List(Head())
    val sql = SqlBuilder.buildQuery(table, model, ops, vendor = Option("SQLite"))
    assert(sql.contains("LIMIT 1"))
  }
}
