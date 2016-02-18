package scala.models

import com.bryzek.apidoc.generator.v0.models.{ File, InvocationForm }
import com.bryzek.apidoc.spec.v0.models.Attribute
import com.bryzek.apidoc.spec.v0.models.Service
import lib.Text._
import lib.generator.CodeGenerator
import generator.ServiceFileNames
import play.api.libs.json.JsArray
import play.api.libs.json.JsString
import play.api.libs.json.JsObject
import play.api.libs.json.__
import play.api.libs.json.Reads
import play.api.libs.functional.syntax._
import scala.models.KafkaUtil._
import scala.generator._
import movio.apidoc.generator.attributes.v0.models._
import movio.apidoc.generator.attributes.v0.models.json._

// Extended from from ScalaCaseClasses
object AdvancedCaseClasses extends AdvancedCaseClasses
trait AdvancedCaseClasses extends CodeGenerator {
  import KafkaUtil._
  import CaseClassUtil._

  private[this] val MaxNumberOfFields = 21

  override def invoke(form: InvocationForm): Either[Seq[String], Seq[File]] = invoke(form, addHeader = true)

  def modelsWithTooManyFieldsErrors(service: Service): Seq[String] = {
    service.models.filter(_.fields.size > MaxNumberOfFields) match {
      case Nil ⇒ Nil
      case invalidModels ⇒ {
        Seq(s"One or more models has more than $MaxNumberOfFields fields. This generator relies on scala case classes and play json serialization which do not yet support a larger number of fields. Models with too many fields: " + invalidModels.map(_.name).mkString(", "))
      }
    }
  }

  def invoke(
    form: InvocationForm,
    addHeader: Boolean = true
  ): Either[Seq[String], Seq[File]] = {
    modelsWithTooManyFieldsErrors(form.service) match {
      case Nil    ⇒ Right(generateCode(form, addHeader))
      case errors ⇒ Left(errors)
    }
  }

  def generateCode(
    form: InvocationForm,
    addHeader: Boolean = true
  ): Seq[File] = {
    val ssd = new ScalaService(form.service)

    val header = addHeader match {
      case false ⇒ ""
      case true  ⇒ ApidocComments(form.service.version, form.userAgent).toJavaString() + "\n"
    }

    val undefinedModels = UnionTypeUndefinedModel(ssd).models match {
      case Nil ⇒ ""
      case wrappers ⇒ {
        wrappers.map { w ⇒ generateCaseClass(ssd, w.model, Seq(w.union)) }.mkString("\n\n").indent(2) + "\n"
      }
    }

    val wrappers = PrimitiveWrapper(ssd).wrappers match {
      case Nil ⇒ ""
      case wrappers ⇒ {
        wrappers.map { w ⇒ generateCaseClass(ssd, w.model, Seq(w.union)) }.mkString("\n\n").indent(2) + "\n"
      }
    }

    val generatedClasses = Seq(undefinedModels, wrappers).filter(!_.isEmpty) match {
      case Nil  ⇒ ""
      case code ⇒ "\n" + code.mkString("\n\n")
    }

    val source = s"${header}package ${packageNamespace(ssd)} {\n\n  " +
      Seq(
        ssd.unions.map { generateUnionTraits(ssd.models, _) }.mkString("\n\n").indent(2),
        "",
        ssd.models.filter(modelFilter).map { m ⇒ generateCaseClass(ssd, m, ssd.unionsForModel(m)) }.mkString("\n\n").indent(2),
        generatedClasses,
        generatePlayEnums(ssd).indent(2)
      ).mkString("\n").trim +
        additionalClasses(ssd).indent(2) +
        s"\n\n}"

    Seq(ServiceFileNames.toFile(form.service.namespace, form.service.organization.key, form.service.application.key, form.service.version, filenamePostfix, source, Some("Scala")))
  }

  def packageNamespace(ssd: ScalaService): String = ssd.namespaces.models

  def modelFilter(model: ScalaModel): Boolean = true

  def filenamePostfix = "Models"

  private def generateUnionTraits(models: Seq[ScalaModel], union: ScalaUnion): String = {
    // TODO: handle primitive types

    union.description.map { desc ⇒ ScalaUtil.textToComment(desc) + "\n" }.getOrElse("") +
      s"sealed trait ${union.name}"
  }

