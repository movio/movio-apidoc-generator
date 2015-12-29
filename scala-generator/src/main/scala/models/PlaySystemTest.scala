package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import com.bryzek.apidoc.spec.v0.models.Attribute
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ScalaEnums, ScalaCaseClasses, ScalaService, ScalaResource, ScalaOperation, ScalaUtil}
import generator.ServiceFileNames
import scala.generator.MovioCaseClasses
import play.api.libs.json.JsString

object PlaySystemTest extends PlaySystemTest 
trait PlaySystemTest extends CodeGenerator {

  override def invoke(
    form: InvocationForm
  ): Either[Seq[String], Seq[File]] = {
    Right(generateCode(form))
  }

  def generateCode(
    form: InvocationForm,
    addHeader: Boolean = true
  ): Seq[File] = {
    val ssd = ScalaService(form.service)

    val prefix = underscoreAndDashToInitCap(ssd.name)
    val enumJson: String = ssd.enums.map { ScalaEnums(ssd, _).buildJson() }.mkString("\n\n")
    val play2Json = Play2Json(ssd).generate()

    val header = addHeader match {
      case false => ""
      case true => ApidocComments(form.service.version, form.userAgent).toJavaString() + "\n"
    }

    val models = ssd.models.filter(model =>
      model.model.attributes.exists(attr =>
        attr.name == MovioCaseClasses.KafkaClassKey
      )
    )

    ssd.resources.map{ resource: ScalaResource =>
      val resourceName = resource.plural
      val serviceName = resource.plural + "Service"

      val resourceFunctions = resource.operations.map { operation: ScalaOperation =>
        val method = operation.method.toString.toLowerCase
        val parameters = operation.parameters
        val argList = ScalaUtil.fieldsToArgList(parameters.map(_.definition())).getOrElse("")
        val firstParamName = parameters.map(_.name).headOption.getOrElse("")

        val argNameList = (Seq("request.body", "request") ++ operation.parameters.map(_.name)).mkString(", ")

        val bodyType = operation.body.map(_.name).getOrElse("Unit")

        // Use in service
        val resultType = operation.resultType

        s"""
def ${method}[T](data: ${bodyType}, request: Request[T], ${argList}): Future[Try[${bodyType}]] = {
  Future {
    producer.send(data, ${firstParamName})
  }
}"""
        }.mkString("\n")


       val source = s"""$header
"""

//       val source = s"""$header
// package services

// import javax.inject.Inject

// import com.typesafe.config.Config

// import play.api.mvc.Request
// import scala.concurrent.Future
// import scala.util.Try

// class ${serviceName} @Inject() (config: Config) {
//   import ${ssd.namespaces.models}._
//   import ${ssd.namespaces.models}.kafka._  //FXIME - KafkaMovieCoreProducer
//   import play.api.libs.concurrent.Execution.Implicits.defaultContext

//   //FIXME
//   val producer = new KafkaMovieCoreProducer(config)

//   ${resourceFunctions.indent(2)}
// }
// """
      ServiceFileNames.toFile("app.services", form.service.organization.key, form.service.application.key, form.service.version, s"${serviceName}", source, Some("Scala"))
    }
  }
}
