package scala.models

import com.bryzek.apidoc.generator.v0.models.{ File, InvocationForm }
import com.bryzek.apidoc.spec.v0.models.Attribute
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ ScalaEnums, ScalaCaseClasses, ScalaService, ScalaResource, ScalaOperation, ScalaUtil }
import generator.ServiceFileNames
import scala.generator.MovioCaseClasses
import play.api.libs.json.JsString

object PlayService extends PlayService
trait PlayService extends CodeGenerator {

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
      case false ⇒ ""
      case true  ⇒ ApidocComments(form.service.version, form.userAgent).toJavaString() + "\n"
    }

    val models = ssd.models.filter(model ⇒
      model.model.attributes.exists(attr ⇒
        attr.name == MovioCaseClasses.KafkaClassKey))

    ssd.resources.map { resource: ScalaResource ⇒
      println(resource.plural) 
      val resourceName = resource.plural
      val serviceName = resource.plural + "Service"

      // Find KafkaProducer that contians the model for $resourceName
      val kafkaProducers = models.flatMap(_.model.attributes.map(attr ⇒ {
        (attr.value \ "data_type").as[JsString].value
      }))
      val resourceBodies = resource.operations.flatMap(_.body.map(_.body.`type`))

      val producerMap = kafkaProducers.intersect(resourceBodies).map(t => t -> ScalaUtil.toClassName(t)).toMap

      val producers = producerMap.values.map(p ⇒ s"val kafka${p}Producer = new Kafka${p}Producer(config)").mkString("\n")

      val resourceFunctions = resource.operations.map { operation: ScalaOperation ⇒
        val method = operation.method.toString.toLowerCase
        val parameters = operation.parameters

        val bodyType = operation.body.map(_.name).getOrElse("Unit")

        val firstParamName = parameters.map(_.name).headOption.getOrElse("")

        val dataArg = bodyType match {
          case "Unit" => None
          case _ => Some(s"""data: ${bodyType}""")
        }
        val additionalArgs = Seq(Some("request: Request[T]"), dataArg).flatten
        val argList = ScalaUtil.fieldsToArgList(additionalArgs ++ (parameters.map(_.definition()))).mkString(", ")

        val argNameList = (Seq("request.body", "request") ++ operation.parameters.map(_.name)).mkString(", ")


        val producerName = operation.body.map(_.body.`type`)
          .map(_.replaceAll("[\\[\\]]", ""))
          .map(clazz => s"kafka${ScalaUtil.toClassName(clazz)}Producer")
          .getOrElse("???")

        // Use in service
        val resultType = operation.resultType

        val bodyScala = method match {
          case "post" | "put" => s"""${producerName}.send(data, ${firstParamName})"""
          case "get" => 
            // Create a default Case Class
            val model = ssd.models.filter(_.qualifiedName == operation.resultType).head
            val caseClass = KafkaTests.createEntity(model, 1, models)
            s"Try { ${caseClass.indent(6)} }"
          case _ => "???"
        }


        s"""
def ${method}[T](${argList}): Future[Try[${resultType}]] = {
  Future {
    ${bodyScala}
  }
}"""
      }.mkString("\n")

      val source = s"""$header
package services

import javax.inject.Inject

import com.typesafe.config.Config

import play.api.mvc.Request
import scala.concurrent.Future
import scala.util.Try

class ${serviceName} @Inject() (config: Config) {
  import ${ssd.namespaces.models}._
  import ${ssd.namespaces.base}.kafka._
  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  ${producers}

  ${resourceFunctions.indent(2)}
}
"""
      ServiceFileNames.toFile("app.services", form.service.organization.key, form.service.application.key, form.service.version, s"${serviceName}", source, Some("Scala"))
    }
  }
}
