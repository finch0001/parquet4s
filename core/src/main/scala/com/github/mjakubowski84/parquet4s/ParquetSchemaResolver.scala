package com.github.mjakubowski84.parquet4s

import org.apache.parquet.schema.Type.Repetition
import org.apache.parquet.schema._
import shapeless._
import shapeless.labelled._

import scala.language.higherKinds


trait ParquetSchemaResolver[T] {

  def resolveSchema: List[Type]

}

object ParquetSchemaResolver
  extends SchemaDefs {

  def resolveSchema[T](implicit g: ParquetSchemaResolver[T]): MessageType = Message(g.resolveSchema:_*)

  implicit val hnil: ParquetSchemaResolver[HNil] = new ParquetSchemaResolver[HNil] {
    def resolveSchema: List[Type] = List.empty
  }

  implicit def hcons[K <: Symbol, V, T <: HList](implicit
                                                 witness: Witness.Aux[K],
                                                 schemaDef: TypedSchemaDef[V],
                                                 rest: ParquetSchemaResolver[T]
                                                ): ParquetSchemaResolver[FieldType[K, V] :: T] =
    new ParquetSchemaResolver[FieldType[K, V] :: T] {
      def resolveSchema: List[Type] = schemaDef(witness.value.name) +: rest.resolveSchema
    }

  implicit def generic[T, G](implicit
                             lg: LabelledGeneric.Aux[T, G],
                             rest: ParquetSchemaResolver[G]
                              ): ParquetSchemaResolver[T] = new ParquetSchemaResolver[T] {
    def resolveSchema: List[Type] = rest.resolveSchema
  }
}

object Message {

  val name = "parquet4s-schema"

  def apply(fields: Type*): MessageType = Types.buildMessage().addFields(fields:_*).named(name)

}

trait SchemaDef {

  def apply(name: String): Type

}

case class PrimitiveSchemaDef(
                             primitiveType: PrimitiveType.PrimitiveTypeName,
                             required: Boolean = true,
                             originalType: Option[OriginalType] = None
                             ) extends SchemaDef {

  override def apply(name: String): Type = {
    val builder = Types.primitive(
      primitiveType,
      if (required) Repetition.REQUIRED else Repetition.OPTIONAL
    )
    originalType.foldLeft(builder)(_.as(_)).named(name)
  }

}

case class GroupSchemaDef(fields: Type*) extends SchemaDef {
  override def apply(name: String): Type = Types.optionalGroup().addFields(fields:_*).named(name)
}

object ListGroupSchemaDef {

  val ElementName = "element"

  def apply(elementSchemaDef: SchemaDef): ListGroupSchemaDef = ListGroupSchemaDef(elementSchemaDef(ElementName))

}

case class ListGroupSchemaDef(element: Type) extends SchemaDef {
  override def apply(name: String): Type = Types.optionalList().element(element).named(name)
}

object MapSchemaDef {

  val KeyName = "key"
  val ValueName = "value"

  def apply(keySchemaDef: SchemaDef, valueSchemaDef: SchemaDef): MapSchemaDef = new MapSchemaDef(
    keySchemaDef(KeyName), valueSchemaDef(ValueName)
  )

}

case class MapSchemaDef(key: Type, value: Type) extends SchemaDef {
  override def apply(name: String): Type = Types.optionalMap().key(key).value(value).named(name)
}

// TODO compare schemas here with those from Spark (for example are really primitives required by default, but strings not? maybe it is about empty string? test it)
// TODO check how Spark saves null and empty primitive values
trait SchemaDefs {

  trait Tag[V]
  type TypedSchemaDef[V] = SchemaDef with Tag[V]

  def typedSchemaDef[V](schemaDef: SchemaDef): TypedSchemaDef[V] = schemaDef.asInstanceOf[TypedSchemaDef[V]]

  implicit val stringSchema: TypedSchemaDef[String] =
    typedSchemaDef[String](
      PrimitiveSchemaDef(PrimitiveType.PrimitiveTypeName.BINARY, required = false, originalType = Some(OriginalType.UTF8))
    )

  implicit val intSchema: TypedSchemaDef[Int] =
    typedSchemaDef[Int](
      PrimitiveSchemaDef(PrimitiveType.PrimitiveTypeName.INT32, originalType = Some(OriginalType.INT_32))
    )

  implicit val longSchema: TypedSchemaDef[Long] =
    typedSchemaDef[Long](
      PrimitiveSchemaDef(PrimitiveType.PrimitiveTypeName.INT64, originalType = Some(OriginalType.INT_64))
    )

  implicit val floatSchema: TypedSchemaDef[Float] =
    typedSchemaDef[Float](
      PrimitiveSchemaDef(PrimitiveType.PrimitiveTypeName.FLOAT)
    )

  implicit val doubleSchema: TypedSchemaDef[Double] =
    typedSchemaDef[Double](
      PrimitiveSchemaDef(PrimitiveType.PrimitiveTypeName.DOUBLE)
    )

  implicit val booleanSchema: TypedSchemaDef[Boolean] =
    typedSchemaDef[Boolean](
      PrimitiveSchemaDef(PrimitiveType.PrimitiveTypeName.BOOLEAN)
    )

  implicit def productSchema[T](implicit parquetSchemaResolver: ParquetSchemaResolver[T]): TypedSchemaDef[T] =
    typedSchemaDef[T](
      GroupSchemaDef(parquetSchemaResolver.resolveSchema:_*)
    )

  implicit def optionSchema[T](implicit tSchemaDef: TypedSchemaDef[T]): TypedSchemaDef[Option[T]] =
    typedSchemaDef[Option[T]](
      tSchemaDef match {
        case primitiveSchemaDef: PrimitiveSchemaDef =>
          primitiveSchemaDef.copy(required = false)
        case other =>
          other // other types are optional by default, aren't they?
      }
    )

  implicit def collectionSchema[E, Col[_]](implicit elementSchema: TypedSchemaDef[E]): TypedSchemaDef[Col[E]] =
    typedSchemaDef[Col[E]](
      ListGroupSchemaDef(elementSchema)
    )

  implicit def mapSchema[MapKey, MapValue](implicit
                                           keySchema: TypedSchemaDef[MapKey],
                                           valueSchema: TypedSchemaDef[MapValue]
                                          ): TypedSchemaDef[Map[MapKey, MapValue]] =
    typedSchemaDef[Map[MapKey, MapValue]](
      MapSchemaDef(keySchema, valueSchema)
    )

}