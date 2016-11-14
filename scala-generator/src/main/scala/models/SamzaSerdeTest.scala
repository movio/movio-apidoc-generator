package scala.models

import com.bryzek.apidoc.generator.v0.models.{File, InvocationForm}
import lib.Text._
import lib.generator.CodeGenerator
import scala.generator.ScalaCaseClasses
import scala.generator.ScalaDatatype
import scala.generator.ScalaField
import scala.generator.ScalaPrimitive
import scala.generator.ScalaService
import generator.ServiceFileNames

object SamzaSerdeTest extends SamzaSerdeTest
trait SamzaSerdeTest extends CodeGenerator {

  override def invoke(form: InvocationForm): Either[Seq[String], Seq[File]] = {
    ScalaCaseClasses.modelsWithTooManyFieldsErrors(form.service) match {
      case Nil    => Right(generateCode(form = form, addBindables = true, addHeader = true))
      case errors => Left(errors)
    }
  }

  def generateCode(form: InvocationForm, addBindables: Boolean, addHeader: Boolean): Seq[File] = {
    val ssd = ScalaService(form.service)

    val header = if (addHeader) {
      val formattingDirective = "// format: OFF"
      val commentHeader = ApidocComments(form.service.version, form.userAgent).toJavaString
      s"$formattingDirective\n\n$commentHeader\n"
    } else {
      ""
    }

    def generateTest(simpleName: String, fullName: String): String = {
      s"""class ${simpleName}SerdeTest extends WordSpec with Matchers with PropertyChecks {
         |  import arbitrary._
         |
         |  "${simpleName} serde" should {
         |    "round trip" in {
         |      val _serde = new serde.${simpleName}Serde()
         |      forAll { obj: ${fullName} =>
         |        _serde.fromBytes(_serde.toBytes(obj)) shouldBe obj
         |      }
         |    }
         |  }
         |}
      """.stripMargin
    }

    val models = ssd.models map { model ⇒
      generateTest(model.name, model.qualifiedName)
    }

    val source = s"""$header
package ${ssd.namespaces.models} {
  import org.scalatest.Matchers
  import org.scalatest.WordSpec
  import org.scalatest.prop.PropertyChecks

${models.mkString("\n\n").indent(2)}
}
"""
    Seq(
      ServiceFileNames.toFile(
        form.service.namespace,
        form.service.organization.key,
        form.service.application.key,
        form.service.version,
        "SerdeTest",
        source,
        Some("Scala")
      )
    )
  }
}
