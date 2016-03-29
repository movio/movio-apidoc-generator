package scala.models

import com.bryzek.apidoc.generator.v0.models.{ File, InvocationForm }
import com.bryzek.apidoc.spec.v0.models.Attribute
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.{ ScalaEnums, ScalaCaseClasses, ScalaService, ScalaResource, ScalaOperation, ScalaUtil }
import scala.generator.ScalaDatatype.Container
import generator.ServiceFileNames
import play.api.libs.json.JsString

object PlayApplicationModule extends PlayApplicationModule
trait PlayApplicationModule extends CodeGenerator {
  import KafkaUtil._
  import CaseClassUtil._

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
    val play2Json = Play2JsonExtended(ssd).generate()

    val header = addHeader match {
      case false ⇒ ""
      case true  ⇒ ApidocComments(form.service.version, form.userAgent).toJavaString() + "\n"
    }

    val services = ssd.resources.map(resource => s"bind(classOf[${resource.plural}Service])").mkString("\n").indent(4)

    val source = s"""
package modules

import com.google.inject.AbstractModule
import com.typesafe.config.Config
import play.api.{ Configuration, Environment }

class ApplicationModule(
    environment: Environment,
    configuration: Configuration
) extends AbstractModule {

  def configure() = {
    import services._
    bind(classOf[Config]).toInstance(configuration.underlying)

${services}
  }
}"""

      Seq(File("ApplicationModule.scala", Some("modules"), source))
  }
}
