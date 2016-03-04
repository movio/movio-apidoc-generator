package scala.models

import org.scalatest.{ ShouldMatchers, FunSpec }
import scala.generator.ScalaService

class HasClassNameSpec extends FunSpec with ShouldMatchers {

  import HasClassName.ops._
  import HasClassNames._

  val service = models.TestHelper.service(models.TestHelper.buildJson("""
      "imports": [],
      "headers": [],
      "info": [],
      "models": [],
      "enums": [],
      "unions": [],
      "resources": [],
      "attributes": [],
      "models": [
        {
          "name": "user",
          "plural": "users",
          "fields": [
            { "name": "first", "type": "string", "required": true, "attributes": [
              { "name": "scala_field_props", "value": { "class": "org.joda.time.LocalDateTime" } }
            ]}
          ],
          "attributes": [
            { "name": "scala_model_props", "value": { "extends": ["com.github.BaseClass"] } }
          ]
        }
      ]
    """))
  val user = service.models(0)

  describe("class names") {
    it("Model") {
      user.getName(service) shouldBe "user"
      user.className(service) shouldBe "User"
      user.qualifiedName(service) shouldBe "test.apidoc.apidoctest.v0.models.User"
    }
  }

}