  def generateCaseClass(ssd: ScalaService, model: ScalaModel, unions: Seq[ScalaUnion]): String = {
    model.description.map { desc ⇒ ScalaUtil.textToComment(desc) + "\n" }.getOrElse("") +
      s"case class ${model.name}(${getArguments(model, unions)})" + extendsClause(model, unions) + generateBody(ssd, model, unions)
  }

  def extendsClause(model: ScalaModel, unions: Seq[ScalaUnion]) =
    ScalaUtil.extendsClause(
      manualExtendsClasses ++
        extendsClasses(model) ++
        unions.map(_.name)
    ).map(s ⇒ s" $s").getOrElse("")

  private def generatePlayEnums(ssd: ScalaService): String = {
    ssd.enums.map { ScalaEnums(ssd, _).build }.mkString("\n\n")
  }

  def generateBody(ssd: ScalaService, model: ScalaModel, unions: Seq[ScalaUnion]): String =
    " {\n\n" +
      generateValidation(ssd, model, unions) +
      generateKafkaBody(ssd, model, unions).indent(2) +
      "\n}"

  // Add validation
  def generateValidation(ssd: ScalaService, model: ScalaModel, unions: Seq[ScalaUnion]): String = {
    val fields = model.fields.map(generateFieldValidation(_)).flatten.mkString("\n")
    s"""  import Validation._
${fields.indent(2)}
"""
  }

  def generateFieldValidation(field: ScalaField): Seq[String] = {
    def toOption(test: Boolean, fn: ScalaField ⇒ String) = if (test) Option(fn(field)) else None
    val fieldValidation = getFieldValidation(field)
    val result = Seq.empty :+
      toOption(field.field.maximum.isDefined, {
                 val dataType = field.`type`
                 dataType match {
                   case _: lib.Datatype.Container ⇒
                     field ⇒ s"""validateMaxSize("${field.name}", ${field.name}, ${field.field.maximum.get})"""
                   case _ ⇒
                     field ⇒ s"""validateMaxLength("${field.name}", ${field.name}, ${field.field.maximum.get})"""
                 }
               }) :+
      // toOption(field.field.minimum.isDefined, field ⇒ s"""validateMinLength("${field.name}", ${field.name}, ${field.field.minimum.get})""") :+
      toOption(field.field.minimum.isDefined, {
                 val dataType = field.`type`
                 dataType match {
                   case _: lib.Datatype.Container ⇒
                     field ⇒ s"""validateMinSize("${field.name}", ${field.name}, ${field.field.minimum.get})"""
                   case _ ⇒
                     field ⇒ s"""validateMinLength("${field.name}", ${field.name}, ${field.field.minimum.get})"""
                 }
               }) :+
      fieldValidation.flatMap(_.regex.flatMap(regex ⇒ Some(s"""validateRegex("${field.name}", ${field.name}, "${regex}")"""))) :+
      fieldValidation.flatMap(_.maximum.flatMap(max ⇒ {
        val dataType = field.`type`
        dataType match {
          case d: lib.Datatype.Container ⇒
            d.inner match {
              case s: lib.Datatype.Primitive ⇒
                s.name match {
                  case "string" => Some(s"""validateMaxLengthOfAll("${field.name}", ${field.name}, ${max})""")
                  case "integer" => Some(s"""validateMaxLengthOfAll("${field.name}", ${field.name}, ${max})""")
                  case "long" => Some(s"""validateMaxLengthOfAll("${field.name}", ${field.name}, ${max})""")
                  case _ => None
                }
              case _ ⇒ None
            }
          case _ ⇒ None
        }
      })) :+
      fieldValidation.flatMap(_.minimum.flatMap(max ⇒ {
        val dataType = field.`type`
        dataType match {
          case d: lib.Datatype.Container ⇒
            d.inner match {
              case s: lib.Datatype.Primitive ⇒
                s.name match {
                  case "string" => Some(s"""validateMinLengthOfAll("${field.name}", ${field.name}, ${max})""")
                  case "integer" => Some(s"""validateMinLengthOfAll("${field.name}", ${field.name}, ${max})""")
                  case "long" => Some(s"""validateMinLengthOfAll("${field.name}", ${field.name}, ${max})""")
                  case _ => None
                }
              case _ ⇒ None
            }
          case _ ⇒ None
        }
      }))

    result.flatten
  }

  def generateKafkaBody(ssd: ScalaService, model: ScalaModel, unions: Seq[ScalaUnion]): String = {
    getKafkaProps(model) match {
      case Some(kafkaProps) ⇒
        s"""def generateKey(tenant: String) = ${kafkaProps.messageGenerateKey}"""
      case None ⇒ ""
    }
  }

