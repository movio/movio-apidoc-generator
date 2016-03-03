package scala.generator

import com.bryzek.apidoc.generator.v0.models.InvocationForm
import scala.models.Play24ClientGenerator

import models.TestHelper
import org.scalatest.{FunSpec, Matchers}

class ReferenceWithImportsSpec extends FunSpec with Matchers {

  lazy val ssd = new ScalaService(models.TestHelper.referenceWithImportsApiService)

  it("user case classes") {
    val model = ssd.models.find(_.name == "User").get
    val code = ScalaCaseClasses.generateCaseClass(model, Seq.empty)
    models.TestHelper.assertEqualsFile("/generators/reference-spec-user-case-class.txt", code)
  }

  it("member case classes") {
    val model = ssd.models.find(_.name == "Member").get
    val code = ScalaCaseClasses.generateCaseClass(model, Seq.empty)
    models.TestHelper.assertEqualsFile("/generators/reference-spec-member-case-class.txt", code)
  }

  it("generates expected code for play 2.4 client") {
    Play24ClientGenerator.invoke(InvocationForm(service = models.TestHelper.referenceWithImportsApiService)) match {
      case Left(errors) => fail(errors.mkString(", "))
      case Right(sourceFiles) => {
        sourceFiles.size shouldBe 1
        models.TestHelper.assertEqualsFile("/generators/reference-with-imports-spec-play-24.txt", sourceFiles.head.contents)
      }
    }
  }

}


