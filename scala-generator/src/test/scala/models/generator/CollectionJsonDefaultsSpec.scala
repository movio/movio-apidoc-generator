package scala.generator

import com.bryzek.apidoc.generator.v0.models.InvocationForm
import scala.models.Play24ClientGenerator

import models.TestHelper
import org.scalatest.{FunSpec, Matchers}

class CollectionJsonDefaultsSpec extends FunSpec with Matchers {

  lazy val ssd = new ScalaService(models.TestHelper.collectionJsonDefaultsService)

  it("user case classes") {
    val model = ssd.models.find(_.name == "User").get
    val code = ScalaCaseClasses.generateCaseClass(model, Seq.empty)
    models.TestHelper.assertEqualsFile("/generators/collection-json-defaults-user-case-class.txt", code)
  }

  it("user_patch case classes") {
    val model = ssd.models.find(_.name == "UserPatch").get
    val code = ScalaCaseClasses.generateCaseClass(model, Seq.empty)
    models.TestHelper.assertEqualsFile("/generators/collection-json-defaults-user-patch-case-class.txt", code)
  }

  it("generates expected code for play 2.4 client") {
    Play24ClientGenerator.invoke(InvocationForm(service = models.TestHelper.collectionJsonDefaultsService)) match {
      case Left(errors) => fail(errors.mkString(", "))
      case Right(sourceFiles) => {
        sourceFiles.size shouldBe 1
        models.TestHelper.assertEqualsFile("/generators/collection-json-defaults-play-24.txt", sourceFiles.head.contents)
      }
    }
  }

}


