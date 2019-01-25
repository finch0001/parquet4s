package com.github.mjakubowski84.parquet4s

import java.nio.ByteBuffer
import java.time.Period

import org.scalatest.{FlatSpec, Matchers}
import ValueImplicits._
import ParquetRecordDecoder.{DecodingException, decode}
import TestCases._


class ParquetRecordDecoderSpec extends FlatSpec with Matchers {

  "HNil decoder" should "be used to decode empty record" in {
    decode[Empty](RowParquetRecord.empty) should be(Empty())
  }

  "Value decoder" should "decode record containing primitive values" in {
    val data = Primitives(
      boolean = true,
      int = 1,
      long = 1234567890l,
      float = 1.1f,
      double = 1.00000000000001d,
      string = "text"
    )
    val record = RowParquetRecord(
      "boolean" -> true,
      "int" -> 1,
      "long" -> 1234567890l,
      "float" -> 1.1f,
      "double" -> 1.00000000000001d,
      "string" -> "text"
    )
    decode[Primitives](record) should be(data)
  }

  ignore should "decode record containing time primitive values" in {
    val data = TimePrimitives(
      timestamp = java.sql.Timestamp.valueOf(java.time.LocalDateTime.of(2019, 1, 1, 12, 0, 1)),
      date = java.sql.Date.valueOf(java.time.LocalDate.of(2019, 1, 1))
    )
    val record = RowParquetRecord(
      "timestamp" -> BinaryValue(ByteBuffer.allocate(12).array()), // TODO fix when we will have encoder and we know better how to encode
      "date" -> Period.between(
        java.time.LocalDate.of(1970, 1, 1),
        java.time.LocalDate.of(2019, 1, 1)
      ).getDays
    )
    decode[TimePrimitives](record) should be(data)
  }

  it should "throw exception if record is missing data for non-optional field" in {
    case class ContainsRequired(requiredField: Int)

    a[ParquetRecordDecoder.DecodingException] should be thrownBy decode[ContainsRequired](RowParquetRecord.empty)
  }

  it should "decode record with optional field that has no value" in {
    decode[ContainsOption](RowParquetRecord.empty) should be(ContainsOption(None))
  }

  it should "throw exception if type of input filed does not match expected type" in {
    case class ContainsInt(int: Int)

    val record = RowParquetRecord("int" -> "I am string but I should be int")

    a[ParquetRecordDecoder.DecodingException] should be thrownBy decode[ContainsInt](record)
  }

  it should "decode record containing collection of primitives" in {
    decode[Collections](RowParquetRecord(
      "list" -> ListParquetRecord.empty,
      "seq" -> ListParquetRecord.empty,
      "vector" -> ListParquetRecord.empty,
      "set" -> ListParquetRecord.empty,
      "array" -> ListParquetRecord.empty
    )) should be(Collections(
      list = List.empty,
      seq = Seq.empty,
      vector = Vector.empty,
      set = Set.empty,
      array = Array.empty
    ))
    val listRecordWithValues = ListParquetRecord(1, 2, 3)
    decode[Collections](RowParquetRecord(
      "list" -> listRecordWithValues,
      "seq" -> listRecordWithValues,
      "vector" -> listRecordWithValues,
      "set" -> listRecordWithValues,
      "array" -> listRecordWithValues
    )) should be(Collections(
      list = List(1, 2, 3),
      seq = Seq(1, 2, 3),
      vector = Vector(1, 2, 3),
      set = Set(1, 2, 3),
      array = Array(1, 2, 3)
    ))
  }

  it should "decode record containing collection of optional primitives" in {
    decode[ContainsCollectionOfOptionalPrimitives](RowParquetRecord(
      "list" -> ListParquetRecord.empty
    )) should be(
      ContainsCollectionOfOptionalPrimitives(List.empty)
    )
    decode[ContainsCollectionOfOptionalPrimitives](RowParquetRecord(
      "list" -> ListParquetRecord(NullValue, 2, NullValue))
    ) should be(
      ContainsCollectionOfOptionalPrimitives(List(None, Some(2), None))
    )
  }

  it should "decode record containing map of primitives" in {
    decode[ContainsMapOfPrimitives](
      RowParquetRecord("map" -> MapParquetRecord("key" -> 1))
    ) should be(ContainsMapOfPrimitives(Map("key" -> 1)))
  }

  it should "decode record containing map of optional primitives" in {
    decode[ContainsMapOfOptionalPrimitives](RowParquetRecord(
      "map" -> MapParquetRecord("1" -> NullValue, "2" -> IntValue(2))
    )) should be(ContainsMapOfOptionalPrimitives(
      Map("1" -> None, "2" -> Some(2))
    ))
  }

  it should "decode record containing map of collections of primitives" in {
    decode[ContainsMapOfCollectionsOfPrimitives](RowParquetRecord(
      "map" -> MapParquetRecord("1" -> ListParquetRecord.empty, "2" -> ListParquetRecord(1, 2, 3))
    )) should be(ContainsMapOfCollectionsOfPrimitives(
      Map("1" -> List.empty, "2" -> List(1, 2, 3))
    ))
  }

  "Product decoder" should "decode record containing nested records" in {
    val data = ContainsNestedClass(Nested(1))
    val record = RowParquetRecord(
      "nested" -> RowParquetRecord("int" -> 1)
    )
    decode[ContainsNestedClass](record) should be(data)
  }

  it should "throw exception if record is missing data for field" in {
    a[ParquetRecordDecoder.DecodingException] should be thrownBy decode[ContainsNestedClass](RowParquetRecord.empty)
  }