  def extendsClasses(model: ScalaModel): Seq[String] = {
    val defined = getScalaProps(model) match {
      case Some(p) ⇒ p.`extends`.getOrElse(Seq.empty)
      case None    ⇒ Seq.empty
    }
    if (isKafkaClass(model)) {
      defined :+ "KafkaMessage"
    } else {
      defined
    }
  }

  def manualExtendsClasses = Seq.empty[String]

  // Override field types
  def getArguments(model: ScalaModel, unions: Seq[ScalaUnion]): String = {
    model.fields.map { field ⇒
      val name = ScalaUtil.quoteNameIfKeyword(snakeToCamelCase(field.name))
      val definition = dataType(field)
      val rootedType = field.datatype.name
      val default = getDefault(field)
      s"$name: ${definition}${default}"
    }.mkString("\n", ",\n", "\n").indent(2) + "\n"
  }

  def getDefault(field: ScalaField): String = {
    getScalaProps(field) match {
      case Some(scalaFieldProps) ⇒
        // Custom Type
        scalaFieldProps.default match {
          case Some(v) ⇒ " = " + v
          case None ⇒
            if (field.required)
              ""
            else
              " = None"
        }
      case None ⇒
        // Standard Field
        if (field.required)
          field.default match {
            case Some(d) ⇒ " = " + d
            case None    ⇒ ""
          }
        else
          " = None"
    }
  }

  def dataType(field: ScalaField) = {
    getScalaProps(field) match {
      case Some(scalaFieldProps) ⇒
        val rootedType = "_root_." + scalaFieldProps.`class`.get
        if (field.required)
          rootedType
        else
          s"_root_.scala.Option[${rootedType}]"
      case None ⇒ field.datatype.name
    }
  }

  def additionalClasses(ssd: ScalaService) = validation + kafkaMessageTrait(ssd)

  def validation = s"""

object Validation {

  def validateMaxLength(name: String, value: _root_.scala.Option[String], length: Int): Unit = {
    value foreach { value ⇒
      validateMaxLength(name, value, length)
    }
  }

  def validateMax[T](name: String, value: T, max: T)(implicit n:Numeric[T]): Unit = {
    require(n.lteq(value , max), s"$$name must be less than or equal to $$max")
  }

  def validateMin[T](name: String, value: T, min: T)(implicit n:Numeric[T]): Unit = {
    require(n.gteq(value , min), s"$$name must be greater than or equal to $$min")
  }

  def validateMaxLength(name: String, value: String, length: Int): Unit = {
    require(value.length <= length, s"$$name must be $$length characters or less")
  }

  def validateMaxLengthOfAll(name: String, values: _root_.scala.Option[Seq[String]], length: Int): Unit = {
    values foreach { values ⇒
      validateMaxLengthOfAll(name, values, length)
    }
  }

  def validateMaxLengthOfAll(name: String, values: Seq[String], length: Int): Unit = {
    values foreach { value ⇒
      validateMaxLength(name, value, length)
    }
  }

  def validateMaxOfAll[T](name: String, values: Seq[T], max: T)(implicit n: Numeric[T]): Unit = {
    values foreach { value ⇒
      validateMax(name, value, max)
    }
  }

  def validateMinOfAll[T](name: String, values: Seq[T], min: T)(implicit n: Numeric[T]): Unit = {
    values foreach { value ⇒
      validateMin(name, value, min)
    }
  }

  def validateMaxOfAll[T](name: String, values: _root_.scala.Option[Seq[T]], max: T)(implicit n: Numeric[T]): Unit = {
    values foreach { values ⇒
      validateMaxOfAll(name, values, max)
    }
  }

  def validateMinOfAll[T](name: String, values: _root_.scala.Option[Seq[T]], min: T)(implicit n: Numeric[T]): Unit = {
    values foreach { values ⇒
      validateMinOfAll(name, values, min)
    }
  }

  def validateRegex(name: String, value: String, regex: String): Unit = {
    require(regex.r.findFirstIn(value).isDefined, s"$$name did not match regex: $$regex")
  }

}
"""

  def kafkaMessageTrait(ssd: ScalaService): String = {
    getKafkaModels(ssd).nonEmpty match {
      case true ⇒ """

trait KafkaMessage {

  /**
    A scala statement/code that returns the kafka key to use
    Usually something like `data.exhibitorId`
    */
  def generateKey(tenant: String): String
}
"""
      case false ⇒ ""
    }
  }

}
