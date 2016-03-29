package scala.models

import com.bryzek.apidoc.generator.v0.models.InvocationForm
import org.scalatest.{ShouldMatchers, FunSpec}

class ExampleUnionTypesSpec extends FunSpec with ShouldMatchers {

  private lazy val service = models.TestHelper.parseFile(s"/examples/union-types-service.json")

  it("generates expected code for play 2.3 client") {
    Play23ClientGenerator.invoke(InvocationForm(service = service)) match {
      case Left(errors) => fail(errors.mkString(", "))
      case Right(sourceFiles) => {
        sourceFiles.size shouldBe 1
        models.TestHelper.assertEqualsFile("/example-union-types-play-23.txt", sourceFiles.head.contents)
      }
    }
  }

}
