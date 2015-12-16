package scala.generator

import scala.models.ApidocComments
import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import com.bryzek.apidoc.spec.v0.models.Service
import lib.Text._
import lib.generator.CodeGenerator
import generator.ServiceFileNames
import play.api.libs.json.JsArray
import play.api.libs.json.JsString
import play.api.libs.json.JsObject

// Extended from from ScalaCaseClasses
object ExtendedCaseClasses extends ExtendedCaseClasses 
trait ExtendedCaseClasses extends CodeGenerator {

  private[this] val MaxNumberOfFields = 21
  val ScalaExtensionKey = "scala_extends"
  val ScalaTypeKey = "scala_type"

  override def invoke(form: InvocationForm): Either[Seq[String], Seq[File]] = invoke(form, addHeader = true)

  def modelsWithTooManyFieldsErrors(service: Service): Seq[String] = {
    service.models.filter(_.fields.size > MaxNumberOfFields) match {
      case Nil => Nil
      case invalidModels => {
        Seq(s"One or more models has more than $MaxNumberOfFields fields. This generator relies on scala case classes and play json serialization which do not yet support a larger number of fields. Models with too many fields: " + invalidModels.map(_.name).mkString(", "))
      }
    }
  }

  def invoke(
    form: InvocationForm,
    addHeader: Boolean = true
  ): Either[Seq[String], Seq[File]] = {
    modelsWithTooManyFieldsErrors(form.service) match {
      case Nil => Right(generateCode(form, addHeader))
      case errors => Left(errors)
    }
  }

  def generateCode(
    form: InvocationForm,
    addHeader: Boolean = true
  ): Seq[File] = {
    val ssd = new ScalaService(form.service)

    val header = addHeader match {
      case false => ""
      case true => ApidocComments(form.service.version, form.userAgent).toJavaString() + "\n"
    }

    val undefinedModels = UnionTypeUndefinedModel(ssd).models match {
      case Nil => ""
      case wrappers => {
        wrappers.map { w => generateCaseClass(ssd, w.model, Seq(w.union)) }.mkString("\n\n").indent(2) + "\n"
      }
    }

    val wrappers = PrimitiveWrapper(ssd).wrappers match {
      case Nil => ""
      case wrappers => {
        wrappers.map { w => generateCaseClass(ssd, w.model, Seq(w.union)) }.mkString("\n\n").indent(2) + "\n"
      }
    }

    val generatedClasses = Seq(undefinedModels, wrappers).filter(!_.isEmpty) match {
      case Nil => ""
      case code => "\n" + code.mkString("\n\n")
    }

    val validationSource = s"""

object Validation {

  def validateLength(name: String, value: Option[String], length: Int): Unit = {
    value foreach { value ⇒
      validateLength(name, value, length)
    }
  }

  def validateLength(name: String, value: String, length: Int): Unit = {
    require(value.length <= length, s"$$name must be $$length characters or less")
  }

  def validateLengthOfAll(name: String, values: Option[Seq[String]], length: Int): Unit = {
    values foreach { values ⇒
      validateLengthOfAll(name, values, length)
    }
  }

  def validateLengthOfAll(name: String, values: Seq[String], length: Int): Unit = {
    values foreach { value ⇒
      validateLength(name, value, length)
    }
  }

}
"""
    val source = s"${header}package ${ssd.namespaces.models} {\n\n  " +
      Seq(
        ssd.unions.map { generateUnionTraits(ssd.models, _) }.mkString("\n\n").indent(2),
        "",
        ssd.models.map { m => generateCaseClass(ssd, m, ssd.unionsForModel(m)) }.mkString("\n\n").indent(2),
        generatedClasses,
        generatePlayEnums(ssd).indent(2)
      ).mkString("\n").trim +
      validationSource.indent(2) + 
      s"\n\n}"


    Seq(ServiceFileNames.toFile(form.service.namespace, form.service.organization.key, form.service.application.key, form.service.version, "Models", source, Some("Scala")))

  }

  private def generateUnionTraits(models: Seq[ScalaModel], union: ScalaUnion): String = {
    // TODO: handle primitive types

    union.description.map { desc => ScalaUtil.textToComment(desc) + "\n" }.getOrElse("") +
      s"sealed trait ${union.name}"
  }

  def generateCaseClass(ssd: ScalaService, model: ScalaModel, unions: Seq[ScalaUnion]): String = {
    model.description.map { desc => ScalaUtil.textToComment(desc) + "\n" }.getOrElse("") +
      s"case class ${model.name}(${getArguments(model, unions)})" + ScalaUtil.extendsClause(extendsClasses(model) ++ unions.map(_.name)).map(s => s" $s").getOrElse("") + generateBody(ssd, model, unions)
  }

  private def generatePlayEnums(ssd: ScalaService): String = {
    ssd.enums.map { ScalaEnums(ssd, _).build }.mkString("\n\n")
  }

  // Add validation
  def generateBody(ssd: ScalaService,  model: ScalaModel, unions: Seq[ScalaUnion]): String = {
    " {\n\n" +
      s"import Validation._".indent(2) + "\n" +
      model.fields.filter(_.field.maximum.isDefined).map{ field: ScalaField =>
        s"""validateLength("${field.name}", ${field.name}, ${field.field.maximum.get})""".indent(2)
      }.mkString("\n") +
      "\n}"
  }

  // Add extends c
  def extendsClasses(model: ScalaModel): Seq[String] = model.model.attributes.filter{attr =>
    attr.name == ScalaExtensionKey
  }.flatMap { attr =>
    (attr.value \ "classes").as[JsArray].value.map(_.as[JsString].value)
  }

  // Override field types
  def getArguments(model: ScalaModel, unions: Seq[ScalaUnion]): String = {
    val fields = Map(model.fields.map ( f => f.field.name -> f): _*)
    model.model.fields.map(field => {
                             field.attributes.find(_.name.equalsIgnoreCase(ScalaTypeKey)) match {
                               case Some(attr) =>
                                 val name = ScalaUtil.quoteNameIfKeyword(snakeToCamelCase(field.name))
                                 val clazz = (attr.value.as[JsObject] \ "class").as[JsString].value
                                 val value = if(field.required)
                                   s"_root_.${clazz}"
                                          else
                                   s"_root_.scala.Option[_root_.${clazz}] = None"
                                 s"$name: ${value}"
                               case None => fields(field.name).definition()
                             }
                           }).mkString("\n", ",\n", "\n").indent(2) + "\n"

  }

}

