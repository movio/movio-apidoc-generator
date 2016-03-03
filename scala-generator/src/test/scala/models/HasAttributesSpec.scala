package scala.models

import org.scalatest.{ ShouldMatchers, FunSpec }
import scala.generator.ScalaService

class HasAttributesSpec extends FunSpec with ShouldMatchers {

  import HasAttributes.ops._
  import HasAttributesI._

  val service = models.TestHelper.service(models.TestHelper.buildJson("""
      "imports": [],
      "headers": [],
      "info": [],
      "models": [],
      "enums": [],
      "unions": [],
      "resources": [],
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

  describe("attrs") {
    it("Model") {
      user.getAttributes(0).name shouldBe "scala_model_props"
      user.findAttribute("scala_model_props").get.name shouldBe "scala_model_props"
    }
    it("Field") {
      user.fields(0).getAttributes(0).name shouldBe "scala_field_props"
    }
  }

}
