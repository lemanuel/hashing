package org.lal.hashing.system

import org.scalatest.{FunSuite, Matchers}

class WriterTest extends FunSuite with Matchers {

  def fixture =
    new {
      val buffer = Writer.createEmptyBuffer ++ Seq(10 -> Seq("10 elements"),
        30 -> Seq(""), 20 -> Seq(""),
        13 -> Seq(""), 12 -> Seq(""),
        3 -> Seq(""), 5 -> Seq(""),
        2 -> Seq(""), 32 -> Seq(""),
        6 -> Seq(""), 33 -> Seq(""))
      val emptyBuffer = Writer.createEmptyBuffer
    }

  test("test for take -- all needed values") {
    val f = fixture
    import f._
    Writer.take(buffer, 2) should have length 2
  }

  test("test for take -- no value") {
    val f = fixture
    import f._

    Writer.take(buffer, 1) should have length 0
  }

  test("test for take -- values after no value") {
    val f = fixture
    import f._

    Writer.take(buffer, 1) should have length 0
    Writer.take(buffer, 2) should have length 2
  }

  test("test for take -- empty buffer") {
    val f = fixture
    import f._

    Writer.take(emptyBuffer, 1) should have length 0
  }

}
