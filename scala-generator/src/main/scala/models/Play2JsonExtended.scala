package scala.models

import lib.Text._
import scala.generator.{ PrimitiveWrapper, ScalaDatatype, ScalaModel, ScalaPrimitive, ScalaService, ScalaUnion, ScalaUnionType }
import play.api.libs.json.JsString
import play.api.libs.json.JsObject
import scala.models.CaseClassUtil._

import PrimitiveWrapper.Wrapper

case class Play2JsonExtended(
    ssd: ScalaService
) {

  private sealed trait ReadWrite
  private case object Reads extends ReadWrite { override def toString = "Reads" }
  private case object Writes extends ReadWrite { override def toString = "Writes" }

  def generate(): String = {
    Seq(
      ssd.models.map(readersAndWriters(_)).mkString("\n\n"),
      PrimitiveWrapper(ssd).wrappers.map(w ⇒ readersAndWriters(w.model)).mkString("\n\n"),
      ssd.unions.map(readersAndWriters(_)).mkString("\n\n")
    ).filter(!_.trim.isEmpty).mkString("\n\n")
  }

  private def readersAndWriters(union: ScalaUnion): String = {
    readers(union) + "\n\n" + writers(union)
  }

  private[models] def readers(union: ScalaUnion): String = {
    Seq(
      s"${identifier(union.name, Reads)} = {",
      s"  (",
      union.types.map { scalaUnionType ⇒
        s"""(__ \\ "${scalaUnionType.originalName}").read(${reader(union, scalaUnionType)}).asInstanceOf[play.api.libs.json.Reads[${union.name}]]"""
      }.mkString("\norElse\n").indent(4),
      s"    orElse",
      s"    play.api.libs.json.Reads(jsValue => play.api.libs.json.JsSuccess(${union.undefinedType.name}(jsValue.toString))).asInstanceOf[play.api.libs.json.Reads[${union.name}]]",
      s"  )",
      s"}"
    ).mkString("\n")
  }

  private[models] def writers(union: ScalaUnion): String = {
    Seq(
      s"${identifier(union.name, Writes)} = new play.api.libs.json.Writes[${union.name}] {",
      s"  def writes(obj: ${union.name}) = obj match {",
      union.types.map { t ⇒
        val typeName = t.datatype match {
          case p @ (ScalaPrimitive.Model(_, _) | ScalaPrimitive.Enum(_, _) | ScalaPrimitive.Union(_, _)) ⇒ {
            p.name
          }
          case p: ScalaPrimitive          ⇒ PrimitiveWrapper.className(union, p)
          case c: ScalaDatatype.Container ⇒ sys.error(s"unsupported container type ${c} encountered in union ${union.name}")
        }
        s"""case x: ${typeName} => play.api.libs.json.Json.obj("${t.originalName}" -> ${writer("x", union, t)})"""
      }.mkString("\n").indent(4),
      s"""    case x: ${union.undefinedType.fullName} => sys.error(s"The type[${union.undefinedType.fullName}] should never be serialized")""",
      "  }",
      "}"
    ).mkString("\n")
  }

  private def reader(union: ScalaUnion, ut: ScalaUnionType): String = {
    ut.model match {
      case Some(model) ⇒ methodName(model.name, Reads)
      case None ⇒ {
        ut.enum match {
          case Some(enum) ⇒ methodName(enum.name, Reads)
          case None ⇒ ut.datatype match {
            // TODO enum representation should be refactored
            // so that the type can be read directly from
            // the enum (not ut). The enum type is always
            // a primitive, so this match is redundant,
            // but necessary due to the way the data is currently
            // structured
            case p: ScalaPrimitive ⇒ methodName(PrimitiveWrapper.className(union, p), Reads)
            case dt                ⇒ sys.error(s"unsupported datatype[${dt}] in union ${ut}")
          }
        }
      }
    }
  }

  private def writer(varName: String, union: ScalaUnion, ut: ScalaUnionType): String = {
    ut.model match {
      case Some(model) ⇒ methodName(model.name, Writes) + ".writes(x)"
      case None ⇒ {
        ut.enum match {
          case Some(enum) ⇒ methodName(enum.name, Writes) + ".writes(x)"
          case None ⇒ ut.datatype match {
            case p: ScalaPrimitive ⇒ methodName(PrimitiveWrapper.className(union, p), Writes) + ".writes(x)"
            case dt                ⇒ sys.error(s"unsupported datatype[${dt}] in union ${ut}")
          }
        }
      }
    }
  }

  private[models] def readersAndWriters(model: ScalaModel): String = {
    Seq(
      fieldsObject(model),
      readers(model),
      writers(model)
    ).mkString("\n\n")
  }

  private[models] def fieldsIdentifier(model: ScalaModel): String = s"${model.name}Fields"

  private[models] def fieldsObject(model: ScalaModel): String = {
    Seq(
      s"object ${fieldsIdentifier(model)} {",
      model.fields.map ( f ⇒
        s"val ${f.originalName} = ${"\""}${f.originalName}${"\""}".indent(2)
      ).mkString("\n"),
      "}"
    ).mkString("\n")
  }

  private[models] def readers(model: ScalaModel): String = {
    Seq(
      s"${identifier(model.name, Reads)} = new play.api.libs.json.Reads[${model.name}] {",
      fieldReaders(model).indent(2),
      s"}"
    ).mkString("\n")
  }

  private[models] def fieldReaders(model: ScalaModel): String = {
    val serializations = fieldReadersWriters(model, "read")

    val fields = model.fields match {
      case field :: Nil ⇒ {
        serializations.head + s""".map { x => new ${model.name}(${field.name} = x) }.reads(json)"""
      }
      case fields ⇒ {
        s"""(
${serializations.mkString(" and\n").indent(6)}
    )(${model.name}.apply _).reads(json)"""
      }
    }
    s"""def reads(json: play.api.libs.json.JsValue) = {
  try {
    ${fields}
  } catch {
    // Catch Validation Errors
    case ex: IllegalArgumentException => play.api.libs.json.JsError(s"$${ex.getMessage}")
  }
}"""
  }

  private[models] def writers(model: ScalaModel): String = {
    model.fields match {
      case field :: Nil ⇒ {
        Seq(
          s"${identifier(model.name, Writes)} = new play.api.libs.json.Writes[${model.name}] {",
          s"  def writes(x: ${model.name}) = play.api.libs.json.Json.obj(",
          s"""    "${field.originalName}" -> play.api.libs.json.Json.toJson(x.${field.name})""",
          "  )",
          "}"
        ).mkString("\n")
      }

      case fields ⇒ {
        Seq(
          s"${identifier(model.name, Writes)} = {",
          s"  (",
          fieldReadersWriters(model, "write").mkString(" and\n").indent(4),
          s"  )(unlift(${model.name}.unapply _))",
          s"}"
        ).mkString("\n")
      }
    }
  }

  private[models] def fieldReadersWriters(model: ScalaModel, readWrite: String): List[String] = {
    val fieldsIdent = fieldsIdentifier(model)
    model.fields.map { field ⇒
      getScalaProps(field.field) match {
        case Some(scalaFieldProps) ⇒
          val datatype = "_root_." + scalaFieldProps.`class`.getOrElse("???")
          if (field.required)
            s"""(__ \\ ${fieldsIdent}.${field.originalName}).${readWrite}[${datatype}]"""
          else
            s"""(__ \\ ${fieldsIdent}.${field.originalName}).${readWrite}Nullable[${datatype}]"""

        case None ⇒
          field.datatype match {
            case ScalaDatatype.Option(inner) ⇒ {
              s"""(__ \\ ${fieldsIdent}.${field.originalName}).${readWrite}Nullable[${inner.name}]"""
            }
            case datatype ⇒ {
              s"""(__ \\ ${fieldsIdent}.${field.originalName}).${readWrite}[${datatype.name}]"""
            }
          }
      }
    }
  }

  private[models] def readersAndWriters(wrapper: Wrapper): String =
    Seq(
      primitiveReaders(wrapper.model),
      primitiveWriters(wrapper.model)
    ).mkString("\n\n")

  private[models] def primitiveReaders(primitiveWrapper: ScalaModel): String = {
    val primitiveType = primitiveWrapper.fields match {
      case field :: Nili ⇒ field.datatype
      case _             ⇒ throw new IllegalArgumentException("Primitive wrapper should only contain a single field")
    }

    Seq (
      s"${identifier(model.name, Reads)} = new play.api.libs.json.Reads[${model.name}] = __.read[$primitiveType].map { x =>",
      s"  new ${model.name}(${field.name} = x)",
      "}"
    ).mkString("\n")
  }

  private[models] def primitiveWriters(primitiveWrapper: ScalaModel): String = ???


  private[models] def identifier(
    name: String,
    readWrite: ReadWrite
  ): String = {
    val method = methodName(name, readWrite)
    s"implicit def $method: play.api.libs.json.$readWrite[$name]"
  }

  private def methodName(
    name: String,
    readWrite: ReadWrite
  ): String = s"json$readWrite${ssd.name}$name"

}
