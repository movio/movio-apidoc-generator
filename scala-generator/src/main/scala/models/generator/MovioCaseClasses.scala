package scala.generator

import scala.models.ApidocComments
import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import com.bryzek.apidoc.spec.v0.models.Attribute
import com.bryzek.apidoc.spec.v0.models.Service
import lib.Text._
import lib.generator.CodeGenerator
import generator.ServiceFileNames
import play.api.libs.json.JsArray
import play.api.libs.json.JsString
import play.api.libs.json.JsObject

// Extended from from ScalaCaseClasses
object MovioCaseClasses extends MovioCaseClasses 
trait MovioCaseClasses extends CodeGenerator {

  private[this] val MaxNumberOfFields = 21
  val ScalaExtensionKey = "scala_extends"
  val ScalaTypeKey = "scala_type"
  val ScalaClassKey = "class"
  val ScalaDefaultKey = "default"
  val ScalaExampleKey = "example"
  val KafkaKey = "kafka_class"

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

    val source = s"${header}package ${packageNamespace(ssd)} {\n\n  " +
      Seq(
        ssd.unions.map { generateUnionTraits(ssd.models, _) }.mkString("\n\n").indent(2),
        "",
        ssd.models.filter(modelFilter).map { m => generateCaseClass(ssd, m, ssd.unionsForModel(m)) }.mkString("\n\n").indent(2),
        generatedClasses,
        generatePlayEnums(ssd).indent(2)
      ).mkString("\n").trim +
      additionalClasses(ssd).indent(2) +
      s"\n\n}"

    Seq(ServiceFileNames.toFile(form.service.namespace, form.service.organization.key, form.service.application.key, form.service.version, filenamePostfix, source, Some("Scala")))
  }

  def packageNamespace(ssd: ScalaService): String = ssd.namespaces.models

  def modelFilter(model: ScalaModel):Boolean = true

  def filenamePostfix = "Models"


  private def generateUnionTraits(models: Seq[ScalaModel], union: ScalaUnion): String = {
    // TODO: handle primitive types

    union.description.map { desc => ScalaUtil.textToComment(desc) + "\n" }.getOrElse("") +
      s"sealed trait ${union.name}"
  }

  def generateCaseClass(ssd: ScalaService, model: ScalaModel, unions: Seq[ScalaUnion]): String = {
    model.description.map { desc => ScalaUtil.textToComment(desc) + "\n" }.getOrElse("") +
      s"case class ${model.name}(${getArguments(model, unions)})" + extendsClause(model, unions) + generateBody(ssd, model, unions)
  }

  def extendsClause(model: ScalaModel, unions: Seq[ScalaUnion]) =
    ScalaUtil.extendsClause(
      manualExtendsClasses ++
      extendsClasses(model) ++
        unions.map(_.name)).map(s => s" $s").getOrElse("")


  private def generatePlayEnums(ssd: ScalaService): String = {
    ssd.enums.map { ScalaEnums(ssd, _).build }.mkString("\n\n")
  }

  def generateBody(ssd: ScalaService, model: ScalaModel, unions: Seq[ScalaUnion]): String =
    " {\n\n" +
      generateValidation(ssd, model, unions) +
      generateKafkaBody(ssd, model, unions).indent(2) +
     "\n}"

  // Add validation
  def generateValidation(ssd: ScalaService,  model: ScalaModel, unions: Seq[ScalaUnion]): String = {
      s"import Validation._".indent(2) + "\n" +
      model.fields.filter(_.field.maximum.isDefined).map{ field: ScalaField =>
        s"""validateLength("${field.name}", ${field.name}, ${field.field.maximum.get})""".indent(2)
      }.mkString("\n")
  }

  def generateKafkaBody(ssd: ScalaService,  model: ScalaModel, unions: Seq[ScalaUnion]): String = {
    containsKafka(model) match {
      case true =>
        val (kafkaDataKey:String, topic: String) =
          (model.model.attributes.find(attr => attr.name == KafkaKey) map {attr: Attribute =>
             (
               (attr.value \ "data_key").as[JsString].value,
               (attr.value \ "topic").as[JsString].value
             )
           }).get
        val version = ssd.namespaces.last
        s"""
def key = ${kafkaDataKey}
"""
      case false => ""
    }
  }

  // Add extends c
  def extendsClasses(model: ScalaModel): Seq[String] = model.model.attributes.filter{attr =>
    attr.name == ScalaExtensionKey
  }.flatMap { attr =>
    (attr.value \ "classes").as[JsArray].value.map(_.as[JsString].value)
  }

  def manualExtendsClasses = Seq.empty[String]

  // Override field types
  def getArguments(model: ScalaModel, unions: Seq[ScalaUnion]): String = {
    model.fields.map {field =>
      val name = ScalaUtil.quoteNameIfKeyword(snakeToCamelCase(field.name))
      val definition = dataType(field)
      val rootedType = field.datatype.name
      s"$name: ${definition}${getDefault(field)}"
    }.mkString("\n", ",\n", "\n").indent(2) + "\n"
  }

  def getDefault(field: ScalaField): String = {
    field.field.attributes.find(a => a.name == ScalaTypeKey ) match {
      case Some(attr) =>
        // Custom Type
        (attr.value.as[JsObject] \ ScalaDefaultKey).toOption match {
          case Some(v) => " = " + v.as[JsString].value
          case None => 
            if(field.required)
              ""
            else
              " = None"
        }
      case None => 
        // Standard Field
        if(field.required)
          ""
        else
          " = None"
    }
  }

  def dataType(field: ScalaField) = {
    field.field.attributes.find(a => a.name == ScalaTypeKey ) match {
      case Some(attr) =>
        val rootedType = "_root_." + (attr.value.as[JsObject] \ ScalaClassKey).as[JsString].value
        if(field.required)
          rootedType
        else
          s"_root_.scala.Option[${rootedType}]"
      case None => field.datatype.name
    }
  }

  def additionalClasses(ssd: ScalaService) = validation + kafkaMessageTrait(ssd)

  def validation = s"""

object Validation {

  def validateLength(name: String, value: _root_.scala.Option[String], length: Int): Unit = {
    value foreach { value ⇒
      validateLength(name, value, length)
    }
  }

  def validateLength(name: String, value: String, length: Int): Unit = {
    require(value.length <= length, s"$$name must be $$length characters or less")
  }

  def validateLengthOfAll(name: String, values: _root_.scala.Option[Seq[String]], length: Int): Unit = {
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

  def containsKafka(ssd: ScalaService):Boolean = 
    ssd.models.exists(containsKafka(_))

  def containsKafka(model: ScalaModel):Boolean = 
    model.model.attributes.exists{attr: Attribute =>
      attr.name == KafkaKey
    }

  def kafkaMessageTrait(ssd: ScalaService): String = {
    containsKafka(ssd) match {
      case true => """

trait KafkaMessage {

  /**
    A scala statement/code that returns the kafka key to use
    Usually something like `data.exhibitorId`
    */
  def key: String
}
"""
      case false => ""
    }
  }

}