  it should "throw exception if input record does not match expected type" in {
    val record = RowParquetRecord("nested" -> ListParquetRecord(1))
    a[ParquetRecordDecoder.DecodingException] should be thrownBy decode[ContainsNestedClass](record)
  }

  "Optional product decoder" should "decode record containing optional nested record" in {
    val dataWithSome = ContainsOptionalNestedClass(Some(Nested(1)))
    val dataWithNone = ContainsOptionalNestedClass(None)

    decode[ContainsOptionalNestedClass](
      RowParquetRecord("nestedOptional" -> NullValue)
    ) should be(dataWithNone)

    decode[ContainsOptionalNestedClass](
      RowParquetRecord("nestedOptional" -> RowParquetRecord("int" -> 1))
    ) should be(dataWithSome)
  }

  it should "throw exception if input does not match expected type" in {
    val invalidRecordWithMap = RowParquetRecord("nestedOptional" -> MapParquetRecord("a" -> 1))
    val invalidRecordWithList = RowParquetRecord("nestedOptional" -> ListParquetRecord(1))

    a[DecodingException] should be thrownBy decode[ContainsOptionalNestedClass](invalidRecordWithMap)
    a[DecodingException] should be thrownBy decode[ContainsOptionalNestedClass](invalidRecordWithList)
  }

  "Collection of products decoder" should "decode record containing sequence of nested records" in {
    decode[CollectionsOfNestedClass](RowParquetRecord(
      "list" -> ListParquetRecord.empty,
      "seq" -> ListParquetRecord.empty,
      "vector" -> ListParquetRecord.empty,
      "set" -> ListParquetRecord.empty,
      "array" -> ListParquetRecord.empty
    )) should be(CollectionsOfNestedClass(
      list = List.empty,
      seq = Seq.empty,
      vector = Vector.empty,
      set = Set.empty,
      array = Array.empty
    ))

    val listOfNestedRecords = ListParquetRecord(
      RowParquetRecord("int" -> 1), RowParquetRecord("int" -> 2), RowParquetRecord("int" -> 3)
    )
    val expectedList = List(Nested(1), Nested(2), Nested(3))
    decode[CollectionsOfNestedClass](RowParquetRecord(
      "list" -> listOfNestedRecords,
      "seq" -> listOfNestedRecords,
      "vector" -> listOfNestedRecords,
      "set" -> listOfNestedRecords,
      "array" -> listOfNestedRecords
    )) should be(CollectionsOfNestedClass(
      list = expectedList,
      seq = expectedList.to,
      vector = expectedList.to,
      set = expectedList.to,
      array = expectedList.to
    ))
  }

  it should "throw exception if input does not match expected type" in {
    val record = RowParquetRecord("set" -> MapParquetRecord("a" -> 1))
    a[DecodingException] should be thrownBy decode[CollectionsOfNestedClass](record)
  }

  "Map of products decoder" should "decode record containing map of records" in {
    val dataWithEmptyMap = ContainsMapOfNestedClass(Map.empty)
    val dataWithMap = ContainsMapOfNestedClass(Map("1" -> Nested(1), "2" -> Nested(2)))

    decode[ContainsMapOfNestedClass](
      RowParquetRecord("nested" -> MapParquetRecord.empty)
    ) should be(dataWithEmptyMap)

    val record = RowParquetRecord("nested" -> MapParquetRecord(
      "1" -> RowParquetRecord("int" -> 1),
      "2" -> RowParquetRecord("int" -> 2)
    ))
    decode[ContainsMapOfNestedClass](record) should be(dataWithMap)
  }

  it should "throw exception when failed to decode a key of a map" in {
    val record = RowParquetRecord("nested" -> MapParquetRecord(123 -> RowParquetRecord("int" -> 1)))
    a[DecodingException] should be thrownBy decode[ContainsMapOfNestedClass](record)
  }

  it should "throw exception when encountered implementation of ParquetRecord unsuitable for map" in {
    val record = RowParquetRecord("nested" -> ListParquetRecord(RowParquetRecord("int" -> 1)))
    a[DecodingException] should be thrownBy decode[ContainsMapOfNestedClass](record)
  }

  it should "decode record containing map of optional records" in {
    val dataWithEmptyMap = ContainsMapOfOptionalNestedClass(Map.empty)
    val dataWithMap = ContainsMapOfOptionalNestedClass(Map(
      "none" -> None,
      "some" -> Some(Nested(2))
    ))

    decode[ContainsMapOfOptionalNestedClass](
      RowParquetRecord("nested" -> MapParquetRecord.empty)
    ) should be(dataWithEmptyMap)

    val record = RowParquetRecord("nested" -> MapParquetRecord(
      "none" -> NullValue,
      "some" -> RowParquetRecord("int" -> 2)
    ))
    decode[ContainsMapOfOptionalNestedClass](record) should be(dataWithMap)
  }

  it should "decode record containing map of collection of records" in {
    val dataWithEmptyMap = ContainsMapOfCollectionsOfNestedClass(Map.empty)
    val dataWithMap = ContainsMapOfCollectionsOfNestedClass(Map(
      "empty" -> List.empty,
      "nonEmpty" -> List(Nested(1), Nested(2), Nested(3))
    ))

    decode[ContainsMapOfCollectionsOfNestedClass](
      RowParquetRecord("nested" -> MapParquetRecord.empty)
    ) should be(dataWithEmptyMap)

    val record = RowParquetRecord("nested" -> MapParquetRecord(
      "empty" -> ListParquetRecord.empty,
      "nonEmpty" -> ListParquetRecord(RowParquetRecord("int" -> 1), RowParquetRecord("int" -> 2), RowParquetRecord("int" -> 3))
    ))
    decode[ContainsMapOfCollectionsOfNestedClass](record) should be(dataWithMap)
  }

}